package tanin.backdoor.desktop.nativeinterface;

import com.sun.jna.Library;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Logger;

public interface Base extends Library {
  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  static final File nativeDir = setUpNativeDir();

  static File setUpNativeDir() {
    var dir = new File("./build/native");
    logger.info("Preparing the native dir: " + dir.getPath());
    if (!dir.exists()) {
      var _ignored = dir.mkdirs();
    }
    System.setProperty("jna.library.path", dir.getPath());

    return dir;
  }

  static void prepareLib(String resourcePath) {
    logger.info("Preparing " + resourcePath);
    File target = nativeDir.toPath().resolve(new File(resourcePath).getName()).toFile();
    if (target.exists()) {
      var _ignored = target.delete();
    }

    try (InputStream in = MacOsApi.class.getResourceAsStream(resourcePath)) {
      assert in != null;
      Files.copy(in, target.toPath());
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("used by another")) {
        logger.warning(target.getAbsolutePath() + " is used by another application. Failed to replace the file. The failure can be ignored.");
      } else {
        logger.severe("Unable to extract: " + resourcePath);
        throw new RuntimeException(e);
      }
    }

    System.load(target.getAbsolutePath()); // Load it. This is so Native will be able to link it.
  }
}
