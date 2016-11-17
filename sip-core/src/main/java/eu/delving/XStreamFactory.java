package eu.delving;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.TreeMarshallingStrategy;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;

public class XStreamFactory {

    public static XStream getStreamFor(Class clazz) {
        XStream xstream = getStream();
        xstream.processAnnotations(clazz);
        return xstream;
    }

    private static XStream getStream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.setMarshallingStrategy(new TreeMarshallingStrategy());
        stream.setMode(XStream.NO_REFERENCES);
        stream.registerConverter(new Tag.Converter());
        stream.registerConverter(new Path.Converter());
        return stream;
    }
}
