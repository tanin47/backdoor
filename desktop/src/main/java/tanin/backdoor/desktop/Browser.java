package tanin.backdoor.desktop;

import sun.misc.Signal;
import tanin.backdoor.desktop.nativeinterface.Base;
import tanin.backdoor.desktop.nativeinterface.MacOsApi;
import tanin.backdoor.desktop.nativeinterface.WindowsApi;

import java.util.logging.Level;
import java.util.logging.Logger;

import static tanin.backdoor.desktop.nativeinterface.WebviewNative.*;

public class Browser {
  private static final Logger logger = Logger.getLogger(Browser.class.getName());

  String url;
  boolean isDebug;
  private long pointer;
  private MacOsApi.OnFileSelected onFileSelected = null;

  public Browser(String url, boolean isDebug) {
    this.url = url;
    this.isDebug = isDebug;
  }

  public void run() throws InterruptedException {
    if (Base.CURRENT_OS == Base.OperatingSystem.MAC) {
      MacOsApi.N.setupMenu();
    }

    this.pointer = N.webview_create(this.isDebug, null);
    N.webview_set_size(this.pointer, 1000, 600, WV_HINT_NONE);
    N.webview_set_title(this.pointer, "Backdoor");
    N.webview_navigate(this.pointer, this.url);

    Signal.handle(new Signal("INT"), sig -> terminate());
    Runtime.getRuntime().addShutdownHook(new Thread(this::terminate));

    if (Base.CURRENT_OS == Base.OperatingSystem.MAC) {
      MacOsApi.N.nsWindowMakeKeyAndOrderFront();
    }
    N.webview_run(this.pointer);
    if (this.pointer != 0) {
      N.webview_destroy(this.pointer);
      this.pointer = 0;
    }
  }

  public void eval(String js) {
    N.webview_dispatch(pointer, ($pointer, arg) -> N.webview_eval(pointer, js), 0);
  }

  public long getWindowPointer() {
    return N.webview_get_window(pointer);
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

  public static interface OnFileSelected {
    void invoke(String path);
  }

  void openFileDialog(boolean isSaved, OnFileSelected fileSelected) {
    if (Base.CURRENT_OS == Base.OperatingSystem.WINDOWS) {
      var thread = new Thread(() -> {
        var pointer = WindowsApi.N.openFileDialog(getWindowPointer(), isSaved);

        if (pointer == null) {
          logger.info("No file has been selected");
        } else {
          try {
            String filePath = pointer.getString(0);
            fileSelected.invoke(filePath);
          } finally {
            WindowsApi.N.freeString(pointer);
          }
        }
      });
      thread.start();
    } else if (Base.CURRENT_OS == Base.OperatingSystem.MAC) {
      onFileSelected = filePath -> {
        System.out.println("Opening file: " + filePath);

        MacOsApi.N.startAccessingSecurityScopedResource(filePath);
        try {
          fileSelected.invoke(filePath);
        } finally {
          MacOsApi.N.stopAccessingSecurityScopedResource(filePath);
        }
        onFileSelected = null;
      };

      if (isSaved) {
        MacOsApi.N.saveFile(onFileSelected);
      } else {
        MacOsApi.N.openFile(onFileSelected);
      }
    } else {
      throw new RuntimeException("Unsupported OS: " + Base.CURRENT_OS);
    }
  }
}
