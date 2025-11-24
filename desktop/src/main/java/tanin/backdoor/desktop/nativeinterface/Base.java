package tanin.backdoor.desktop.nativeinterface;

import com.sun.jna.Library;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class Base {
  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  static final File nativeDir = setUpNativeDir();

  static File setUpNativeDir() {
    boolean sandboxed = System.getenv("APP_SANDBOX_CONTAINER_ID") != null;


    try {
      File nativeDir;
      if (sandboxed) {
        Path appPath = Paths.get(Base.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        while (appPath != null && !appPath.toString().endsWith(".app")) {
          appPath = appPath.getParent();
        }

        assert appPath != null;
        nativeDir = appPath.resolve("Contents/app/resources").toFile();
      } else {
        nativeDir = new File("src/main/resources/native");
      }

      System.setProperty("jna.debug_load.jna", "true");
      System.setProperty("jna.nosys", "true");
      System.setProperty("jna.library.path", nativeDir.getAbsolutePath());

      if (sandboxed) {
        System.setProperty("jna.nounpack", "true");
        System.setProperty("jna.noclasspath", "true");
        System.setProperty("jna.boot.library.path", nativeDir.getAbsolutePath());
      }

      logger.info("User path: " + System.getProperty("user.dir"));
      logger.info("Set the native library path to: " + nativeDir.getAbsolutePath());

      return nativeDir;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
