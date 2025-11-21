package tanin.backdoor.desktop;


import tanin.backdoor.desktop.nativeinterface.MacOsApi;

import static tanin.backdoor.desktop.nativeinterface.WebviewNative.N;
import static tanin.backdoor.desktop.nativeinterface.WebviewNative.WV_HINT_FIXED;

public class Browser {

  String url;

  public Browser(String url) {
    this.url = url;
  }

  public void run() throws InterruptedException {
    MacOsApi.N.setupMenu();

    var pointer = N.webview_create(true, null);
    N.webview_set_size(pointer, 1000, 600, WV_HINT_FIXED);
    N.webview_navigate(pointer, this.url);

    N.webview_run(pointer);
    N.webview_destroy(pointer);
    // The below causes a crash when closing the window.
//    N.webview_terminate(pointer);
  }
}
