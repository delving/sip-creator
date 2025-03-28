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

package eu.delving.schema.util;

import eu.delving.schema.Fetcher;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * This class plays the role of fetcher and searches locally for the right directory
 *
 *
 */
public class FileSystemFetcher implements Fetcher {
    private File schemas;
    private boolean validating;

    public FileSystemFetcher(boolean validating) {
        this.validating = validating;
    }

    @Override
    public String fetchList() {
        return getFileContents(SCHEMA_DIRECTORY);
    }

    @Override
    public String fetchSchema(SchemaVersion schemaVersion, SchemaType schemaType) {
        return getFileContents(schemaVersion.getPath(schemaType));
    }

    @Override
    public Boolean isValidating() {
        return validating;
    }

    public String getFileContents(String path) {
        if (schemas == null) findSchemasDirectory();
        try {
            return FileUtils.readFileToString(new File(schemas, path), "UTF-8");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void findSchemasDirectory() {
        String here = new File(".").getAbsolutePath();
        schemas = new File(here.substring(0, here.length() - 1)); // remove the dot
        while (true) {
            String schemasPath = schemas.getAbsolutePath();
            if (schemasPath.length() > 1) {
                File[] schemaDirectory = schemas.listFiles(new SchemaFilter());
                if (schemaDirectory.length == 1) {
                    schemas = schemaDirectory[0];
                    break;
                }
            }
            schemas = schemas.getParentFile();
        }
    }

    private class SchemaFilter implements FileFilter {
        @Override
        public boolean accept(File directory) {
            return directory.isDirectory() && directory.getName().equals("schemas.delving.eu");
        }
    }
}
