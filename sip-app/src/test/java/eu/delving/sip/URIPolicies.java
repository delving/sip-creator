package eu.delving.sip;

import java.util.UUID;

/**
 * Adopted from FORTH code
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class URIPolicies {
    /**
     * returns a URI for the mainly described object.
     *
     * @param className    the name of the corresponding CIDOC class
     * @param nameOfMuseum the name of the organization keeping the object.
     * @param entry        the id given to the object by the organization
     * @return the URI of the form "urn:iso21127:(Main_Object)nameOfMuseum:entry"
     *         (e.g. "urn:iso21127:(Main_Object)Germanic_National_Museum,_Graphical_Collection_(Nuremberg):H_3672")
     *         if nameOFMuseum and entry is not null, or a uuid otherwise.
     */
    public String uriForPhysicalObjects(String className, String nameOfMuseum, String entry) {
        if (has(nameOfMuseum, entry)) {
            return encode(PRE + ":(Main_Object)" + nameOfMuseum + "-" + entry);
        }
        return uuid();
    }

    /**
     * returns a uri for a specific place according to the available information about
     * the geographical spaces or coordinates it belongs.
     *
     * @param className   the name of the corresponding CIDOC class
     * @param placeName   the name of the Place
     * @param authority   the authority that has assigned an id to this place
     * @param placeID     the id given to this place by the authority
     * @param coordinates the geographical coordinates of the current place
     * @param spaces      the geographical spaces the place belongs
     * @return a URI in the form "urn:iso21127:(Place)Authority-id" (e.g. "urn:iso21127:(Place)TGN:7001393") if the authority and placeID are not null,
     *         a URI in the form "urn:iso21127:(Place)longitude_latidute" (e.g."urn:iso21127:(Place)37°58'N_23°46'E") if the structure coordinates is not null
     *         a URI in the form "urn:iso21127:(Place)inhabitedPlace-region-nation-continent"(e.g. "urn:iso21127:(Place)Athens-Perifereia_Protevousis-Greece-Europe")if the structure spaces is not null
     *         or a uuid otherwise
     */
    public String uriForPlaces(String className, String placeName, String authority, String placeID, String coordinates, String spaces) {
        if (!placeID.equals("")) {
            return encode(PRE + "(Place)" + placeName + "," + (authority.isEmpty() ? "" : authority + ":") + placeID);
        }
        else if (!coordinates.isEmpty()) {
            return encode(PRE + "(Place)" + placeName + "," + coordinates);
        }
        else if (!spaces.isEmpty()) {
            return encode(PRE + "(Place)" + spaces);
        }
        else {
            return uuid();
        }
    }

    //creates a URI for actor from a given authority and ID
    private String uriActorFromAuthority(String className, String authority, String id) {
        return encode(PRE + ":(Actor" + dashClassName(className, "Actor") + ")" + authority + ":" + id);
    }

    //creates a URI for Actor from his Name and vitalsDate
    private String uriNameDate(String className, String name, String vitalDates) {
        return encode(PRE + ":(Actor" + dashClassName(className, "Actor") + ")" + name + ",b." + vitalDates);
    }

    /**
     * returns a URI for Actor depending on the available information about him (an already registered identifier
     * or his name combined with his vital Dates),
     * or a UUID if there is no such information available.
     *
     * @param className the name of the corresponding CIDOC class
     * @param authority if not null, it represents an authority that has registered
     *                  a specific identifier to the current Actor
     * @param id        the identifier registered to the Actor
     * @param name      the Name of the Actor
     * @param birthDate the date Actor was born
     * @return a URI of the form "urn:iso21127:(Actor)authority:id" (e.g. "urn:iso21127:(Actor)DISKUS-KUE-Datei:10153461")if authority and id are not empty,
     *         a URI of the form "urn:iso21127:(Actor)name,b.birthDate" (e.g. urn:iso21127:(Actor)Camerlohr,_Joseph_von,b.1820) if the name and the vitalDates are not empty,
     *         or a UUID otherwise
     */
    public String uriForActors(String className, String authority, String id, String name, String birthDate) {
        if (has(authority, id)) {
            return uriActorFromAuthority(className, authority, id);
        }
        else if (!birthDate.equals("")) {
            return uriNameDate(className, name, birthDate);
        }
        else {
            return uuid();
        }
    }

    /**
     * returns a URI for Physical Things
     *
     * @param className the name of the corresponding CIDOC class
     * @param thing     the name of the thing
     * @return a URI of the form "urn:iso21127:(Thing-ClassName)thing"
     *         (e.g. "urn:iso21127:(Thing-Document)obj_00120252")
     */
    public String uriPhysThing(String className, String thing) {
        return encode(PRE + ":(Thing" + dashClassName(className) + ")" + thing);
    }

    /**
     * returns a URI for Conceptual Things
     *
     * @param className the name of the corresponding CIDOC class
     * @param thing     the name of the thing
     * @return a URI of the form "urn:iso21127:(Conceptual-ClassName)thing"
     *         (e.g. "urn:iso21127:(Conceptual-Right)Copyright")
     */
    public String uriConceptual(String className, String thing) {
        return encode(PRE + ":(Conceptual" + dashClassName(className) + ")" + thing);
    }


    /**
     * returns a URI for a specific Type
     *
     * @param className the name of the corresponding CIDOC class
     * @param type      the type
     * @return a URI of the form "urn:iso21127:(Type-ClassName)type"
     *         (e.g."urn:iso21127:(Type)Paper")
     */
    public String uriType(String className, String type) {
        if (has(type)) {
            return encode(PRE + ":(Type" + dashClassName(className, "Type") + ")" + type);
        }
        return uuid();
    }

    /**
     * returns a URI for a specific appellation about an entity determined by the subject URI given
     *
     * @param className   the name of the corresponding CIDOC class
     * @param subjUri     the URI of the subject to which the appellation corresponds
     * @param appellation the appellation of the subject
     * @return a URI if the form: "urn:iso21127:(Appellation-ClassName)@subjUri@appellation"
     *         (e.g. "urn:iso21127:(Appellation-Actor_Appellation)@urn:iso21127:(Actor)DISKUS-KUE-Datei:10153461@Camerlohr,_Joseph_von")
     */
    public String appellationURI(String className, String subjUri, String appellation) {
        if (has(subjUri, appellation)) {
            return encode(PRE + ":(Appellation" + dashClassName(className, "Appellation") + ")@" + subjUri + "@" + appellation);
        }
        return uuid();
    }

    /**
     * returns a URI for a specific dimensions about an entity determined by the subject URI given
     *
     * @param className  the name of the corresponding CIDOC class
     * @param subjUri    the URI of the subject to which the dimensions correspond
     * @param dimensions the dimensions of the subject
     * @return a URI of the form: "urn:iso21127:(Dimensions)@subjUri@dimensions"
     *         (e.g. "urn:iso21127:(Dimensions)@urn:iso21127:(Main_Object)Germanic_National_Museum,_Graphical_Collection_(Nuremberg):H_3672@44,3x35,4_cm)
     */
    public String dimensionURI(String className, String subjUri, String dimensions) {
        if (has(subjUri, dimensions)) {
            return encode(PRE + ":(Dimensions)@" + subjUri + "@" + dimensions);
        }
        return uuid();
    }

    /**
     * returns a URI for a TimeSpan defined by the argument timespan
     *
     * @param className the name of the corresponding CIDOC class
     * @param timespan  the period of time
     * @return a URI of the form: "urn:iso21127:(TimeSpan)timespan"
     *         (e.g. "urn:iso21127:(TimeSpan)1871")
     */
    public String uriTimeSpan(String className, String timespan) {
        if (has(timespan)) {
            return PRE + ":(TimeSpan)" + timespan;
        }
        return uuid();
    }

    /**
     * returns a URI for an Event based either on an already registered identifier for it, or on the type of the event
     * determined by the className - if it is unique for the subject it is referred to(Birth, Death, Creation, etc.),
     * or a UUID otherwise
     *
     * @param className the name of the corresponding CIDOC class
     * @param authority the qualified authority for the event identifier
     * @param eventID   the event identifier
     * @param subjUri   the URI of the subject to which the event corresponds
     * @return a uri of the form "urn:iso21127:(Event)Authority-eventID" (e.g. "urn:iso21127:(Event)KB:1334")if an identifier already exists,
     *         one of the form "urn:iso21127:(Event)@subjUri@className" (e.g. "urn:iso21127:(Event)@urn:iso21127:(Actor)DISKUS-KUE-Datei:10153461@Birth)
     *         if the className is one of the Birth, Death, Creation, etc. in other words if the event is unique for the referred subject,
     *         or a UUID otherwise
     */
    public String uriEvents(String className, String authority, String eventID, String subjUri) {
        if (has(eventID)) {
            return PRE + ":(Event)" + authority + ":" + eventID;
        }
        if (isUniqueEvent(className) && has(subjUri)) {
            return PRE + ":(Event)@" + subjUri + "@" + className;
        }
        return uuid();
    }

    private boolean isUniqueEvent(String className) {
        for (String unique : UNIQUE_EVENTS) {
            if (unique.equals(className)) return true;
        }
        return false;
    }

    /**
     * returns a Literal. That rule refers to any information being represented as a String.
     * This is not a urn form.
     *
     * @param className the name of the corresponding CIDOC class
     * @param type      the type of information described
     * @param note      the String representation
     * @return a URI of the form "literal:type:note" (e.g. "literal:recordType:Item")
     */
    public String createLiteral(String className, String type, String note) {
        return "literal:" + type + ":" + note;
    }

    private String dashClassName(String className, String defaultName) {
        return (className.equals(defaultName) ? "" : "-" + className);
    }

    private String dashClassName(String className) {
        return (className.isEmpty() ? "" : "-" + className);
    }

    private boolean has(String... parts) {
        for (String part : parts) if (part == null || part.isEmpty()) return false;
        return true;
    }

    private static String uuid() {
        return URN + ":uuid:" + UUID.randomUUID();
    }

    private static String encode(String s) {
        StringBuilder out = new StringBuilder();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            int ch = s.charAt(i);
            if ('A' <= ch && ch <= 'Z') {        // 'A'..'Z'
                out.append((char) ch);
            }
            else if ('a' <= ch && ch <= 'z') {    // 'a'..'z'
                out.append((char) ch);
            }
            else if ('0' <= ch && ch <= '9') {    // '0'..'9'
                out.append((char) ch);
            }
            else if (ch == ' ') {            // space
                out.append('_');
            }
            else if (ch == '-' || ch == '_' // unreserved
                    || ch == '.' || ch == '!' || ch == '*' || ch == '(' || ch == ')' ||
                    ch == '+' || ch == ',' || ch == ':' || ch == '=' || ch == '@' || ch == ';' || ch == '$') {
                out.append((char) ch);
            }
            else if (ch <= 0x007f) {        // other ASCII
                out.append(hex[ch]);
            }
            else if (ch <= 0x07FF) {        // non-ASCII <= 0x7FF
                out.append(hex[0xC0 | (ch >> 6)]);
                out.append(hex[0x80 | (ch & 0x3F)]);
            }
            else {// 0x7FF < ch <= 0xFFFF
                out.append(hex[0xe0 | (ch >> 12)]);
                out.append(hex[0x80 | ((ch >> 6) & 0x3F)]);
                out.append(hex[0x80 | (ch & 0x3F)]);
            }
        }
        return out.toString();
    }

    final static String[] hex = {
            "%00", "%01", "%02", "%03", "%04", "%05", "%06", "%07",
            "%08", "%09", "%0a", "%0b", "%0c", "%0d", "%0e", "%0f",
            "%10", "%11", "%12", "%13", "%14", "%15", "%16", "%17",
            "%18", "%19", "%1a", "%1b", "%1c", "%1d", "%1e", "%1f",
            "%20", "%21", "%22", "%23", "%24", "%25", "%26", "%27",
            "%28", "%29", "%2a", "%2b", "%2c", "%2d", "%2e", "%2f",
            "%30", "%31", "%32", "%33", "%34", "%35", "%36", "%37",
            "%38", "%39", "%3a", "%3b", "%3c", "%3d", "%3e", "%3f",
            "%40", "%41", "%42", "%43", "%44", "%45", "%46", "%47",
            "%48", "%49", "%4a", "%4b", "%4c", "%4d", "%4e", "%4f",
            "%50", "%51", "%52", "%53", "%54", "%55", "%56", "%57",
            "%58", "%59", "%5a", "%5b", "%5c", "%5d", "%5e", "%5f",
            "%60", "%61", "%62", "%63", "%64", "%65", "%66", "%67",
            "%68", "%69", "%6a", "%6b", "%6c", "%6d", "%6e", "%6f",
            "%70", "%71", "%72", "%73", "%74", "%75", "%76", "%77",
            "%78", "%79", "%7a", "%7b", "%7c", "%7d", "%7e", "%7f",
            "%80", "%81", "%82", "%83", "%84", "%85", "%86", "%87",
            "%88", "%89", "%8a", "%8b", "%8c", "%8d", "%8e", "%8f",
            "%90", "%91", "%92", "%93", "%94", "%95", "%96", "%97",
            "%98", "%99", "%9a", "%9b", "%9c", "%9d", "%9e", "%9f",
            "%a0", "%a1", "%a2", "%a3", "%a4", "%a5", "%a6", "%a7",
            "%a8", "%a9", "%aa", "%ab", "%ac", "%ad", "%ae", "%af",
            "%b0", "%b1", "%b2", "%b3", "%b4", "%b5", "%b6", "%b7",
            "%b8", "%b9", "%ba", "%bb", "%bc", "%bd", "%be", "%bf",
            "%c0", "%c1", "%c2", "%c3", "%c4", "%c5", "%c6", "%c7",
            "%c8", "%c9", "%ca", "%cb", "%cc", "%cd", "%ce", "%cf",
            "%d0", "%d1", "%d2", "%d3", "%d4", "%d5", "%d6", "%d7",
            "%d8", "%d9", "%da", "%db", "%dc", "%dd", "%de", "%df",
            "%e0", "%e1", "%e2", "%e3", "%e4", "%e5", "%e6", "%e7",
            "%e8", "%e9", "%ea", "%eb", "%ec", "%ed", "%ee", "%ef",
            "%f0", "%f1", "%f2", "%f3", "%f4", "%f5", "%f6", "%f7",
            "%f8", "%f9", "%fa", "%fb", "%fc", "%fd", "%fe", "%ff"
    };
    private static final String URN = "URN";
    private static final String ISO = "iso21127";
    private static final String PRE = URN + ":" + ISO;
    private static final String[] UNIQUE_EVENTS = {
            "Birth",
            "Transformation",
            "Production",
            "Creation",
            "Formation",
            "Destruction",
            "Dissolution",
            "Death",
            "Transformation",
    };
}
