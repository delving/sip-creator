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

package eu.delving.metadata;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;

/**
 * This class manages all aspects of using an MD5 hash as prefix in the naming of files in the data set,
 * so that files are not uploaded repeatedly.
 *
 *
 */

public class Hasher {
    public static final String SEPARATOR = "__";
    public static final int QUICK_SAMPLE_SIZE = 1024;
    private static final int BLOCK_SIZE = 4096;
    private static final int QUICK_SAMPLES = 8;
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
            if (hashedFile.exists()) FileUtils.deleteQuietly(hashedFile);
            FileUtils.moveFile(file, hashedFile);
            return hashedFile;
        }
    }

    public static File ensureFileNotHashed(File file) throws IOException {
        if (file.getName().contains(SEPARATOR)) {
            int sep = file.getName().indexOf(SEPARATOR);
            String noHash = file.getName().substring(sep + SEPARATOR.length());
            File noHashFile = new File(file.getParentFile(), noHash);
            if (noHashFile.exists()) FileUtils.deleteQuietly(noHashFile);
            FileUtils.moveFile(file, noHashFile);
            return noHashFile;
        }
        else {
            return file;
        }
    }

    public static boolean checkHash(File file) throws IOException {
        String hash = extractHash(file);
        Hasher hasher = new Hasher();
        hasher.update(file);
        return hash.equals(hasher.getHashString());
    }

    public static String quickHash(File file) throws IOException {
        Hasher hasher = new Hasher();
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        byte [] chunk = new byte[QUICK_SAMPLE_SIZE];
        long length = raf.length() - chunk.length;
        long step = length/QUICK_SAMPLES;
        for (int walk=0; walk<QUICK_SAMPLES; walk++) {
            raf.seek(step * walk);
            raf.readFully(chunk);
            hasher.update(chunk, chunk.length);
        }
        raf.close();
        return hasher.getHashString().substring(4, 14);
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
        messageDigest.reset();
        return new DigestOutputStream(outputStream, messageDigest);
    }

    public void update(String string) {
        byte[] bytes = new byte[0];
        try {
            bytes = string.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        update(bytes, bytes.length);
    }

    public void update(byte[] buffer, int bytes) {
        messageDigest.update(buffer, 0, bytes);
    }

    public void update(File inputFile) throws IOException {
        try {
            InputStream inputStream = new BufferedInputStream(new FileInputStream(inputFile));
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
