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
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            InputStream inputStream;
            boolean sourceFormat = false;
            switch (dataSet.getState()) {
                case IMPORTED:
                    inputStream = dataSet.importedInput();
                    break;
                case SOURCED:
                    inputStream = dataSet.sourceInput();
                    sourceFormat = true;
                    break;
                default:
                    throw new IllegalStateException("Unexpected state: "+dataSet.getState());
            }
            XMLStreamReader2 input = (XMLStreamReader2) xmlif.createXMLStreamReader(getClass().getName(), inputStream);
            StringBuilder text = new StringBuilder();
            long count = 0;
            boolean running = true;
            while (running) {
                switch (input.getEventType()) {
                    case XMLEvent.START_ELEMENT:
                        if (++count % ELEMENT_STEP == 0) {
                            if (null != listener) {
                                if (!listener.progress(count)) {
                                    running = false;
                                }
                            }
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
                        text.append(input.getText());
                        break;
                    case XMLEvent.CDATA:
                        text.append(input.getText());
                        break;
                    case XMLEvent.END_ELEMENT:
                        recordValue(ValueFilter.filter(text.toString()));
                        text.setLength(0);
                        path.pop();
                        break;
                }
                if (!input.hasNext()) {
                    break;
                }
                input.next();
            }
            input.close();
            if (running) {
                List<FieldStatistics> fieldStatisticsList = new ArrayList<FieldStatistics>(statisticsMap.values());
                listener.success(new Statistics(fieldStatisticsList, sourceFormat));
            }
            else {
                listener.failure(null, null);
            }
        }
        catch (Exception e) {
            try {
                File renamedTo = null;
                switch (dataSet.getState()) {
                    case IMPORTED:
                    case DELIMITED:
                        renamedTo = dataSet.renameInvalidImport();
                        break;
                    case SOURCED:
                        renamedTo = dataSet.renameInvalidSource();
                        break;
                    default:
                        throw new IllegalStateException("Unexpected state "+dataSet.getState());
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
        if (!value.isEmpty()) {
            fieldStatistics.recordValue(value);
        }
        fieldStatistics.recordOccurrence();
    }
}