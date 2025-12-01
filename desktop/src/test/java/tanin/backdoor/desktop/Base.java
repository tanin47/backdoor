package tanin.backdoor.desktop;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.support.ui.Select;
import tanin.backdoor.core.DatabaseConfig;
import tanin.ejwf.MinumBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;
import static tanin.backdoor.core.BackdoorCoreServer.makeSqlName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Base {

  static File sqliteFile;

  static {
    try {
      sqliteFile = File.createTempFile("test", ".sqlite");
      sqliteFile.deleteOnExit();
      var _ignored = sqliteFile.delete();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static String SQLITE_DATABASE_URL = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();

  public DatabaseConfig sqliteConfig = new DatabaseConfig("sqlite", SQLITE_DATABASE_URL, null, null);
  static int PORT = 9091;
  static String TEST_AUTH_KEY = "test-auth-key";
  static boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

  public WebDriver webDriver;
  public BackdoorDesktopServer server;

  @BeforeAll
  void setUpAll() throws SQLException, URISyntaxException, InterruptedException {
    initializeWebDriver();
  }

  public void initializeWebDriver() {
    if (webDriver != null) {
      webDriver.close();
    }
    var options = new ChromeOptions();

    if (System.getenv("HEADLESS") != null) {
      options.addArguments("--headless");
    }
    options.addArguments(
      "--guest",
      "--disable-extensions",
      "--disable-web-security",
      "--window-size=1280,800",
      "--disable-dev-shm-usage",
      "--disable-smooth-scrolling",
      "--ignore-certificate-errors"
    );

    var logPrefs = new LoggingPreferences();
    logPrefs.enable(LogType.BROWSER, Level.ALL);
    options.setCapability("goog:loggingPrefs", logPrefs);

    webDriver = new ChromeDriver(options);
  }

  void clearPreferences() {
    try {
      java.util.prefs.Preferences.userNodeForPackage(BackdoorDesktopServer.Mode.Test.getClass()).removeNode();
    } catch (Exception ignored) {
    }
  }

  void resetDatabase() throws Exception {
    SqlHistoryManager.resetForTesting();
    clearPreferences();

    try (var sqlite = server.engineProvider.createEngine(sqliteConfig, null)) {
      var conn = sqlite.connection;
      List<String> tableNames = new ArrayList<>();
      var rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
      while (rs.next()) {
        tableNames.add(rs.getString("TABLE_NAME"));
      }
      for (String tableName : tableNames) {
        conn.createStatement().execute("DROP TABLE IF EXISTS " + makeSqlName(tableName));
      }

      conn.createStatement().execute(
        """
          CREATE TABLE "user" (
            id INT PRIMARY KEY,
            username VARCHAR(255) NOT NULL UNIQUE,
            password VARCHAR(255) NOT NULL
          )
          """
      );

      for (int i = 1; i <= 4; i++) {
        conn.createStatement().execute(String.format(
            """
                INSERT INTO "user" (
                  id,
                  username,
                  password
                ) VALUES (
                  '%1$d',
                  'test_user_%1$d',
                  'password%1$d'
                )
              """,
            i
          )
        );
      }
    }


  }

  @BeforeEach
  void setUp() throws Exception {
    var cert = SelfSignedCertificate.generate("localhost");
    var keyStorePassword = SelfSignedCertificate.generateRandomString(64);
    var keyStoreFile = SelfSignedCertificate.generateKeyStoreFile(cert, keyStorePassword);
    server = new BackdoorDesktopServer(
      new DatabaseConfig[]{
        new DatabaseConfig(sqliteConfig.nickname, sqliteConfig.jdbcUrl, sqliteConfig.username, sqliteConfig.password),
      },
      PORT,
      TEST_AUTH_KEY,
      new MinumBuilder.KeyStore(keyStoreFile, keyStorePassword),
      BackdoorDesktopServer.Mode.Test,
      js -> {
      }
    );
    resetDatabase();
    server.start();

    go("/");
    webDriver.manage().deleteAllCookies();
    webDriver.manage().addCookie(new Cookie(
      BackdoorDesktopServer.AUTH_KEY_COOKIE_KEY,
      TEST_AUTH_KEY
    ));
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @AfterAll
  void tearDownAll() {
    webDriver.close();
  }

  public void go(String path) {
    webDriver.get("https://localhost:" + PORT + path);
  }

  String getCurrentPath() {
    return webDriver.getCurrentUrl().substring(("https://localhost:" + PORT).length());
  }

  public String tid(String... args) {
    var beginning = true;
    var sb = new StringBuilder();

    for (int i = 0; i < args.length; i++) {
      if (beginning) {
        sb.append("[data-test-id=")
          .append(args[i])
          .append("]");
        beginning = false;
      } else if (args[i] == null) {
        sb.append(" ");
        beginning = true;
      } else {
        sb.append("[data-test-value=")
          .append(args[i])
          .append("]");
      }
    }

    return sb.toString();
  }

  public WebElement elem(String cssSelector) throws InterruptedException {
    return elem(cssSelector, true);
  }

  WebElement elem(String cssSelector, boolean checkDisplay) throws InterruptedException {
    try {
      if (checkDisplay) {
        waitUntil(() -> assertTrue(elems(cssSelector).stream().anyMatch(WebElement::isDisplayed)));
        return elems(cssSelector).stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
      } else {
        return elems(cssSelector).getFirst();
      }
    } catch (StaleElementReferenceException e) {
      Thread.sleep(100);
      return elem(cssSelector, checkDisplay);
    }
  }

  public boolean hasElem(String cssSelector) throws InterruptedException {
    return webDriver.findElements(new By.ByCssSelector(cssSelector)).stream().anyMatch(WebElement::isDisplayed);
  }

  public List<WebElement> elems(String cssSelector) throws InterruptedException {
    waitUntil(() -> assertFalse(webDriver.findElements(new By.ByCssSelector(cssSelector)).isEmpty()));
    return webDriver.findElements(new By.ByCssSelector(cssSelector));
  }

  void retryInteraction(VoidFn fn) throws InterruptedException {
    for (int retry = 0; retry < 2; retry++) {
      try {
        fn.invoke();
        return;
      } catch (ElementNotInteractableException | NullPointerException ex) {
        Thread.sleep(1000);
      }
    }

    // Last try
    fn.invoke();
  }

  public void click(String cssSelector) throws InterruptedException {
    retryInteraction(() -> elem(cssSelector).click());
  }

  public void select(String cssSelector, String label) throws InterruptedException {
    retryInteraction(() -> {
      var select = new Select(elem(cssSelector));
      select.selectByVisibleText(label);
    });
  }

  public void hover(String cssSelector) throws InterruptedException {
    var actions = new Actions(webDriver);
    actions.moveToElement(elem(cssSelector), 2, 2).perform();
  }

  public void fill(String cssSelector, String text) throws InterruptedException {
    retryInteraction(() -> {
      var el = elem(cssSelector);

      el.sendKeys(IS_MAC ? Keys.COMMAND : Keys.CONTROL, "a");
      el.sendKeys(Keys.BACK_SPACE);

      Thread.sleep(10);
      el.sendKeys(text);
    });
  }

  public void sendClearKeys() throws InterruptedException {
    var actions = new Actions(webDriver);
    var modifierKey = IS_MAC ? Keys.COMMAND : Keys.CONTROL;
    actions
      .keyDown(modifierKey)
      .sendKeys("a")
      .keyUp(modifierKey)
      .sendKeys(Keys.BACK_SPACE)
      .perform();
  }

  public void sendKeys(String text) {
    var actions = new Actions(webDriver);
    actions.sendKeys(text).perform();
  }

  @FunctionalInterface
  public interface InterruptibleSupplier {
    boolean get() throws InterruptedException;
  }

  @FunctionalInterface
  public interface VoidFn {
    void invoke() throws InterruptedException;
  }

  public void waitUntil(VoidFn fn) throws InterruptedException {
    waitUntil(5000, fn);
  }

  public void waitUntil(long waitUntilTimeoutInMillis, VoidFn fn) throws InterruptedException {
    InterruptibleSupplier newFn = () -> {
      try {
        fn.invoke();
        return true;
      } catch (AssertionError | StaleElementReferenceException | java.util.NoSuchElementException |
               NoSuchElementException e) {
        return false;
      }
    };

    var startTime = System.currentTimeMillis();
    while ((System.currentTimeMillis() - startTime) < waitUntilTimeoutInMillis) {
      if (newFn.get()) return;
      Thread.sleep(500);
    }

    fn.invoke();
  }

  public void assertContains(String actual, String expectedPart) {
    assertTrue(actual.contains(expectedPart), () -> actual + " doesn't contain " + expectedPart);
  }

  public void assertColumnValues(
    String columnName,
    String... expectedValues
  ) throws InterruptedException {
    waitUntil(() -> {
      assertIterableEquals(
        elems(tid("sheet-column-value", columnName)).stream().map(s -> s.getText().trim()).toList(),
        Arrays.stream(expectedValues).toList()
      );
    });
  }


  public void assertCell(
    int rowIndex,
    String columnName,
    String expectedValue
  ) throws InterruptedException {
    assertEquals(
      expectedValue,
      elems(tid("sheet-column-value", columnName)).get(rowIndex).getText().trim()
    );
  }

  public void assertSheetViewContent(String expectedValue) throws InterruptedException {
    waitUntil(() -> {
      assertEquals(expectedValue, elem(tid("sheet-view-content")).getText());
    });
  }

  void checkErrorPanel(String... errors) throws InterruptedException {
    checkErrorPanel(5000, errors);
  }

  void checkErrorPanel(long waitTimeoutInMillis, String... errors) throws InterruptedException {
    waitUntil(
      waitTimeoutInMillis,
      () -> {
        var actualErrors = elems(tid("error-panel") + " p").stream().map(p -> p.getText().trim());
        assertArrayEquals(errors, actualErrors.toArray());
      }
    );
  }

  public void fillCodeMirror(String sql) throws InterruptedException {
    waitUntil(() -> {
      click(".CodeMirror-code");

      var isFocused = ((JavascriptExecutor) webDriver).executeScript("return window.CODE_MIRROR_FOR_TESTING.hasFocus();");
      if (isFocused instanceof Boolean && !((Boolean) isFocused)) {
        Thread.sleep(1000);
      }
      assertEquals(true, isFocused);
    });

    sendClearKeys();
    sendKeys(sql);
  }
}
