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

package eu.delving.schema;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The combination of prefix and version number
 *
 *
 */

public class SchemaVersion implements Comparable<SchemaVersion> {
    private static final Pattern PATTERN = Pattern.compile("([a-z]{3,6})_([0-9][.][0-9][.][0-9]+)");
    private String prefix;
    private String version;

    public SchemaVersion(String string) {
        Matcher matcher = PATTERN.matcher(string);
        if (!matcher.matches()) throw new IllegalArgumentException("Pattern mismatch: " + string);
        this.prefix = matcher.group(1);
        this.version = matcher.group(2);
    }

    public SchemaVersion(String prefix, String version) {
        this(prefix + "_" + version);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getVersion() {
        return version;
    }

    public String getFullFileName(SchemaType schemaType) {
        return String.format(
                "%s_%s_%s",
                prefix,
                version,
                schemaType.fileName
        );
    }

    public String getPath(SchemaType schemaType) {
        return String.format(
                "/%s/%s",
                prefix, getFullFileName(schemaType)
        );
    }

    public String toString() {
        return prefix + "_" + version;
    }

    @Override
    public int compareTo(SchemaVersion schemaVersion) {
        return prefix.compareTo(schemaVersion.prefix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaVersion that = (SchemaVersion) o;
        return prefix.equals(that.prefix) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        int result = prefix.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }
}
