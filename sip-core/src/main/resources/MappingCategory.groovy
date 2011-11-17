import eu.delving.groovy.DiscardRecordException
import eu.delving.groovy.GroovyList
import eu.delving.groovy.GroovyNode

// MappingCategory is a class used as a Groovy Category to add methods to existing classes

public class MappingCategory {

    private static GroovyList toList(a) {
        if (a instanceof GroovyList) {
            return a
        }
        else if (a instanceof List) {
            if (!a) {
                return new GroovyList()
            }
            else if (a.size() == 1) {
                if (a[0] instanceof GroovyList) {
                    return (GroovyList) a[0]
                }
                else if (a instanceof List) {
                    return toList(a[0])
                }
                else {
                    return new GroovyList(a[0])
                }
            }
            else {
                throw new RuntimeException("Unable to interpret list of ${a.size()} entries")
            }
        }
        else if (a instanceof GroovyNode) {
            return toList(((GroovyNode) a).value())
        }
        else {
            return new GroovyList(a)
        }
    }

    static void discard(Boolean condition, String why) {
        if (condition) throw new DiscardRecordException(why)
    }

    static String getAt(GroovyNode node, Object what) {
        return node.toString()[what]
    }

    static int indexOf(GroovyNode node, String string) {
        return node.text().indexOf(string)
    }

    static String substring(GroovyNode node, int from) {
        return node.text().substring(from);
    }

    static String substring(GroovyNode node, int from, int to) {
        return node.text().substring(from, to);
    }

    static GroovyList ifAbsentUse(GroovyList list, Object factVariable) {
        if (!list) {
            list += factVariable
        }
        else if (list.size() == 1) {
            GroovyNode node = (GroovyNode) list[0]
            if (!node.text()) {
                list += factVariable
            }
        }
        return list
    }

    static Object plus(a, b) { // operator +
        GroovyList both = new GroovyList()
        both.addAll(a.children())
        both.addAll(b.children())
        return both;
    }

    static Object or(a, b) { // operator |
        a = toList(a)
        b = toList(b)
        GroovyList listA = a.children()
        GroovyList listB = b.children()
        GroovyList tupleList = new GroovyList()
        int max = Math.min(listA.size(), listB.size());
        for (Integer index: 0..(max - 1)) tupleList.add(new GroovyList(listA[index], listB[index]))
        return tupleList
    }

    static Object multiply(a, Closure closure) { // operator *
        a = toList(a)
        for (Object child: a.children()) closure.call(child);
        return null
    }

    static GroovyList multiply(a, String delimiter) {
        a = toList(a)
        Iterator walk = a.children().iterator();
        StringBuilder out = new StringBuilder()
        while (walk.hasNext()) {
            out.append(walk.next())
            if (walk.hasNext()) {
                out.append(delimiter)
            }
        }
        return new GroovyList(out.toString())
    }

    static Object power(a, Closure closure) {  // operator **
        a = toList(a)
        for (Object child: a.children()) {
            closure.call(child)
            break
        }
        return null
    }

    static GroovyList mod(a, String regex) {
        a = toList(a)
        GroovyList all = new GroovyList();
        for (Object node: a.children()) {
            if (node instanceof GroovyNode) {
                all += new GroovyList(node.text().split(regex))
            }
        }
        return all;
    }

    static GroovyList extractYear(a) {
        a = toList(a)
        String text = a.text()
        GroovyList result = new GroovyList()
        switch (text) {

            case ~/$normalYear/:
                result += (text =~ /$year/)[0]
                break

            case ~/$yearAD/:
                result += (text =~ /$yr/)[0] + ' AD'
                break

            case ~/$yearBC/:
                result += (text =~ /$yr/)[0] + ' BC'
                break

            case ~/$yearRange/:
                def list = text =~ /$year/
                if (list[0] == list[1]) {
                    result += list[0]
                }
                else {
                    result += list[0]
                    result += list[1]
                }
                break

            case ~/$yearRangeBrief/:
                def list = text =~ /\d{1,4}/
                result += list[0]
                result += list[0][0] + list[0][1] + list[1]
                break

            case ~/$yr/:
                result += text + ' AD'
                break

            default:
                text.eachMatch(/$year/) {
                    result += it
                }
                break
        }
        return result
    }

    static String toId(a, spec) {
        a = toList(a)
        String identifier = a.text()
        if (!spec) {
            throw new MissingPropertyException("spec", String.class)
        }
        if (!identifier) {
            throw new MissingPropertyException("Identifier passed to toId", String.class)
        }
        def uriBytes = identifier.toString().getBytes("UTF-8");
        def digest = java.security.MessageDigest.getInstance("SHA-1")
        def hash = new StringBuilder()
        for (Byte b in digest.digest(uriBytes)) {
            hash.append('0123456789ABCDEF'[(b & 0xF0) >> 4])
            hash.append('0123456789ABCDEF'[b & 0x0F])
        }
        return "$spec/$hash"
    }

    static String sanitize(GroovyNode node) {
        return sanitize(node.toString())
    }

    static String sanitize(GroovyList list) {
        return sanitize(list.toString())
    }

    static String sanitize(String text) { // same effect as in Sanitizer.sanitizeGroovy, except apostrophe removal
        text = (text =~ /\n/).replaceAll(' ')
        text = (text =~ / +/).replaceAll(' ')
        return text
    }

    static year = /\d{4}/
    static dateSlashA = /$year\/\d\d\/\d\d\//
    static dateDashA = /$year-\d\d-\d\d/
    static dateSlashB = /\d\d\/\d\d\/$year/
    static dateDashB = /\d\d-\d\d-$year/
    static ad = /(ad|AD|a\.d\.|A\.D\.)/
    static bc = /(bc|BC|b\.c\.|B\.C\.)/
    static yr = /\d{1,3}/
    static yearAD = /$yr\s?$ad/
    static yearBC = /$yr\s?$bc/
    static normalYear = /($year|$dateSlashA|$dateSlashB|$dateDashA|$dateDashB)/
    static yearRangeDash = /$normalYear-$normalYear/
    static yearRangeTo = /$normalYear to $normalYear/
    static yearRange = /($yearRangeDash|$yearRangeTo)/
    static yearRangeBrief = /$year-\d\d/
}