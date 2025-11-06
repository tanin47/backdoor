package tanin.backdoor;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionHelper {
  private static final String ALGORITHM = "AES";
  private static final String CHARSET = "UTF-8";

  private static byte[] generateKey(String secretKey) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return digest.digest(secretKey.getBytes(CHARSET));
  }

  public static String encryptText(String plainText, String secretKey) throws Exception {
    SecretKeySpec key = new SecretKeySpec(generateKey(secretKey), ALGORITHM);
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    cipher.init(Cipher.ENCRYPT_MODE, key);
    byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  public static String decryptText(String encryptedText, String secretKey) throws Exception {
    SecretKeySpec key = new SecretKeySpec(generateKey(secretKey), ALGORITHM);
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, key);
    byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
    return new String(decryptedBytes, CHARSET);
  }

  static String generateRandomString(int length) {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      int randomIndex = random.nextInt(chars.length());
      sb.append(chars.charAt(randomIndex));
    }
    return sb.toString();
  }
}
