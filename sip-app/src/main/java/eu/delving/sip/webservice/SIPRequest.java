package eu.delving.sip.webservice;

public class SIPRequest {

    public String mappingURL;
    public String recordDefinitionURL;
    public String input;

    @Override
    public String toString() {
        return "SIPRequest(mappingURL=" + mappingURL + ", recordDefinitionURL=" + recordDefinitionURL + ", input=" + input + ")";
    }
}
