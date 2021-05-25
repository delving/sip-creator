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

package eu.delving.schema;

import eu.delving.schema.util.XStreamFactory;
import eu.delving.schema.xml.Schema;
import eu.delving.schema.xml.SchemaFile;
import eu.delving.schema.xml.Schemas;
import eu.delving.schema.xml.Version;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Try out the ideas
 *
 *
 */

public class SchemaRepository {
    private MessageDigest messageDigest;
    private Schemas schemas;
    private Fetcher fetcher;
    private Map<SchemaKey, SchemaResponse> cache = new HashMap<SchemaKey, SchemaResponse>();

    public SchemaRepository(Fetcher fetcher) throws IOException {
        this.fetcher = fetcher;
        this.schemas = (Schemas) XStreamFactory.getSchemasStream().fromXML(fetcher.fetchList());
        try {
            this.messageDigest = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available??");
        }
    }

    public SchemaResponse getSchema(SchemaVersion schemaVersion, SchemaType schemaType) throws IOException {
        String hash = null;
        for (Schema schema : schemas.schemas) {
            if (!schema.prefix.equals(schemaVersion.getPrefix())) continue;
            for (Version version : schema.versions) {
                if (!version.number.equals(schemaVersion.getVersion())) continue;
                for (SchemaFile schemaFile : version.files) {
                    if (!schemaFile.name.equals(schemaType.fileName)) continue;
                    hash = schemaFile.hash;
                    break;
                }
            }
        }
        if (hash == null) return null;
        SchemaKey key = new SchemaKey(schemaVersion, schemaType);
        SchemaResponse response = cache.get(key);
        if (response == null) {
            String schemaText = fetcher.fetchSchema(schemaVersion, schemaType);
            if (schemaText != null) {
                boolean isValidated = false;
                if (fetcher.isValidating() && !hash.isEmpty()) {
                    String foundHash = getHashString(schemaText.trim());
                    if (!hash.equals(foundHash)) {
                        throw new IllegalStateException(schemaVersion.getFullFileName(schemaType) + ": expected hash " + foundHash);
                    }
                    isValidated = true;
                }
                cache.put(key, response = new SchemaResponse(schemaText, isValidated));
            }
        }
        return response;
    }

    private String getHashString(String value) {
        try {
            return toHexadecimal(messageDigest.digest(value.getBytes("UTF-8")));
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

//    private String fetchCachedSchema(SchemaVersion schemaVersion, SchemaType schemaType) throws IOException {
//        SchemaKey key = new SchemaKey(schemaVersion, schemaType);
//        String schema = cache.get(key);
//        if (schema == null) {
//            schema = fetcher.fetchSchema(schemaVersion, schemaType);
//            cache.put(key, schema);
//        }
//        return schema;
//    }

    private static class SchemaKey {
        private SchemaVersion schemaVersion;
        private SchemaType schemaType;

        private SchemaKey(SchemaVersion schemaVersion, SchemaType schemaType) {
            this.schemaVersion = schemaVersion;
            this.schemaType = schemaType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SchemaKey schemaKey = (SchemaKey) o;
            return schemaType == schemaKey.schemaType && schemaVersion.equals(schemaKey.schemaVersion);
        }

        @Override
        public int hashCode() {
            int result = schemaVersion.hashCode();
            result = 31 * result + schemaType.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return String.format("SchemaKey(%s, %s)", schemaVersion, schemaType);
        }
    }

    // == all of this is for binding


    static final String HEXES = "0123456789ABCDEF";

    private static String toHexadecimal(byte[] raw) {
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }
}
