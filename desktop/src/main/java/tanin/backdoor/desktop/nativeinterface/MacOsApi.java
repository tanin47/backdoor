package tanin.backdoor.desktop.nativeinterface;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.util.Collections;
import java.util.logging.Logger;

public interface MacOsApi extends Library {
  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  static final MacOsApi N = runSetup();

  private static MacOsApi runSetup() {
    Base.setUpNativeDir();
    return Native.load(
      "MacOsApi",
      MacOsApi.class,
      Collections.singletonMap(Library.OPTION_STRING_ENCODING, "UTF-8")
    );
  }

  void setupMenu();

  void nsWindowMakeKeyAndOrderFront();
}
