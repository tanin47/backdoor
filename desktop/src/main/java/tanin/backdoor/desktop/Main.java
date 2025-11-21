package tanin.backdoor.desktop;

import tanin.backdoor.core.DatabaseConfig;
import tanin.ejwf.MinumBuilder;

import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());

  static {
    try (var configFile = Main.class.getResourceAsStream("/logging.properties")) {
      LogManager.getLogManager().readConfiguration(configFile);
      logger.info("The log config (logging.properties) has been loaded.");
    } catch (IOException e) {
      logger.warning("Could not load the log config file (logging.properties): " + e.getMessage());
    }
  }

  public static void main(String[] args) throws Exception {
    var cert = SelfSignedCertificate.generate("localhost");
    logger.info("The SSL cert is randomly generated on each run:");
    logger.info("  Certificate SHA-256 Fingerprint: " + SelfSignedCertificate.getSHA256Fingerprint(cert.cert().getEncoded()));

    var keyStorePassword = SelfSignedCertificate.generateRandomString(64);
    var keyStoreFile = SelfSignedCertificate.generateKeyStoreFile(cert, keyStorePassword);

    var authKey = SelfSignedCertificate.generateRandomString(32);
    logger.info("The auth key is randomly generated on each run: " + authKey);
    var server = new BackdoorDesktopServer(
      new DatabaseConfig[]{
        new DatabaseConfig("postgres", "postgres://127.0.0.1:5432/backdoor_test", null, null),
        new DatabaseConfig("clickhouse", "jdbc:ch://localhost:8123?user=backdoor&password=test_ch", null, null)
      },
      0,
      19999,
      authKey,
      new MinumBuilder.KeyStore(keyStoreFile, keyStorePassword)
    );
    logger.info("Starting...");
    var minum = server.start();

    var browser = new Browser("https://localhost:19999/landing?authKey=" + authKey);
    browser.run();

    logger.info("Blocking...");
    minum.block();
    logger.info("Exiting");
  }
}
