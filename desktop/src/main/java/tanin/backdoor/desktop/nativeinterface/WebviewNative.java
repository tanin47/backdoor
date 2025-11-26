package tanin.backdoor.desktop.nativeinterface;


import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public interface WebviewNative extends Library {
  static final Logger logger = Logger.getLogger(WebviewNative.class.getName());
  static final WebviewNative N = runSetup();

  private static WebviewNative runSetup() {
    logger.info("Load webview library");
    return Native.load(
      "webview",
      WebviewNative.class,
      Collections.singletonMap(Library.OPTION_STRING_ENCODING, "UTF-8")
    );
  }

  static final int WV_HINT_NONE = 0;
  static final int WV_HINT_MIN = 1;
  static final int WV_HINT_MAX = 2;
  static final int WV_HINT_FIXED = 3;


  static interface BindCallback extends Callback {

    void callback(long seq, String req, long arg);

  }


  static interface DispatchCallback extends Callback {

    /**
     * @param $pointer The pointer of the webview
     * @param arg      Unused
     */
    void callback(long $pointer, long arg);

  }

  long webview_create(boolean debug, PointerByReference window);

  /**
   * @param $pointer The instance pointer of the webview
   * @return a native window handle pointer.
   * @implNote This is either a pointer to a GtkWindow, NSWindow, or
   * HWND.
   */
  long webview_get_window(long $pointer);

  /**
   * Load raw HTML content onto the window.
   *
   * @param $pointer The instance pointer of the webview
   * @param html     The raw HTML string.
   */
  void webview_set_html(long $pointer, String html);

  /**
   * Navigates to the given URL.
   *
   * @param $pointer The instance pointer of the webview
   * @param url      The target url, can be a data uri.
   */
  void webview_navigate(long $pointer, String url);

  /**
   * Sets the title of the webview window.
   *
   * @param $pointer The instance pointer of the webview
   * @param title
   */
  void webview_set_title(long $pointer, String title);

  void webview_set_size(long $pointer, int width, int height, int hint);

  /**
   * Runs the main loop until it's terminated. You must destroy the webview after
   * this method returns.
   *
   * @param $pointer The instance pointer of the webview
   */
  void webview_run(long $pointer);

  /**
   * Destroys a webview and closes the native window.
   *
   * @param $pointer The instance pointer of the webview
   */
  void webview_destroy(long $pointer);

  /**
   * Stops the webview loop, which causes {@link #webview_run(long)} to return.
   *
   * @param $pointer The instance pointer of the webview
   */
  void webview_terminate(long $pointer);

  /**
   * Evaluates arbitrary JavaScript code asynchronously.
   *
   * @param $pointer The instance pointer of the webview
   * @param js       The script to execute
   */
  void webview_eval(long $pointer, String js);

  /**
   * Injects JavaScript code at the initialization of the new page.
   *
   * @param $pointer The instance pointer of the webview
   * @param js       The script to execute
   * @implSpec It is guaranteed to be called before window.onload.
   */
  void webview_init(long $pointer, String js);

  /**
   * Binds a native callback so that it will appear under the given name as a
   * global JavaScript function. Internally it uses webview_init().
   *
   * @param $pointer The instance pointer of the webview
   * @param name     The name of the function to be exposed in Javascript
   * @param callback The callback to be called
   * @param arg      Unused
   */
  void webview_bind(long $pointer, String name, BindCallback callback, long arg);

  /**
   * Remove the native callback specified.
   *
   * @param $pointer The instance pointer of the webview
   * @param name     The name of the callback
   */
  void webview_unbind(long $pointer, String name);

  void webview_return(long $pointer, long seq, boolean isError, String result);

  /**
   * Dispatches the callback on the UI thread, only effective while
   * {@link #webview_run(long)} is blocking.
   *
   * @param $pointer The instance pointer of the webview
   * @param callback The callback to be called
   * @param arg      Unused
   */
  void webview_dispatch(long $pointer, DispatchCallback callback, long arg);

  /**
   * Returns the version info.
   */
  VersionInfoStruct webview_version();

  static class VersionInfoStruct extends Structure {
    public int major; // This is technically in a sub-struct.
    public int minor; // This is technically in a sub-struct.
    public int patch; // This is technically in a sub-struct.
    public byte[] version_number = new byte[32];
    public byte[] pre_release = new byte[48];
    public byte[] build_metadata = new byte[48];

    @Override
    protected List<String> getFieldOrder() {
      return Arrays.asList("major", "minor", "patch", "version_number", "pre_release", "build_metadata");
    }
  }

}
