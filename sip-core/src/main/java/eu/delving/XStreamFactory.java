package eu.delving;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.TreeMarshallingStrategy;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;

import java.util.Collection;

public class XStreamFactory {

    public static XStream getStreamFor(Class clazz) {
        XStream stream = XStreamFactory.createSecureXStream();

        stream.setMarshallingStrategy(new TreeMarshallingStrategy());
        stream.registerConverter(new Tag.Converter());
        stream.registerConverter(new Path.Converter());

        stream.processAnnotations(clazz);
        return stream;
    }

    public static XStream asSecureXStream(XStream stream) {
        stream.setMode(XStream.NO_REFERENCES);

        // See http://x-stream.github.io/security.html#example
        XStream.setupDefaultSecurity(stream);
        stream.addPermission(NullPermission.NULL);
        stream.addPermission(PrimitiveTypePermission.PRIMITIVES);

        stream.allowTypeHierarchy(Collection.class);
        stream.allowTypeHierarchy(String.class);
        stream.allowTypesByWildcard(new String[] {
            "eu.delving.sip.actions.*",
            "eu.delving.sip.base.*",
            "eu.delving.sip.files.*",
            "eu.delving.sip.frames.*",
            "eu.delving.sip.menus.*",
            "eu.delving.sip.model.*",
            "eu.delving.sip.panels.*",
            "eu.delving.sip.xml.*",
            "eu.delving.metadata.*",
            "eu.delving.stats.*",
            "eu.delving.*",

        });
        return stream;
    }

    public static XStream createSecureXStream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        return XStreamFactory.asSecureXStream(stream);
    }
}
