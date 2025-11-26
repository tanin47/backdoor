package tanin.backdoor.desktop.nativeinterface;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class Base {
  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  public static final File nativeDir;

  static {
    boolean sandboxed = System.getenv("APP_SANDBOX_CONTAINER_ID") != null;

    try {
      if (sandboxed) {
        nativeDir = new File(System.getProperty("java.library.path"));
      } else {
        nativeDir = new File("src/main/resources/native");
      }

      System.setProperty("jna.debug_load.jna", "true");
      System.setProperty("jna.nosys", "true");
      System.setProperty("jna.library.path", nativeDir.getAbsolutePath());

      if (sandboxed) {
        logger.info("Run in sandbox.");
        System.setProperty("jna.nounpack", "true");
        System.setProperty("jna.noclasspath", "true");
        System.setProperty("jna.boot.library.path", nativeDir.getAbsolutePath());
      }

      logger.info("Set the native library path to: " + nativeDir.getAbsolutePath());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
