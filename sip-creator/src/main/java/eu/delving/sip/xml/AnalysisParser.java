/*
 * Copyright 2007 EDL FOUNDATION
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

package eu.delving.sip.xml;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyze xml input and compile statistics.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class AnalysisParser extends AbstractRecordParser {
    private Map<Path, FieldStatistics> statisticsMap = new HashMap<Path, FieldStatistics>();

    public AnalysisParser(FileStore.DataSetStore dataSetStore, Listener listener) {
        super(dataSetStore, listener);
    }

    protected void parse(XMLStreamReader2 input) throws XMLStreamException, FileStoreException {
        StringBuilder text = new StringBuilder();
        while (!abort) {
            switch (input.getEventType()) {
                case XMLEvent.START_DOCUMENT:
                    LOG.info("Starting document");
                    break;
                case XMLEvent.START_ELEMENT:
                    reportProgress();
                    pushTag(input);
//                        if (input.getAttributeCount() > 0) {
//                            for (int walk = 0; walk < input.getAttributeCount(); walk++) {
//                                QName attributeName = input.getAttributeName(walk);
//                                path.push(Tag.create(attributeName.getPrefix(), attributeName.getLocalPart()));
//                                recordValue(input.getAttributeValue(walk));
//                                path.pop();
//                            }
//                        }
                    break;
                case XMLEvent.CHARACTERS:
                    text.append(input.getText());
                    break;
                case XMLEvent.CDATA:
                    text.append(input.getText());
                    break;
                case XMLEvent.END_ELEMENT:
                    recordValue(text.toString());
                    text.setLength(0);
                    path.pop();
                    break;
                case XMLEvent.END_DOCUMENT: {
                    LOG.info("Ending document");
                    break;
                }
            }
            if (!input.hasNext()) {
                break;
            }
            input.next();
        }
        List<FieldStatistics> fieldStatisticsList = new ArrayList<FieldStatistics>(statisticsMap.values());
        Collections.sort(fieldStatisticsList);
        for (FieldStatistics fieldStatistics : fieldStatisticsList) {
            fieldStatistics.finish();
        }
        dataSetStore.setStatistics(fieldStatisticsList);
        listener.success(fieldStatisticsList);
    }


    @Override
    protected void handleException(Exception e) {
        LOG.error("Analysis Failed!", e);
    }

    private void recordValue(String value) {
        value = value.trim();
        FieldStatistics fieldStatistics = statisticsMap.get(path);
        if (fieldStatistics == null) {
            Path key = new Path(path);
            statisticsMap.put(key, fieldStatistics = new FieldStatistics(key));
        }
        if (!value.isEmpty()) {
            fieldStatistics.recordValue(value);
        }
        fieldStatistics.recordOccurrence();
    }
}