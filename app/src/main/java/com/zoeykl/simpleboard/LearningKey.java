package com.zoeykl.simpleboard;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Random SQLCipher key wrapped by a non-exportable, per-install Android Keystore key. */
final class LearningKey {
    static synchronized byte[] load(Context context,String databaseName) {
        System.loadLibrary("sqlcipher");
        byte[] raw=null;
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(databaseName.getBytes(StandardCharsets.UTF_8));
            StringBuilder id=new StringBuilder();for(byte b:digest)id.append(String.format(java.util.Locale.US,"%02x",b&255));
            String alias="simpleboard.learning."+id;
            AtomicFile wrapped=new AtomicFile(new File(context.getNoBackupFilesDir(),"learning-"+id+".key"));
            byte[] aad=(context.getPackageName()+":"+databaseName+":v1").getBytes(StandardCharsets.UTF_8);
            KeyStore keystore=KeyStore.getInstance("AndroidKeyStore");keystore.load(null);
            // AtomicFile also recovers a completed backup after an interrupted write.
            boolean exists=wrapped.getBaseFile().exists()||new File(wrapped.getBaseFile()+".bak").exists();
            if(exists) {
                if(!keystore.containsAlias(alias))throw new IllegalStateException("Learning encryption key is unavailable");
                byte[] blob=wrapped.readFully();
                if(blob.length!=1+12+32+16||blob[0]!=1)throw new IllegalStateException("Invalid learning key file");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,(SecretKey)keystore.getKey(alias,null),new GCMParameterSpec(128,Arrays.copyOfRange(blob,1,13)));
                cipher.updateAAD(aad);
                return cipher.doFinal(blob,13,blob.length-13);
            }
            if(context.getDatabasePath(databaseName).exists())throw new IllegalStateException("Encrypted learning database exists without its key");
            if(!keystore.containsAlias(alias)) {
                KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build());
                generator.generateKey();
            }
            raw=new byte[32];new SecureRandom().nextBytes(raw);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,(SecretKey)keystore.getKey(alias,null));cipher.updateAAD(aad);
            byte[] ciphertext=cipher.doFinal(raw);
            byte[] blob=ByteBuffer.allocate(1+12+ciphertext.length).put((byte)1).put(cipher.getIV()).put(ciphertext).array();
            FileOutputStream stream=null;
            try {stream=wrapped.startWrite();stream.write(blob);wrapped.finishWrite(stream);}
            catch(Exception e){wrapped.failWrite(stream);throw e;}
            return raw;
        } catch(Exception e) {
            if(raw!=null)Arrays.fill(raw,(byte)0);
            // Never replace a missing key or silently fall back to plaintext storage.
            throw new IllegalStateException("Cannot unlock local learning. Existing data has been preserved.",e);
        }
    }
    private LearningKey(){}
}
