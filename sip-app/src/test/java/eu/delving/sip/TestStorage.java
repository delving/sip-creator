/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip;

import eu.delving.schema.SchemaRepository;
import eu.delving.schema.util.FileSystemFetcher;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.FileImporter;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.SourceConverter;
import eu.delving.stats.Stats;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestStorage {
    @Test
    public void zipImport() throws StorageException, IOException {
        File storageDir = new File(Mockery.getTargetDirectory(), "storage");
        FileUtils.deleteQuietly(storageDir);
        FileSystemFetcher localFetcher = new FileSystemFetcher(true);
        SchemaRepository repo = new SchemaRepository(localFetcher);
        Storage storage = new StorageImpl(storageDir, repo, null);
        File zip = new File(getClass().getResource("/zip/ZipImport.xml.zip").getFile());
        final DataSet dataSet = storage.createDataSet("spek");
        FileUtils.write(new File(dataSet.importedOutput().getParent(), "dataset_facts.txt"),
                "schemaVersions=ese_3.4.0\n"
        );
        DataSetModel dataSetModel = new DataSetModel();
        dataSetModel.setDataSet(dataSet, "ese");
        Work.LongTermWork importer = new FileImporter(zip, dataSet, null);
        MockProgressListener importProgress = new MockProgressListener();
        importer.setProgressListener(importProgress);
        importer.run();
        Assert.assertNotNull(dataSet.openImportedInputStream());
        Assert.assertEquals("Alert list size for import wrong", 0, importProgress.getAlerts().size());
        FileUtils.write(new File(dataSet.importedOutput().getParent(), "hints.txt"),
                "recordCount=6\n" +
                        "recordRootPath=/zip-entries/eadgrp/archdescgrp/dscgrp/ead\n" +
                        "uniqueElementPath=/zip-entries/eadgrp/archdescgrp/dscgrp/ead/eadheader/eadid\n"
        );
        AnalysisParser parser = new AnalysisParser(dataSetModel, 60, new AnalysisParser.Listener() {
            @Override
            public void success(Stats stats) {
                try {
                    dataSet.setStats(stats, false);
                }
                catch (StorageException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void failure(String message, Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        parser.run();
        SourceConverter converter = new SourceConverter(dataSet, new Runnable() {
            @Override
            public void run() {
                Assert.assertEquals("Data set should still be in delimited state", DataSetState.DELIMITED, dataSet.getState(null));
            }
        });
        MockProgressListener convertProgress = new MockProgressListener();
        converter.setProgressListener(convertProgress);
        // the conversion will fail because of a too-large identifier!
        converter.run();
        Assert.assertEquals("Alert list size for convert wrong", 1, convertProgress.getAlerts().size());
    }
}
