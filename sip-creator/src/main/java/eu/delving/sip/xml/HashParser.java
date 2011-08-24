package eu.delving.sip.xml;

import eu.delving.metadata.Hasher;
import eu.delving.metadata.Path;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.stream.XMLStreamException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static javax.xml.stream.events.XMLEvent.CDATA;
import static javax.xml.stream.events.XMLEvent.CHARACTERS;
import static javax.xml.stream.events.XMLEvent.END_DOCUMENT;
import static javax.xml.stream.events.XMLEvent.END_ELEMENT;
import static javax.xml.stream.events.XMLEvent.START_ELEMENT;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class HashParser extends AbstractRecordParser {

    private final Path rootPath;
    private final Path uniqueElementPath;
    private final Hasher hasher = new Hasher();

    public HashParser(FileStore.DataSetStore dataSetStore, Listener listener) {
        super(dataSetStore, listener);
        this.rootPath = new Path(dataSetStore.getFacts().getRecordRootPath());
        this.uniqueElementPath = new Path(dataSetStore.getFacts().getUniqueElementPath());
    }


    @Override
    protected void parse(XMLStreamReader2 input) throws XMLStreamException, FileStoreException {
        Map<String, String> hashMap = new HashMap<String, String>();
        StringBuilder elementText = null;
        StringBuffer xmlBuffer = null;
        String unique = null;
        while (!abort) {
            switch (input.getEventType()) {
                case START_ELEMENT:
                    reportProgress();
                    pushTag(input);
                    if (path.equals(rootPath)) {
                        xmlBuffer = new StringBuffer();
                    }
                    if (path.equals(uniqueElementPath)) {
                        elementText = new StringBuilder();
                    }
                    break;
                case CHARACTERS:
                case CDATA:
                    if (elementText != null) {
                        elementText.append(input.getText());
                    }
                    if (xmlBuffer != null) {
                        xmlBuffer.append(input.getText());
                    }
                    break;
                case END_ELEMENT:
                    if (path.equals(uniqueElementPath)) {
                        unique = elementText.toString();
                        elementText = null;
                    }
                    if (path.equals(rootPath)) {
                        hashMap.put(unique, hasher.getHashString(xmlBuffer.toString()));
                        unique = null;
                        xmlBuffer = null;
                    }
                    path.pop();
                    break;
                case END_DOCUMENT:
                    break;
            }
            if (!input.hasNext()) {
                break;
            }
            input.next();
        }
        Properties hashes = new Properties();
        hashes.putAll(hashMap);
        dataSetStore.setRecordHashes(hashes);
        listener.success(hashMap);
    }

    @Override
    protected void handleException(Exception e) {
        LOG.error("Could not create hashes", e);
    }
}
