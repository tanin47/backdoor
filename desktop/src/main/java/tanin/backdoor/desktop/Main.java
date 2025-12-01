package tanin.backdoor.desktop;

import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.desktop.nativeinterface.Base;
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

    // Setting up the native lib path must be the first thing we do.
    logger.info("Native lib path: " + Base.nativeDir.getAbsolutePath());
  }

  private MinumBuilder minumBuilder;
  private static Browser browser;

  public static void main(String[] args) throws Exception {
    var cert = SelfSignedCertificate.generate("localhost");
    logger.info("The SSL cert is randomly generated on each run:");
    logger.info("  Certificate SHA-256 Fingerprint: " + SelfSignedCertificate.getSHA256Fingerprint(cert.cert().getEncoded()));

    var keyStorePassword = SelfSignedCertificate.generateRandomString(64);
    var keyStoreFile = SelfSignedCertificate.generateKeyStoreFile(cert, keyStorePassword);

    var authKey = SelfSignedCertificate.generateRandomString(32);
    var server = new BackdoorDesktopServer(
      new DatabaseConfig[0],
      0,
      authKey,
      new MinumBuilder.KeyStore(keyStoreFile, keyStorePassword),
      MinumBuilder.IS_LOCAL_DEV ? BackdoorDesktopServer.Mode.Dev : BackdoorDesktopServer.Mode.Prod,
      js -> browser.eval(js)
    );
    logger.info("Starting...");
    var minum = server.start();

    var sslPort = minum.getSslServer().getPort();
    var url = "https://localhost:" + sslPort + "/?authKey=" + authKey;
    if (MinumBuilder.IS_LOCAL_DEV) {
      logger.info("[dev] You can access the UI in a browser at: " + url);
    }

    browser = new Browser(
      url,
      true
//      MinumBuilder.IS_LOCAL_DEV
    );
    browser.run();

    logger.info("Exiting");
  }
}
