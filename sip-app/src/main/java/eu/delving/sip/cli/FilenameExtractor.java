package eu.delving.sip.cli;

import java.nio.file.Path;

public class FilenameExtractor {

    public static String extractBaseName(String filepath) {
        if (filepath == null || filepath.isEmpty()) {
            return "";
        }

        // Get just the filename from the path (everything after last / or \)
        String filename = filepath.contains("/")
                ? filepath.substring(filepath.lastIndexOf('/') + 1)
                : filepath.contains("\\")
                        ? filepath.substring(filepath.lastIndexOf('\\') + 1)
                        : filepath;

        String suffix = "_record-definition.xml";
        int index = filename.indexOf(suffix);

        return index != -1 ? filename.substring(0, index) : filename;
    }
}
