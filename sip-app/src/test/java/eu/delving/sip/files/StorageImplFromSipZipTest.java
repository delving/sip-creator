/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.sip.files;

import eu.delving.sip.base.CancelException;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.base.ProgressListener;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageImplFromSipZipTest {

    @Test
    void cancelledImportLeavesNoSourceFile() throws Exception {
        File home = Files.createTempDirectory("sip-storage-test").toFile();
        File sipZip = new File(home, "testset.sip.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(sipZip))) {
            zip.putNextEntry(new ZipEntry("source.xml"));
            zip.write("<pockets><pocket id=\"1\"><a>b</a></pocket></pockets>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        StorageImpl storage = new StorageImpl(home, new Properties(), null, null);
        DataSet dataSet = storage.createDataSet("testset");

        ProgressListener cancelling = new ProgressListener() {
            @Override public void setProgressMessage(String message) {}
            @Override public void prepareFor(int total) {}
            @Override public void setProgress(int progress) throws CancelException { throw new CancelException(); }
            @Override public Feedback getFeedback() { return null; }
        };

        assertThrows(StorageException.class, () -> dataSet.fromSipZip(sipZip, cancelling));

        String[] leftovers = dataSet.getSipFile().list((dir, name) -> name.startsWith("source.xml"));
        assertEquals(0, leftovers.length,
                "a cancelled import must not leave a partial source file behind, found " + String.join(",", leftovers));
        assertEquals(DataSetState.ABSENT, dataSet.getState());
    }
}
