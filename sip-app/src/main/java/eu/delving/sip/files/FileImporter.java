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
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import java.io.*;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static eu.delving.metadata.StringUtil.*;
import static eu.delving.sip.files.StorageHelper.BLOCK_SIZE;
import static eu.delving.sip.files.StorageHelper.delete;

/**
 * Handle the importing of files into a dataset
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FileImporter implements Work.DataSetWork, Work.LongTermWork  {
    private static final String XML_HEADER = "<?xml";
    private File inputFile;
    private ProgressListener progressListener;
    private Runnable finished;
    private DataSet dataSet;

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
        progressListener.setProgressMessage(String.format("Storing data for %s", dataSet.getSpec()));
    }

    @Override
    public void run() {
        int fileBlocks = (int) (inputFile.length() / BLOCK_SIZE);
        if (progressListener != null) progressListener.prepareFor(fileBlocks);
        Hasher hasher = new Hasher();
        boolean cancelled = false;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            outputStream = new GZIPOutputStream(new FileOutputStream(dataSet.importedOutput()));
            CountingInputStream countingInput;
            String name = inputFile.getName();
            if (name.endsWith(".csv")) {
                inputStream = new FileInputStream(inputFile);
                countingInput = new CountingInputStream(inputStream);
                BufferedReader reader = new BufferedReader(new InputStreamReader(countingInput, "UTF-8"));
                Writer writer = new OutputStreamWriter(outputStream, "UTF-8");
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<csv-entries>\n");
                String line;
                List<String> titles = null;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    if (lineNumber == 0) {
                        titles = csvLineParse(line);
                        for (int walk = 0; walk < titles.size(); walk++) {
                            titles.set(walk, csvTitleToTag(titles.get(walk), walk));
                        }
                    }
                    else {
                        List<String> values = csvLineParse(line);
                        if (values.size() != titles.size()) {
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
                }
                writer.write("</csv-entries>\n");
                writer.close();
            }
            else if (name.endsWith(".xml.zip")) {
                inputStream = new FileInputStream(inputFile);
                countingInput = new CountingInputStream(inputStream);
                ZipEntryXmlReader reader = new ZipEntryXmlReader(countingInput);
                Writer writer = new OutputStreamWriter(outputStream, "UTF-8");
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<zip-entries>\n");
                while (true) {
                    String line = reader.readLine();
                    if (line == null) break;
                    if (line.startsWith("<?xml")) continue;
                    writer.write(line);
                    writer.write("\n");
                    if (progressListener != null) {
                        if (!progressListener.setProgress((int) (countingInput.getByteCount() / BLOCK_SIZE))) {
                            cancelled = true;
                            break;
                        }
                    }
                    hasher.update(line);
                }
                writer.write("</zip-entries>\n");
                writer.close();
            }
            else {
                if (name.endsWith(".xml")) {
                    inputStream = new FileInputStream(inputFile);
                    countingInput = new CountingInputStream(inputStream);
                    inputStream = countingInput;
                }
                else if (name.endsWith(".xml.gz")) {
                    inputStream = new FileInputStream(inputFile);
                    countingInput = new CountingInputStream(inputStream);
                    inputStream = new GZIPInputStream(countingInput);
                }
                else {
                    throw new IllegalArgumentException("Input file should be .xml, .xml.gz or .xml.zip, but it is " + name);
                }
                boolean headerFound = false;
                byte[] buffer = new byte[BLOCK_SIZE];
                int bytesRead;
                while (-1 != (bytesRead = inputStream.read(buffer))) {
                    if (!headerFound) {
                        String chunk = new String(buffer, 0, XML_HEADER.length(), "UTF-8");
                        if (!XML_HEADER.equals(chunk)) throw new IOException(String.format("Not an XML File. Must begin with '%s...'.", XML_HEADER));
                        headerFound = true;
                    }
                    outputStream.write(buffer, 0, bytesRead);
                    if (progressListener != null) {
                        if (!progressListener.setProgress((int) (countingInput.getByteCount() / BLOCK_SIZE))) {
                            cancelled = true;
                            break;
                        }
                    }
                    hasher.update(buffer, bytesRead);
                }
            }
            if (finished != null) finished.run();
        }
        catch (IOException e) {
            progressListener.getFeedback().alert("Unable to import", e);
        }
        finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
        }
        if (cancelled) {
            delete(dataSet.importedOutput());
        }
        else {
            File hashedSource = new File(dataSet.importedOutput().getParentFile(), hasher.prefixFileName(Storage.FileType.IMPORTED.getName()));
            if (hashedSource.exists()) delete(dataSet.importedOutput());
        }

    }

}
