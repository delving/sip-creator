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
import java.util.Arrays;
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
        return new Node(getCurrentNode(), name, new ArrayList());
    }

    @Override
    protected Object createNode(Object name, Object value) {
        return new Node(getCurrentNode(), name, value);
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        return new Node(getCurrentNode(), name, attributes, new ArrayList());
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
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
                    runMapClosures((Node) node, (Map<String, Object>) object);
                }
                else if (object instanceof Closure) {
                    node = createNode(name);
                    setValuesFromClosure((Node) node, (Closure) object);
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
                        runMapClosures((Node) node, (Map<String, Object>) object1); // todo: maybe this has to repeat upon multiple values
                        setValuesFromClosure((Node) node, (Closure) object2);
                    }
                    else {
                        node = createNode(name, (Map) object1, object2);
                    }
                }
                else {
                    if (object2 instanceof Closure) {
                        node = createNode(name, object1);
                        setValuesFromClosure((Node) node, (Closure) object2);
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

    private void setValuesFromClosure(Node node, Closure closure) {
        ClosureResult result = runClosure(node, closure);
        if (result.string != null) node.setValue(result.string);
        if (result.list != null) for (Object member : result.list) new Node(node, node.name(), member.toString());
    }

    private void runMapClosures(Node node, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Closure) {
                ClosureResult result = runClosure(node, (Closure) entry.getValue());
                map.put(entry.getKey(), result.string);
            }
        }
    }

    private ClosureResult runClosure(Node node, Closure closure) {
        ClosureResult cr = new ClosureResult();
        Object oldCurrent = getCurrent();
        setCurrent(node);
        setClosureDelegate(closure, node);
        Object result = closure.call();
        setCurrent(oldCurrent);
        if (result instanceof String) {
            cr.string = (String) result;
        }
        else if (result instanceof GString) {
            cr.string = result.toString();
        }
        else if (result instanceof List) {
            cr.list = (List) result;
        }
        else if (result instanceof Object[]) {
            cr.list = Arrays.asList((Object[])result);
        }
        return cr;
    }

    private static class ClosureResult {
        public String string;
        public List list;
    }
}
