package eu.delving.sip.cli;

import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.*;
import eu.delving.stats.Stats;

import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class CLIDatasetImpl implements DataSet {

    private final Path inputFile;
    private final Path outputDir;

    public CLIDatasetImpl(Path inputFile, Path outputDir) {
        this.inputFile = inputFile;
        this.outputDir = outputDir;
    }

    @Override
    public InputStream openSourceInputStream() {
        try {
            return new GZIPInputStream(Files.newInputStream(inputFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public File targetOutput() {
        return outputDir.toFile();
    }

    @Override
    public ReportWriter openReportWriter(RecDef recDef) {
        return null;
    }

    // Methods from here on are all unsupported
    @Override
    public File getSipFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSpec() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SchemaVersion getSchemaVersion() {
        return null;
    }

    @Override
    public RecDef getRecDef() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Validator newValidator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataSetState getState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getDataSetFacts() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getHints() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setHints(Map<String, String> hints) throws StorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteResults() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stats getStats() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStats(Stats stats) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RecMapping getRecMapping(RecDefModel recDefModel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RecMapping revertRecMapping(File previousMappingFile, RecDefModel recDefModel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRecMapping(RecMapping recMapping, boolean freeze) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<File> getRecMappingFiles() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReportFile getReport() throws StorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteSource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fromSipZip(File sipZipFile, ProgressListener progressListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public File toSipZip(boolean sourceIncluded) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove() throws StorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(DataSet o) {
        throw new UnsupportedOperationException();
    }
}
