package eu.delving.sip.xml;

import com.ctc.wstx.sr.InputElementStack;
import com.ctc.wstx.sr.ValidatingStreamReader;
import eu.delving.metadata.Path;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import org.apache.commons.lang.StringEscapeUtils;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Produces a stream of filtered records based on a list of unique keys
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class RecordFilterParser extends AbstractRecordParser {

    private OutputStream stream = null;
    private final String[] changedRecords;
    private final Path rootPath;
    private final Path uniqueElementPath;

    public RecordFilterParser(OutputStream stream, String[] changedRecords, FileStore.DataSetStore dataSetStore, Listener listener) {
        super(dataSetStore, listener);
        try {
            this.stream = new GZIPOutputStream(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.changedRecords = changedRecords;
        this.rootPath = new Path(dataSetStore.getFacts().getRecordRootPath());
        this.uniqueElementPath = new Path(dataSetStore.getFacts().getUniqueElementPath());
    }

    @Override
    protected void parse(XMLStreamReader2 input) throws IOException, XMLStreamException, FileStoreException {
        PrintStream printStream = new PrintStream(stream, true, "UTF-8");

        printStream.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");

        boolean isParentOfRootElement = path.size() < rootPath.size() && rootPath.toString().startsWith(path.toString());

        StringBuilder elementText = null;
        String unique = null;
        StringBuffer xmlBuffer = null;
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
                    if (isParentOfRootElement) {
                        printStream.append("<");
                        printStream.append(input.getName().toString());

                        // namespaces
                        // if someone wants to spend more time on this, please feel free to do so

//                        we only get the qualified ones at the moment. below the start of an attempt to get the noNamespaceSchemaLocation elements
//                        StringVector nameList = ((ValidatingStreamReader) input).getAttributeCollector().getNameList();
//                        for(int i = 0; i < nameList.size(); i++) {
//                        }

                        if(input.getAttributeCount() > 0) {
                            InputElementStack inputElementStack = ((ValidatingStreamReader) input).getInputElementStack();
                            int totalNsCount = inputElementStack.getTotalNsCount();
                            for (int i = 0; i < totalNsCount; i++) {
                                String attributePrefix = inputElementStack.getAttributePrefix(i);
                                String attributeNamespace = inputElementStack.getAttributeNamespace(i);
                                printStream.append(" ");
                                printStream.append("xmlns:").append(attributePrefix).append("=\"").append(attributeNamespace).append("\"");
                            }
                        }
                        printStream.append(">");
                    }
                    if (xmlBuffer != null) {
                        xmlBuffer.append("<").append(input.getName().toString()).append(">");
                    }
                    break;
                case CHARACTERS:
                case CDATA:
                    if (elementText != null) {
                        elementText.append(input.getText());
                    }
                    if (xmlBuffer != null) {
                        xmlBuffer.append(StringEscapeUtils.escapeXml(input.getText()));
                    }
                    break;
                case END_ELEMENT:
                    if (xmlBuffer != null) {
                        xmlBuffer.append("</").append(input.getName().toString()).append(">");
                    }
                    if (path.equals(uniqueElementPath)) {
                        unique = elementText.toString();
                        elementText = null;
                    }
                    if(isParentOfRootElement) {
                        // close it
                        printStream.append("</").append(input.getName().toString()).append(">");
                    }

                    if (path.equals(rootPath)) {
                        // do we send this one or not
                        if (Arrays.binarySearch(changedRecords, unique) < 0) {
                            printStream.append(xmlBuffer.toString());
                            printStream.flush();
                        }
                        unique = null;
                        xmlBuffer = null;
                    }
                    path.pop();
                    break;
                case END_DOCUMENT:

                    printStream.flush();
                    printStream.close();
                    break;
            }
            if (!input.hasNext()) {
                break;
            }
            input.next();
        }
    }

    @Override
    protected void handleException(Exception e) {
        LOG.error("Terrible things happen", e); // TODO better message
    }
}
