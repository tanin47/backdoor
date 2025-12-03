package tanin.backdoor.desktop.nativeinterface;

import java.io.File;
import java.util.logging.Logger;

public class Base {
  public enum OperatingSystem {
    MAC,
    WINDOWS,
    LINUX
  }

  public static final OperatingSystem CURRENT_OS = determineOS();

  private static OperatingSystem determineOS() {
    var _ignored = nativeDir;
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("mac")) return OperatingSystem.MAC;
    if (os.contains("win")) return OperatingSystem.WINDOWS;
    return OperatingSystem.LINUX;
  }

  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  public static final File nativeDir;

  static {
    boolean packaged = System.getProperty("backdoor.packaged") != null;

    try {
      if (packaged) {
        nativeDir = new File(System.getProperty("java.library.path"));
      } else {
        nativeDir = new File("src/main/resources/native");
      }

      System.setProperty("jna.debug_load.jna", "true");
      System.setProperty("jna.nosys", "true");
      System.setProperty("jna.library.path", nativeDir.getAbsolutePath());
      System.setProperty("java.library.path", nativeDir.getAbsolutePath());

      if (packaged) {
        logger.info("Run in a packaged environment.");
        System.setProperty("jna.nounpack", "true");
        System.setProperty("jna.noclasspath", "true");
        System.setProperty("jna.boot.library.path", nativeDir.getAbsolutePath());

        System.setProperty("org.sqlite.lib.path", nativeDir.getAbsolutePath());
      }

      logger.info("Set the native library path to: " + nativeDir.getAbsolutePath());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
