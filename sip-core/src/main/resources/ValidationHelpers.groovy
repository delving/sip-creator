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

def checkOption(Node node) {
    return optionSource.allowOption(node)
}

def isUrl(Object value) {
    try {
        value = stringify(value);
        new URL(value.toString())
        return true;
    }
    catch (Exception e) {
        return false;
    }
}

def isOption(Object object) {
    if (object instanceof NodeList) {
        NodeList list = (NodeList)object
        boolean option = true;
        for (nodeObject in list) {
            if (!checkOption((Node)nodeObject)) option = false;
        }
        return option;
    }
    else if (object instanceof Node) {
        return checkOption((Node)object)
    }
    else {
        throw new RuntimeException("Checking option for "+object.getClass())
    }
}
