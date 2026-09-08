/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.sip.files;

import eu.delving.schema.SchemaVersion;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageImplSchemaVersionTest {

    @Test
    void datasetWithoutFactsReportsUnknownSchemaVersion() throws Exception {
        File home = Files.createTempDirectory("sip-storage-test").toFile();
        StorageImpl storage = new StorageImpl(home, new Properties(), null, null);
        DataSet dataSet = storage.createDataSet("testset");

        SchemaVersion schemaVersion = dataSet.getSchemaVersion();

        assertEquals("unknown", schemaVersion.getPrefix(),
                "a dataset with no schemaVersions fact must report the unknown prefix instead of throwing");
    }
}
