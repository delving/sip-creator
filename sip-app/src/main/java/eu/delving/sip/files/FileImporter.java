/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.files;

import eu.delving.metadata.Hasher;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import java.io.*;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static eu.delving.metadata.StringUtil.*;
import static eu.delving.sip.files.StorageHelper.*;
import static org.apache.commons.io.FileUtils.moveFile;

/**
 * Handle the importing of files into a dataset
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FileImporter implements Work.DataSetWork, Work.LongTermWork {
    private static final String XML_HEADER = "<?xml";
    private File inputFile;
    private ProgressListener progressListener;
    private CountingInputStream countingInputStream;
    private Runnable finished;
    private DataSet dataSet;
    private Hasher hasher = new Hasher();

    public FileImporter(File inputFile, DataSet dataSet, Runnable finished) {
        this.inputFile = inputFile;
        this.dataSet = dataSet;
        this.finished = finished;
    }

    @Override
    public DataSet getDataSet() {
        return dataSet;
    }

    @Override
    public Job getJob() {
        return Job.IMPORT_SOURCE;
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        progressListener.setProgressMessage("Storing data");
    }

    @Override
    public void run() {
        int fileBlocks = (int) (inputFile.length() / BLOCK_SIZE);
        progressListener.prepareFor(fileBlocks);
        try {
            OutputStream outputStream = new GZIPOutputStream(new FileOutputStream(dataSet.importedOutput()));
            InputStream inputStream = new FileInputStream(inputFile);
            inputStream = countingInputStream = new CountingInputStream(inputStream);
            try {
                String name = inputFile.getName();
                if (name.endsWith(".csv")) {
                    consumeCSVFile(inputStream, outputStream);
                }
                else if (name.endsWith(".xml.zip")) {
                    consumeXMLZipFile(inputStream, outputStream);
                }
                else if (name.endsWith(".xml") || name.endsWith(".xml.gz")) {
                    if (name.endsWith(".xml.gz")) inputStream = new GZIPInputStream(inputStream);
                    consumeXMLFile(inputStream, outputStream);
                }
                else {
                    throw new IllegalArgumentException("Unrecognized file extension: " + name);
                }
            }
            finally {
                IOUtils.closeQuietly(outputStream);
                IOUtils.closeQuietly(inputStream);
            }
            File hashedImport = new File(dataSet.importedOutput().getParentFile(), hasher.prefixFileName(Storage.FileType.IMPORTED.getName()));
            if (hashedImport.exists()) delete(hashedImport);
            moveFile(dataSet.importedOutput(), hashedImport);
            delete(statsFile(dataSet.importedOutput().getParentFile(), false, null));
            if (finished != null) finished.run();
        }
        catch (CancelException e) {
            delete(dataSet.importedOutput());
            progressListener.getFeedback().alert("Cancelled", e);
        }
        catch (IOException e) {
            progressListener.getFeedback().alert("Unable to import: "+e.getMessage(), e);
        }
    }

    private void consumeCSVFile(InputStream inputStream, OutputStream outputStream) throws IOException, CancelException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        Writer writer = new OutputStreamWriter(outputStream, "UTF-8");
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<csv-entries>\n");
        char delimiter = ',';
        String line;
        List<String> titles = null;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            if (lineNumber == 0) {
                delimiter = csvDelimiter(line);
                titles = csvLineParse(line, delimiter);
                for (int walk = 0; walk < titles.size(); walk++) {
                    titles.set(walk, csvTitleToTag(titles.get(walk), walk));
                }
            }
            else {
                List<String> values = csvLineParse(line, delimiter);
                if (values.size() != titles.size()) {
                    if (values.size() == 1 && values.get(0).isEmpty()) continue;
                    throw new IOException(String.format(
                            "Expected %d fields in CSV file on line %d",
                            titles.size(), lineNumber
                    ));
                }
                writer.write(String.format("<csv-entry line=\"%d\">\n", lineNumber));
                for (int walk = 0; walk < titles.size(); walk++) {
                    writer.write(String.format(
                            "   <%s>%s</%s>\n",
                            titles.get(walk), csvEscapeXML(values.get(walk)), titles.get(walk))
                    );
                }
                writer.write("</csv-entry>\n");
            }
            lineNumber++;
            showProgress();
        }
        writer.write("</csv-entries>\n");
        writer.close();
    }

    private void consumeXMLZipFile(InputStream inputStream, OutputStream outputStream) throws IOException, CancelException {
        ZipEntryXmlReader reader = new ZipEntryXmlReader(inputStream);
        Writer writer = new OutputStreamWriter(outputStream, "UTF-8");
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<zip-entries>\n");
        while (true) {
            String line = reader.readLine();
            if (line == null) break;
            if (line.startsWith("<?xml")) continue;
            writer.write(line);
            writer.write("\n");
            showProgress();
            hasher.update(line);
        }
        writer.write("</zip-entries>\n");
        writer.close();
    }

    private void consumeXMLFile(InputStream inputStream, OutputStream outputStream) throws IOException, CancelException {
        boolean headerFound = false;
        byte[] buffer = new byte[BLOCK_SIZE];
        int bytesRead;
        while (-1 != (bytesRead = inputStream.read(buffer))) {
            if (!headerFound) {
                String chunk = new String(buffer, 0, buffer.length, "UTF-8");
                if (chunk.indexOf('<') > 0) chunk = chunk.substring(chunk.indexOf('<'));
                if (!chunk.startsWith(XML_HEADER)) throw new IOException(String.format("Not an XML File. Must begin with '%s...'.", XML_HEADER));
                headerFound = true;
            }
            outputStream.write(buffer, 0, bytesRead);
            showProgress();
            hasher.update(buffer, bytesRead);
        }
    }

    private void showProgress() throws CancelException {
        progressListener.setProgress((int) (countingInputStream.getByteCount() / 1024));
    }
}
