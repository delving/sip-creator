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
    return validationReference.allowOption(node)
}

def checkUnique(Node node) {
    return validationReference.isUnique(node.text())
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

def isUnique(Object object) {
    if (object instanceof NodeList) {
        NodeList list = (NodeList)object
        if (list.size() == 1) {
            return checkUnique((Node)list[0])
        }
        else {
            return false
        }
    }
    else if (object instanceof Node) {
        return checkUnique((Node)object)
    }
    else {
        throw new RuntimeException("Checking unique for "+object.getClass())
    }
}
