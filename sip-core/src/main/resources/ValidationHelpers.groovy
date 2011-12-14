// ValidationHelpers contains methods to help with building validation assertions

private static String stringifyFirst(a) {
    if (!a) return ''
    if (a instanceof String) return a
    if (a instanceof Node) return stringifyFirst(a.value())
    if (a instanceof List) return stringifyFirst(a[0])
    return a.toString();
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
        String string = stringifyFirst(value);
        new URL(string)
        return true;
    }
    catch (Exception e) {
        e.printStackTrace()
        return false;
    }
}

def isOption(Object value) {
    if (!value) return true;
    if (value instanceof List) {
        List list = (List)value
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
    if (value instanceof List) {
        List list = (List)value
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
