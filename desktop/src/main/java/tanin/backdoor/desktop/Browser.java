package tanin.backdoor.desktop;


import tanin.backdoor.desktop.nativeinterface.MacOsApi;

import java.util.logging.Level;
import java.util.logging.Logger;

import static tanin.backdoor.desktop.nativeinterface.WebviewNative.N;
import static tanin.backdoor.desktop.nativeinterface.WebviewNative.WV_HINT_FIXED;

public class Browser {
  private static final Logger logger = Logger.getLogger(Browser.class.getName());

  String url;
  private long pointer;

  public Browser(String url) {
    this.url = url;
  }

  public void run() throws InterruptedException {
    MacOsApi.N.setupMenu();

    this.pointer = N.webview_create(true, null);
    N.webview_set_size(this.pointer, 1000, 600, WV_HINT_FIXED);
    N.webview_navigate(this.pointer, this.url);

    Runtime.getRuntime().addShutdownHook(new Thread(this::terminate));

    N.webview_run(this.pointer);
    if (this.pointer != 0) {
      N.webview_destroy(this.pointer);
      this.pointer = 0;
    }
  }

  private void terminate() {
    logger.info("Received a shutdown hook. Terminating the webview...");
    try {
      if (this.pointer != 0) {
        N.webview_terminate(this.pointer);
        this.pointer = 0;
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error while terminating webview", e);
    }
  }
}
