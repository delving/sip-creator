/*
 * Copyright 2010 DELVING BV
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

package eu.delving.groovy;

import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.MissingMethodException;
import groovy.util.BuilderSupport;
import groovy.util.Node;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom node builder which executes closures if they are found as attribute values, or if
 * an element closure returns a String or GString.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class NodeBuilder extends BuilderSupport {

    public static NodeBuilder newInstance() {
        return new NodeBuilder();
    }

    @Override
    protected void setParent(Object parent, Object child) {
    }

    @Override
    protected Object createNode(Object name) {
        System.out.println("createNodeZ: " + name);
        return new Node(getCurrentNode(), name, new ArrayList());
    }

    @Override
    protected Object createNode(Object name, Object value) {
        System.out.println("createNodeA: " + name + " " + value);
        return new Node(getCurrentNode(), name, value);
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        System.out.println("createNodeB: " + name + " " + attributes);
        return new Node(getCurrentNode(), name, attributes, new ArrayList());
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        System.out.println("createNodeC: " + name + " " + attributes + " " + value);
        return new Node(getCurrentNode(), name, attributes, value);
    }

    protected Node getCurrentNode() {
        return (Node) getCurrent();
    }

    @Override
    protected Object doInvokeMethod(String methodName, Object name, Object args) {
        Object node;
        List list = InvokerHelper.asList(args);
        switch (list.size()) {
//            case 0:
//                node = createNode(name);
//                break;
            case 1: { ///
                Object object = list.get(0);
                if (object instanceof Map) {
                    node = createNode(name, (Map) object);
                    runMapClosures((Node) node, (Map) object);
                }
                else if (object instanceof Closure) {
                    node = createNode(name);
                    setValueFromClosure((Node) node, (Closure) object);
                }
                else {
                    node = createNode(name, object);
                }
            }
            break;
            case 2: {
                Object object1 = list.get(0);
                Object object2 = list.get(1);
                if (object1 instanceof Map) {
                    if (object2 instanceof Closure) {
                        node = createNode(name, (Map) object1);
                        runMapClosures((Node) node, (Map) object1);
                        setValueFromClosure((Node) node, (Closure) object2);
                    }
                    else {
                        node = createNode(name, (Map) object1, object2);
                    }
                }
                else {
                    if (object2 instanceof Closure) {
                        node = createNode(name, object1);
                        setValueFromClosure((Node) node, (Closure) object2);
                    }
                    else if (object2 instanceof Map) {
                        node = createNode(name, (Map) object2, object1);
                    }
                    else {
                        throw new MissingMethodException(name.toString(), getClass(), list.toArray(), false);
                    }
                }
            }
            break;
            default: {
                throw new MissingMethodException(name.toString(), getClass(), list.toArray(), false);
            }
        }
        if (getCurrent() != null) setParent(getCurrent(), node);
        nodeCompleted(getCurrent(), node);
        return postNodeCompletion(getCurrent(), node);
    }

    private void setValueFromClosure(Node node, Closure closure) {
        String result = runClosure(node, closure);
        if (result != null) node.setValue(result);
    }

    private void runMapClosures(Node node, Map map) {
        for (Object entryObj : map.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObj;
            if (entry.getValue() instanceof Closure) {
                String result = runClosure(node, (Closure) entry.getValue());
                map.put(entry.getKey(), result);
            }
        }
    }

    private String runClosure(Node node, Closure closure) {
        Object oldCurrent = getCurrent();
        setCurrent(node);
        setClosureDelegate(closure, node);
        if (closure.getParameterTypes().length > 0) {
            System.out.println("Lost parameter: " + closure.getParameterTypes()[0] + " length=" + closure.getParameterTypes().length);
        }
        Object result = closure.call();
        setCurrent(oldCurrent);
        if (result instanceof String) {
            return (String) result;
        }
        else if (result instanceof GString) {
            return result.toString();
        }
        else {
            return null;
        }
    }

}
