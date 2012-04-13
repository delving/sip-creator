/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.xml;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.files.StorageException;
import org.apache.commons.io.IOUtils;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * Analyze xml input and compile statistics. When analysis fails, the .error will be appended to the filename
 * of the erroneous file.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class AnalysisParser implements Runnable {
    public static final int ELEMENT_STEP = 10000;
    private Path path = Path.empty();
    private Map<Path, FieldStatistics> statisticsMap = new HashMap<Path, FieldStatistics>();
    private Map<String, String> namespaces = new TreeMap<String, String>();
    private Listener listener;
    private DataSet dataSet;

    public interface Listener {

        void success(Statistics statistics);

        void failure(String message, Exception exception);

        boolean progress(long elementCount);
    }

    public AnalysisParser(DataSet dataSet, Listener listener) {
        this.dataSet = dataSet;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            XMLInputFactory2 xmlif = (XMLInputFactory2) XMLInputFactory2.newInstance();
            xmlif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
            xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            xmlif.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
            xmlif.configureForSpeed();
            boolean running = true;
            boolean sourceFormat = false;
            InputStream inputStream = null;
            try {
                switch (dataSet.getState()) {
                    case IMPORTED:
                        inputStream = dataSet.openImportedInputStream();
                        break;
                    case SOURCED:
                        inputStream = dataSet.openSourceInputStream();
                        sourceFormat = true;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected state: " + dataSet.getState());
                }
                XMLStreamReader2 input = (XMLStreamReader2) xmlif.createXMLStreamReader(getClass().getName(), inputStream);
                StringBuilder text = new StringBuilder();
                long count = 0;
//                long time = System.currentTimeMillis();
                while (running) {
                    switch (input.getEventType()) {
                        case XMLEvent.START_ELEMENT:
                            if (++count % ELEMENT_STEP == 0) {
                                if (listener != null && !listener.progress(count)) running = false;
//                                Runtime r = Runtime.getRuntime();
//                                long since = System.currentTimeMillis() - time;
//                                time = System.currentTimeMillis();
//                                int approximateSize = 0;
//                                for (FieldStatistics stat : statisticsMap.values()) approximateSize += stat.getApproximateSize();
//                                System.out.println(String.format("Mem: Since: %5d, Size: %5d, Free: %10d", since, approximateSize, r.freeMemory()));
                            }
                            for (int walk = 0; walk < input.getNamespaceCount(); walk++) {
                                namespaces.put(input.getNamespacePrefix(walk), input.getNamespaceURI(walk));
                            }
                            path.push(Tag.element(input.getName()));
                            if (input.getAttributeCount() > 0) {
                                for (int walk = 0; walk < input.getAttributeCount(); walk++) {
                                    QName attributeName = input.getAttributeName(walk);
                                    path.push(Tag.attribute(attributeName));
                                    recordValue(input.getAttributeValue(walk));
                                    path.pop();
                                }
                            }
                            break;
                        case XMLEvent.CHARACTERS:
                        case XMLEvent.CDATA:
                            text.append(input.getText());
                            break;
                        case XMLEvent.END_ELEMENT:
                            recordValue(text.toString().trim());
                            text.setLength(0);
                            path.pop();
                            break;
                    }
                    if (!input.hasNext()) break;
                    input.next();
                }
            }
            finally {
                IOUtils.closeQuietly(inputStream);
            }
            if (running) {
                List<FieldStatistics> fieldStatisticsList = new ArrayList<FieldStatistics>(statisticsMap.values());
                listener.success(new Statistics(namespaces, fieldStatisticsList, sourceFormat));
            }
            else {
                listener.failure(null, null);
            }
        }
        catch (Exception e) {
            try {
                File renamedTo;
                switch (dataSet.getState()) {
                    case IMPORTED:
                    case DELIMITED:
                        renamedTo = dataSet.renameInvalidImport();
                        break;
                    case SOURCED:
                        renamedTo = dataSet.renameInvalidSource();
                        break;
                    default:
                        throw new IllegalStateException("Unexpected state " + dataSet.getState(), e);
                }
                listener.failure(String.format("The imported file contains errors, the file has been renamed to '%s'", renamedTo.getName()), e);
            }
            catch (StorageException se) {
                listener.failure("Error renaming file", e);
            }
        }
    }

    private void recordValue(String value) {
        value = value.trim();
        FieldStatistics fieldStatistics = statisticsMap.get(path);
        if (fieldStatistics == null) {
            Path key = path.copy();
            statisticsMap.put(key, fieldStatistics = new FieldStatistics(key));
        }
        if (!value.isEmpty()) fieldStatistics.recordValue(value);
        fieldStatistics.recordOccurrence();
    }
}