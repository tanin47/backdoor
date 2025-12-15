package tanin.backdoor.web;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordHasher {

  private static final String ALGORITHM = "PBKDF2WithHmacSHA512";
  private static final int ITERATIONS = 120000;
  private static final int KEY_LENGTH = 512;
  private static final int SALT_LENGTH_BYTES = 16;

  public static byte[] generateSalt() {
    SecureRandom random = new SecureRandom();
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    random.nextBytes(salt);
    return salt;
  }

  public static String generateHash(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
    byte[] salt = generateSalt();
    return generateHash(password, salt);
  }

  public static String generateHash(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
    SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
    byte[] hash = factory.generateSecret(spec).getEncoded();
    return Base64.getEncoder().encodeToString(salt) + ":" +
      Base64.getEncoder().encodeToString(hash);
  }


  public static boolean verifyPassword(String rawPassword, String storedHashAndSalt) throws NoSuchAlgorithmException, InvalidKeySpecException {
    String[] parts = storedHashAndSalt.split(":");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Stored hash format is invalid.");
    }
    byte[] salt = Base64.getDecoder().decode(parts[0]);
    String storedHash = parts[1];

    String potentialNewHash = generateHash(rawPassword, salt).split(":")[1];

    return java.security.MessageDigest.isEqual(Base64.getDecoder().decode(storedHash),
      Base64.getDecoder().decode(potentialNewHash));
  }
}
