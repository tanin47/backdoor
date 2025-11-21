package tanin.backdoor.desktop;

import tanin.javaelectron.nativeinterface.MacOsApi;

import static tanin.javaelectron.nativeinterface.WebviewNative.N;

public class Browser {

  String url;

  public Browser(String url) {
    this.url = url;
  }

  public void run() throws InterruptedException {
    MacOsApi.N.setupMenu();

    var pointer = N.webview_create(true, null);
    N.webview_navigate(pointer, this.url);

    N.webview_run(pointer);
    N.webview_destroy(pointer);
    N.webview_terminate(pointer);
  }
}
