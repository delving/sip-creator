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

import eu.delving.sip.base.Work;
import eu.delving.sip.files.*;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestStorage {
    @Test
    public void zipImport() throws StorageException {
        File storageDir = new File(Mockery.getTargetDirectory(), "storage");
        FileUtils.deleteQuietly(storageDir);
        Storage storage = new StorageImpl(storageDir, null, null);
        File zip = new File(getClass().getResource("/zip/ZipImport.xml.zip").getFile());
        DataSet dataSet = storage.createDataSet("spek", "orgy");
        Work.LongTermWork importer = new FileImporter(zip, dataSet, null);
        importer.setProgressListener(new MockProgressListener());
        importer.run();
        Assert.assertNotNull(dataSet.openImportedInputStream());
    }
}
