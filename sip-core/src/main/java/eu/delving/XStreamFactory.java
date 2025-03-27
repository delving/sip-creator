/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.TreeMarshallingStrategy;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.security.AnyTypePermission;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;

import eu.delving.metadata.Assertion.AssertionList;
import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;

public class XStreamFactory {
    public static XStream getStreamFor(Class<?> clazz) {
        XStream stream = new XStream(new PureJavaReflectionProvider());

        // Security configuration
        stream.addPermission(AnyTypePermission.ANY);
        //stream.addPermission(NoTypePermission.NONE);
        stream.addPermission(NullPermission.NULL);
        stream.addPermission(PrimitiveTypePermission.PRIMITIVES);
        stream.ignoreUnknownElements();

        // Register a specific converter for MappingFunction.FunctionList's TreeSet
        stream.registerConverter(new CollectionConverter(stream.getMapper()) {
            @Override
            public boolean canConvert(Class type) {
                return TreeSet.class.isAssignableFrom(type);
            }

            @Override
            public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                TreeSet<MappingFunction> set = new TreeSet<>();
                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    MappingFunction function = (MappingFunction) context.convertAnother(set, MappingFunction.class);
                    set.add(function);
                    reader.moveUp();
                }
                return set;
            }
        });

        // Explicitly register the MappingFunction and FunctionList classes
        stream.processAnnotations(MappingFunction.class);
        stream.processAnnotations(MappingFunction.FunctionList.class);

        // Allow these specific types
        stream.allowTypes(new Class[] {
                MappingFunction.class,
                MappingFunction.FunctionList.class,
                AssertionList.class, // Explicitly allow nested class
                // MapToCRM.Mappings.class // Explicitly allow nested class
        });

        // Allow Java collection types
        stream.allowTypeHierarchy(Collection.class);
        stream.allowTypeHierarchy(Map.class);
        stream.allowTypeHierarchy(TreeSet.class);
        stream.allowTypeHierarchy(NavigableMap.class);
        stream.allowTypeHierarchy(TreeMap.class);

        // Allow the specific class and its hierarchy
        stream.allowTypeHierarchy(clazz);

        // Allow types for converters
        stream.allowTypesByWildcard(new String[] {
                "eu.delving.sip.**", // Ensure this covers all test classes
                "eu.delving.metadata.**",
                "eu.delving.rdf.**",
                "java.util.**",
                "java.lang.**"
        });

        // Configure stream settings
        stream.setMarshallingStrategy(new TreeMarshallingStrategy());
        stream.registerConverter(new Tag.Converter());
        stream.registerConverter(new Path.Converter());

        // Process annotations last after security is configured
        stream.processAnnotations(clazz);

        return stream;
    }

    public static XStream asSecureXStream(XStream stream) {
        stream.setMode(XStream.NO_REFERENCES);

        // Security configuration
        stream.addPermission(NoTypePermission.NONE);
        stream.addPermission(NullPermission.NULL);
        stream.addPermission(PrimitiveTypePermission.PRIMITIVES);
        stream.ignoreUnknownElements();

        stream.registerConverter(new CollectionConverter(stream.getMapper()) {
            @Override
            public boolean canConvert(Class type) {
                return TreeSet.class.isAssignableFrom(type);
            }

            @Override
            public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                // Use a natural order TreeSet
                TreeSet<Object> set = new TreeSet<>();
                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    set.add(context.convertAnother(set, Object.class));
                    reader.moveUp();
                }
                return set;
            }
        });

        // Allow Java collection types
        stream.allowTypeHierarchy(Collection.class);
        stream.allowTypeHierarchy(Map.class);
        stream.allowTypeHierarchy(String.class);
        stream.allowTypeHierarchy(TreeSet.class);

        // Allow specific application packages
        stream.allowTypesByWildcard(new String[] {
                "eu.delving.sip.**",
                "eu.delving.metadata.**",
                "eu.delving.rdf.**",
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
                "java.util.**",
                "java.lang.**",
                "java.time.**"
        });

        return stream;
    }

    public static XStream createSecureXStream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());

        // Security configuration
        stream.addPermission(NoTypePermission.NONE);
        stream.addPermission(NullPermission.NULL);
        stream.addPermission(PrimitiveTypePermission.PRIMITIVES);

        // Register custom converter for TreeSet
        stream.registerConverter(new CollectionConverter(stream.getMapper()) {
            @Override
            public boolean canConvert(Class type) {
                return TreeSet.class.isAssignableFrom(type);
            }

            @Override
            public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                // Create TreeSet with a custom comparator to handle non-Comparable objects
                TreeSet<Object> set = new TreeSet<>((o1, o2) -> {
                    if (o1 == null || o2 == null) {
                        return o1 == null ? (o2 == null ? 0 : -1) : 1;
                    }
                    // Use toString() comparison as fallback if objects aren't Comparable
                    if (o1 instanceof Comparable && o2 instanceof Comparable) {
                        return ((Comparable) o1).compareTo(o2);
                    }
                    return o1.toString().compareTo(o2.toString());
                });

                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    // Try to determine the actual type from the XML
                    Class<?> itemType = context.getRequiredType();
                    if (itemType == null || itemType == Object.class) {
                        itemType = Path.class; // or whatever your expected type is
                    }
                    Object item = context.convertAnother(set, itemType);
                    set.add(item);
                    reader.moveUp();
                }
                return set;
            }
        });

        // Allow Java collection types
        stream.allowTypeHierarchy(Collection.class);
        stream.allowTypeHierarchy(Map.class);

        // Allow specific types
        stream.allowTypes(new Class[] {
                TreeSet.class,
                TreeMap.class,
                ArrayList.class,
                HashSet.class,
                HashMap.class,
                AssertionList.class,
                MappingFunction.class,
        });

        // Allow specific packages
        stream.allowTypesByWildcard(new String[] {
                "eu.delving.sip.**",
                "eu.delving.metadata.**",
                "eu.delving.rdf.**",
                "eu.delving.**",
                "java.util.**",
                "java.lang.**",
                "java.time.**"
        });

        return stream;
    }
}
