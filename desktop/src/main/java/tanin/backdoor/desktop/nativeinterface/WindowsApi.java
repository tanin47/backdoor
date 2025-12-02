package tanin.backdoor.desktop.nativeinterface;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.util.Collections;
import java.util.logging.Logger;

public interface WindowsApi extends Library {
  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  static final WindowsApi N = runSetup();

  private static WindowsApi runSetup() {
    logger.info("Load WindowsApi library");
    var _ignored = Base.nativeDir;
    return Native.load(
      "WindowsApi",
      WindowsApi.class,
      Collections.singletonMap(Library.OPTION_STRING_ENCODING, "UTF-8")
    );
  }

  Pointer openFileDialog(long hwnd, boolean isSaved);
  void freeString(Pointer p);
}
