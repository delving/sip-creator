/*
 * Copyright 2011, 2012 Delving BV
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

import eu.delving.XMLToolFactory;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.model.DataSetModel;
import eu.delving.stats.Stats;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;

/**
 * Analyze xml input and compile statistics. When analysis fails, the .error will be appended to the filename
 * of the erroneous file.
 */
public class AnalysisParser implements Work.LongTermWork, Work.DataSetWork {
    public static final int ELEMENT_STEP = 10000;

    private final Listener listener;
    private final DataSetModel dataSetModel;
    private final int maxUniqueValueLength;
    private ProgressListener progressListener;

    public interface Listener {

        void success(Stats stats);

        void failure(String message, Exception exception);
    }

    public AnalysisParser(DataSetModel dataSetModel, int maxUniqueValueLength, Listener listener) {
        this.dataSetModel = dataSetModel;
        this.listener = listener;
        this.maxUniqueValueLength = maxUniqueValueLength;
    }

    @Override
    public Job getJob() {
        return Job.PARSE_ANALYZE;
    }

    @Override
    public DataSet getDataSet() {
        return dataSetModel.getDataSet();
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        progressListener.setProgressMessage("Analyzing Data");
    }

    @Override
    public void run() {
        try {
            if (dataSetModel.isEmpty()) return;

            switch (dataSetModel.getDataSetState()) {
                case SOURCED:
                    Stats stats = new Stats();
                    stats.maxUniqueValueLength = maxUniqueValueLength;

                    try (InputStream in = dataSetModel.getDataSet().openSourceInputStream()) {
                        updateStats(stats, in, progressListener);
                        stats.name = dataSetModel.getDataSet().getDataSetFacts().get("name");
                        stats.finish();
                        listener.success(stats);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected state: " + dataSetModel.getDataSetState());
            }
        } catch (CancelException e) {
            listener.failure("Cancellation", e);
        } catch (Exception e) {
            switch (dataSetModel.getDataSetState()) {
                case SOURCED:
                    dataSetModel.getDataSet().deleteSource();
                    break;
                default:
                    throw new IllegalStateException("Unexpected state " + dataSetModel.getDataSetState(), e);
            }
            listener.failure("The imported file contains errors, the file has been deleted", e);
        }
    }

    public static Stats updateStats(Stats stats, InputStream inputStream) throws XMLStreamException, CancelException {
        return updateStats(stats, inputStream, null);
    }

    private static Stats updateStats(Stats stats, InputStream inputStream, ProgressListener listener) throws XMLStreamException, CancelException {
        XMLInputFactory xmlif = XMLToolFactory.xmlInputFactory();
        Path path = Path.create();
        XMLStreamReader2 input = (XMLStreamReader2) xmlif.createXMLStreamReader(AnalysisParser.class.getName(), inputStream);
        StringBuilder text = new StringBuilder();
        int count = 0;
        while (true) {
            switch (input.getEventType()) {
                case XMLEvent.START_ELEMENT:
                    if (++count % ELEMENT_STEP == 0) {
                        if (listener != null) listener.setProgress(count);
                    }
                    for (int walk = 0; walk < input.getNamespaceCount(); walk++) {
                        stats.recordNamespace(input.getNamespacePrefix(walk), input.getNamespaceURI(walk));
                    }
                    String chunk = text.toString().trim();
                    if (!chunk.isEmpty()) {
                        stats.recordValue(path, chunk);
                    }
                    text.setLength(0);
                    path = path.child(Tag.element(input.getName()));
                    if (input.getAttributeCount() > 0) {
                        for (int walk = 0; walk < input.getAttributeCount(); walk++) {
                            QName attributeName = input.getAttributeName(walk);
                            Path withAttr = path.child(Tag.attribute(attributeName));
                            stats.recordValue(withAttr, input.getAttributeValue(walk));
                        }
                    }
                    break;
                case XMLEvent.CHARACTERS:
                case XMLEvent.CDATA:
                    text.append(input.getText());
                    break;
                case XMLEvent.END_ELEMENT:
                    // todo: stats.recordRecordEnd()
                    stats.recordValue(path, text.toString().trim());
                    text.setLength(0);
                    path = path.parent();
                    break;
            }
            if (!input.hasNext()) break;
            input.next();
        }
        return stats;
    }
}
