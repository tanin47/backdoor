package tanin.javaelectron.nativeinterface;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.util.Collections;
import java.util.logging.Logger;

public interface MacOsApi extends tanin.javaelectron.nativeinterface.Base {
  static final Logger logger = Logger.getLogger(tanin.javaelectron.nativeinterface.WebviewNative.class.getName());
  static final MacOsApi N = runSetup();

  private static MacOsApi runSetup() {
    tanin.javaelectron.nativeinterface.Base.prepareLib("/libMacOsApi.dylib");

    return Native.load(
      "MacOsApi",
      MacOsApi.class,
      Collections.singletonMap(Library.OPTION_STRING_ENCODING, "UTF-8")
    );
  }

  void setupMenu();
}
