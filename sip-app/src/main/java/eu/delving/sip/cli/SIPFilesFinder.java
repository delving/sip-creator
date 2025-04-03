/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.cli;

import eu.delving.sip.model.SipProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.delving.schema.SchemaVersion;
import eu.delving.schema.SchemaRepository;
import eu.delving.metadata.CachedResourceResolver;
import eu.delving.sip.Application.ResolverContext;
import eu.delving.sip.files.SchemaFetcher;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import static eu.delving.sip.files.Storage.NARTHEX_URL;
import static eu.delving.sip.base.HttpClientFactory.createHttpClient;

import org.apache.http.client.HttpClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class to find and validate required files in a SIP directory
 */
public class SIPFilesFinder {
    private static final Logger logger = LoggerFactory.getLogger(SIPFilesFinder.class);

    public static class SIPFiles {
        private final Path recordDefinition;
        private final Path validationSchema;
        private final Path mappingFile;
        private final Path sourceFile;
        private final Properties properties;
        private Storage storage;

        public SIPFiles(Path recordDefinition, Path validationSchema, Path mappingFile, Path sourceFile,
                Properties properties) {
            this.recordDefinition = recordDefinition;
            this.validationSchema = validationSchema;
            this.mappingFile = mappingFile;
            this.sourceFile = sourceFile;
            this.properties = properties;
        }

        public Path getRecordDefinition() {
            return recordDefinition;
        }

        public Path getValidationSchema() {
            return validationSchema;
        }

        public Path getMappingFile() {
            return mappingFile;
        }

        public Path getSourceFile() {
            return sourceFile;
        }

        public File getStorageDir() {
            return this.mappingFile.getParent().getParent().toFile();
        }

        public Path getSipDir() {
            return this.mappingFile.getParent();
        }

        public SchemaVersion getSchemaVersion() {
            String schemaVersion = FilenameExtractor.extractBaseName(this.recordDefinition.toString());
            return new SchemaVersion(schemaVersion);
        }

        @Override
        public String toString() {
            return String.format("""
                    SIPFiles {
                        recordDefinition: %s
                        validationSchema: %s
                        mappingFile: %s
                        sourceFile: %s
                    }""",
                    recordDefinition, validationSchema, mappingFile, sourceFile);
        }

        public Properties getProperties() {
            return properties;
        }

        public Storage getStorage() throws StorageException {
            if (this.storage == null) {
                this.storage = setupStorage();
            }
            return this.storage;
        }

        private Storage setupStorage() throws StorageException {
            String serverUrl = properties.getProperty(NARTHEX_URL, "http://delving.org/narthex");
            HttpClient httpClient = createHttpClient(serverUrl).build();
            SchemaRepository schemaRepository;
            try {
                schemaRepository = new SchemaRepository(new SchemaFetcher(httpClient));
            } catch (IOException e) {
                throw new StorageException("Unable to create Schema Repository", e);
            }
            ResolverContext context = new ResolverContext();
            Storage storage = new StorageImpl(this.getStorageDir(), properties, schemaRepository,
                    new CachedResourceResolver(context));
            context.setStorage(storage);
            context.setHttpClient(httpClient);
            return storage;
        }
    }

    /**
     * Finds all required files in the specified SIP directory
     *
     * @param sipDir The directory containing SIP files
     * @return SIPFiles object containing paths to all required files
     * @throws IOException if directory cannot be accessed or required files are
     *                     missing
     */
    public static SIPFiles findRequiredFiles(Path sipDir) throws IOException {
        if (!Files.isDirectory(sipDir)) {
            throw new IOException("SIP directory does not exist or is not a directory: " + sipDir);
        }

        try (Stream<Path> paths = Files.list(sipDir)) {
            // Find record definition file
            Path recordDefinition = findFile(sipDir, "*record-definition.xml");
            if (recordDefinition == null) {
                throw new IOException("No record definition file found in " + sipDir);
            }

            // Find validation schema
            Path validationSchema = findFile(sipDir, "*validation.xsd");
            if (validationSchema == null) {
                throw new IOException("No validation schema found in " + sipDir);
            }

            // Find latest mapping file
            //Path mappingFile = findLatestMappingFile(sipDir);
            Path mappingFile = findLatestFile(sipDir, "mapping_*.xml");
            if (mappingFile == null) {
                throw new IOException("No mapping file found in " + sipDir);
            }

            // Find latest source file
            //Path sourceFile = sipDir.resolve("source.xml.gz");
            Path sourceFile = findLatestFile(sipDir, "source.xml.{gz,zst}");
            if (!Files.exists(sourceFile)) {
                throw new IOException("Source file not found: " + sourceFile);
            }

            Properties properties = loadPropertiesFromDir(sipDir.toFile());

            return new SIPFiles(recordDefinition, validationSchema, mappingFile, sourceFile, properties);
        }
    }

    private static Path findFile(Path directory, String glob) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);

        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(path -> matcher.matches(path.getFileName()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path findLatestFile(Path directory, String suffix) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:{*_,}" + suffix);

        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(path -> matcher.matches(path.getFileName()))
                    .max((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p1)
                                    .compareTo(Files.getLastModifiedTime(p2));
                        } catch (IOException e) {
                            logger.warn("Error comparing file modification times", e);
                            return 0;
                        }
                    })
                    .orElse(null);
        }
    }

    private static Properties loadPropertiesFromDir(File currentDir) throws IOException {
        Properties prop = new Properties();
        File propsFile = new File(currentDir.getParentFile().getParentFile(), SipProperties.FILE_NAME);

        if (!propsFile.exists()) {
            throw new FileNotFoundException("Properties file not found at: " + propsFile.getAbsolutePath());
        }

        try (FileInputStream input = new FileInputStream(propsFile)) {
            prop.load(input);
        }

        return prop;
    }
}
