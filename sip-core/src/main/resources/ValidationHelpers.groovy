// ValidationHelpers contains methods to help with building validation assertions

def stringify(Object value) {
    if (value instanceof NodeList) {
        NodeList list = (NodeList)value;
        if (list.size() == 1) {
            value = list[0]
        }
    }
    if (value instanceof Node) {
        value = ((Node)value).value()
    }
    return value.toString()
}

def isUrl(Object value) {
    try {
        value = stringify(value);
        println "isUrl(${value}) : ${value.getClass()}"
        new URL(value.toString())
        return true;
    }
    catch (Exception e) {
        return false;
    }
}

//def isOption(Object value, String prefix, String localName) {
//    return options.hasOption(stringify(value), prefix, localName)
//}

