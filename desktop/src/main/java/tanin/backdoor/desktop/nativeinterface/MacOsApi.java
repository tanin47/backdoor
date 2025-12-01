package tanin.backdoor.desktop.nativeinterface;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;

import java.util.Collections;
import java.util.logging.Logger;

public interface MacOsApi extends Library {
  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  static final MacOsApi N = runSetup();

  private static MacOsApi runSetup() {
    logger.info("Load MacOsApi library");
    var _ignored = Base.nativeDir;
    return Native.load(
      "MacOsApi",
      MacOsApi.class,
      Collections.singletonMap(Library.OPTION_STRING_ENCODING, "UTF-8")
    );
  }

  void setupMenu();

  void nsWindowMakeKeyAndOrderFront();

  public static interface OnFileSelected extends Callback {
    public void invoke(String filePath);
  }

  void openFile(OnFileSelected onFileSelected);

  void saveFile(OnFileSelected onFileSelected);

  boolean startAccessingSecurityScopedResource(String path);

  void stopAccessingSecurityScopedResource(String path);
}
