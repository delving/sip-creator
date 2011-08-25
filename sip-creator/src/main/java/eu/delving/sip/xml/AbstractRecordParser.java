package eu.delving.sip.xml;

import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import org.apache.log4j.Logger;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;

/**
 * Abstract parser for records from a DataSet
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public abstract class AbstractRecordParser implements Runnable {
    protected final Logger LOG = Logger.getLogger(getClass());
    protected static final int ELEMENT_STEP = 10000;
    protected Path path = new Path();
    protected Listener listener;
    protected FileStore.DataSetStore dataSetStore;
    protected boolean abort;
    private long count;

    public AbstractRecordParser(FileStore.DataSetStore dataSetStore, Listener listener) {
        this.dataSetStore = dataSetStore;
        this.listener = listener;
    }

    public void abort() {
        abort = true;
    }

    @Override
    public void run() {
        try {
            XMLStreamReader2 input = initializeParser();
            count = 0;
            parse(input);
        } catch (Exception e) {
            handleException(e);
            listener.failure(e);
        }
    }

    protected abstract void parse(XMLStreamReader2 input) throws IOException, XMLStreamException, FileStoreException;

    protected XMLStreamReader2 initializeParser() throws XMLStreamException, FileStoreException {
        XMLInputFactory2 xmlif = (XMLInputFactory2) XMLInputFactory2.newInstance();
        xmlif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        xmlif.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        xmlif.configureForSpeed();
        return (XMLStreamReader2) xmlif.createXMLStreamReader(getClass().getName(), dataSetStore.getSourceInputStream());
    }

    protected abstract void handleException(Exception e);

    protected void reportProgress() {
        if (++count % ELEMENT_STEP == 0) {
            if (null != listener) {
                listener.progress(count);
            }
        }
    }


    protected void pushTag(XMLStreamReader2 input) {
        path.push(Tag.create(input.getName().getPrefix(), input.getName().getLocalPart()));
    }


    public interface Listener {

        void success(final Object payload);

        void failure(Exception exception);

        void progress(long elementCount);
    }
}
