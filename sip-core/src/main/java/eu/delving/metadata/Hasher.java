/*
 * Copyright 2011 DELVING BV
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

package eu.delving.metadata;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;

/**
 * This class manages all aspects of using an MD5 hash as prefix in the naming of files in the data set,
 * so that files are not uploaded repeatedly.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Hasher {
    private static final String SEPARATOR = "__";
    private static final int BLOCK_SIZE = 4096;
    private MessageDigest messageDigest;

    public static String extractFileName(File file) {
        String fileName = file.getName();
        int hashSeparator = fileName.indexOf(SEPARATOR);
        if (hashSeparator > 0) {
            fileName = fileName.substring(hashSeparator + 2);
        }
        return fileName;
    }

    public static String extractHash(File file) {
        return extractHashFromFileName(file.getName());
    }

    public static String extractHashFromFileName(String fileName) {
        int hashSeparator = fileName.indexOf(SEPARATOR);
        if (hashSeparator > 0) {
            return fileName.substring(0, hashSeparator);
        }
        else {
            return null;
        }
    }

    public static File ensureFileHashed(File file) throws IOException {
        if (file.getName().contains(SEPARATOR)) {
            return file;
        }
        else {
            Hasher hasher = new Hasher();
            hasher.update(file);
            File hashedFile = new File(file.getParentFile(), hasher.prefixFileName(file.getName()));
            if (hashedFile.exists()) {
                FileUtils.deleteQuietly(file);
            }
            else {
                FileUtils.moveFile(file, hashedFile);
            }
            return hashedFile;
        }
    }

    public static boolean checkHash(File file) throws IOException {
        String hash = extractHash(file);
        Hasher hasher = new Hasher();
        hasher.update(file);
        return hash.equals(hasher.getHashString());
    }

    public Hasher() {
        try {
            this.messageDigest = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available??");
        }
    }

    public DigestOutputStream createDigestOutputStream(OutputStream outputStream) {
        return new DigestOutputStream(outputStream, messageDigest);
    }

    public void update(byte[] buffer, int bytes) {
        messageDigest.update(buffer, 0, bytes);
    }

    public void update(File inputFile) throws IOException {
        try {
            InputStream inputStream = new FileInputStream(inputFile);
            if (inputFile.getName().endsWith(".gz")) {
                inputStream = new GZIPInputStream(inputStream);
            }
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            while (-1 != (bytesRead = inputStream.read(buffer))) {
                update(buffer, bytesRead);
            }
            inputStream.close();
        }
        catch (Exception e) {
            throw new IOException("Unable to get hash of " + inputFile.getAbsolutePath(), e);
        }
    }

    public byte[] getHash(String value) {
        try {
            return messageDigest.digest(value.getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getHashString(String value) {
        try {
            return toHexadecimal(messageDigest.digest(value.getBytes("UTF-8")));
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getHashString() {
        return toHexadecimal(messageDigest.digest());
    }

    public String prefixFileName(String fileName) {
        return getHashString() + SEPARATOR + fileName;
    }

    static final String HEXES = "0123456789ABCDEF";

    private static String toHexadecimal(byte[] raw) {
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

}
