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

import com.mysql.jdbc.StringUtils;
import org.junit.Test;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestEncryption {
    private static final String PLAINTEXT = "Here is something that we need to encrypt so that it cannot be read";
    public static final String AES = "AES";

    @Test
    public void encryptDecrypt() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES);
        keyGen.init(128);
        SecretKey sk = keyGen.generateKey();
        byte[] key = sk.getEncoded();
        System.out.println(StringUtils.dumpAsHex(key, key.length));
        SecretKeySpec sks = new SecretKeySpec(sk.getEncoded(), AES);
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.ENCRYPT_MODE, sks, cipher.getParameters());
        byte[] encrypted = cipher.doFinal(PLAINTEXT.getBytes());
        System.out.println(StringUtils.dumpAsHex(encrypted, encrypted.length));
        cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.DECRYPT_MODE, sks, cipher.getParameters());
        byte[] decrypted = cipher.doFinal(encrypted);
        System.out.println(new String(decrypted));
    }
}
