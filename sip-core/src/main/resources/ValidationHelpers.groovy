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
    if (!value) return true;
    try {
        value = stringify(value);
        new URL(value.toString())
        return true;
    }
    catch (Exception e) {
        return false;
    }
}

def isOption(Object value) {
    if (!value) return true;
    if (value instanceof NodeList) {
        NodeList list = (NodeList)value
        boolean option = true;
        for (nodeObject in list) {
            if (!checkOption((Node)nodeObject)) option = false;
        }
        return option;
    }
    else if (value instanceof Node) {
        return checkOption((Node)value)
    }
    else {
        throw new RuntimeException("Checking option for "+value.getClass())
    }
}

def isUnique(Object value) {
    if (!value) return true;
    if (value instanceof NodeList) {
        NodeList list = (NodeList)value
        if (list.size() == 1) {
            return checkUnique((Node)list[0])
        }
        else {
            return false
        }
    }
    else if (value instanceof Node) {
        return checkUnique((Node)value)
    }
    else {
        throw new RuntimeException("Checking unique for "+value.getClass())
    }
}
