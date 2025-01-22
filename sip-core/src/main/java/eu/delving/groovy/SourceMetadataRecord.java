package eu.delving.groovy;

/**
 * MetadataRecord with the input XML attached.
 *
 * Probably not worthwhile during bulk running due to possible performance hit, but useful for presenting
 * raw XML in the user interface.
 */
public class SourceMetadataRecord extends MetadataRecord {
    private final String sourceXML;

    SourceMetadataRecord(MetadataRecord metadataRecord, String sourceXML) {
        super(metadataRecord.getRootNode(), metadataRecord.getRecordNumber(), metadataRecord.getRecordCount());
        this.sourceXML = sourceXML;
    }

    public String getSourceXML() {
        return sourceXML;
    }

}
