package testclasses.encryption;

import dev.skidfuscator.annotations.NativeObfuscation;

import javax.crypto.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class AesCipther {
    static SecretKey key;

    static {
        try {
            key = getSecretEncryptionKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static SecretKey getSecretEncryptionKey() throws NoSuchAlgorithmException {
        KeyGenerator aesKeyGenerator = KeyGenerator.getInstance("AES");
        aesKeyGenerator.init(128); // The AES key size in number of bits
        return aesKeyGenerator.generateKey();
    }

    @NativeObfuscation
    public static String doThing(String input) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] bytePlainText = aesCipher.doFinal(input.getBytes(StandardCharsets.UTF_8));

        Cipher aesCipher2 = Cipher.getInstance("AES");
        aesCipher2.init(Cipher.DECRYPT_MODE, key);

        byte[] decrypted = aesCipher2.doFinal(bytePlainText);
        assert input.equals(new String(decrypted)) : "Failed decrypted ? " + input + " to " + decrypted;

        System.out.println("Successfully decrypted " + input);
        return new String(decrypted);
    }
}
