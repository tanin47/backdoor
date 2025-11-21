package tanin.backdoor.desktop;

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

// Generating a self-signed certificate is critical to preventing a MITM attack.
public record SelfSignedCertificate(X509Certificate cert, PrivateKey privateKey) {

  public static File generateKeyStoreFile(SelfSignedCertificate cert, String keyStorePassword) throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
    var pass = keyStorePassword.toCharArray();
    var params = new KeyStore.PasswordProtection(pass);
    var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, pass);
    keyStore.setEntry(
      "localhost",
      new KeyStore.PrivateKeyEntry(cert.privateKey, new X509Certificate[]{cert.cert}),
      params
    );
    var file = File.createTempFile("keystore", ".jks");
    try (var fos = new FileOutputStream(file)) {
      keyStore.store(fos, pass);
    }
    return file;
  }

  public static SelfSignedCertificate generate(String host) throws CertificateException, NoSuchAlgorithmException, IOException, SignatureException, InvalidKeyException, NoSuchProviderException {
    var cakg = new CertAndKeyGen("RSA", "MD5WithRSA");
    cakg.generate(1024);

    var name = new X500Name("CN=" + host);

    java.security.cert.X509Certificate certificate = cakg.getSelfCertificate(name, 2000000);
    certificate.checkValidity();

    return new SelfSignedCertificate(certificate, cakg.getPrivateKey());
  }

  public static String getSHA256Fingerprint(byte[] bs) throws NoSuchAlgorithmException, CertificateEncodingException {
    // Get an instance of the SHA-256 MessageDigest
    MessageDigest md = MessageDigest.getInstance("SHA-256");

    // Calculate the hash of the certificate's encoded form
    byte[] fingerprintBytes = md.digest(bs);

    // Convert the byte array to a hexadecimal string
    StringBuilder sb = new StringBuilder();
    for (byte b : fingerprintBytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  public static String generateRandomString(int length) {
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
