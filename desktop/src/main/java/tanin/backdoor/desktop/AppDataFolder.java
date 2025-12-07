package tanin.backdoor.desktop;

import java.io.File;

public class AppDataFolder {
  public static File getAppDataFolder() throws Exception {
    String os = System.getProperty("os.name").toLowerCase();

    if (os.contains("win")) { // Windows
      return new File(System.getenv("LOCALAPPDATA"));
    } else if (os.contains("mac")) { // macOS
      // On Mac, the app is running on the app-specific path already. This is imposed by the sandbox.
      return new File(".");
    } else { // Linux and other Unix-like systems
      throw new Exception("Unsupported OS: " + os);
    }
  }
}
