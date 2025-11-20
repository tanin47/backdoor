package tanin.backdoor.web;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.support.ui.Select;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;
import tanin.backdoor.core.engine.Engine;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Base {
  User loggedInUser = new User("backdoor", "test");
  static String POSTGRES_DATABASE_URL = "postgres://127.0.0.1:5432/backdoor_test";
  static String CLICKHOUSE_DATABASE_URL = "jdbc:ch://127.0.0.1:8123/backdoor_test";

  public DatabaseConfig postgresConfig = new DatabaseConfig("postgres", POSTGRES_DATABASE_URL, "backdoor_test_user", "test");
  public DatabaseConfig clickHouseConfig = new DatabaseConfig("clickhouse", CLICKHOUSE_DATABASE_URL, "backdoor", "test_ch");
  static int PORT = 9091;
  static boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

  public WebDriver webDriver;
  BackdoorWebServer server;
  boolean shouldLoggedIn = true;

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
    options.addArguments("--guest");
    options.addArguments("--disable-extensions");
    options.addArguments("--disable-web-security");
    options.addArguments("--window-size=1280,800");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--disable-smooth-scrolling");

    var logPrefs = new LoggingPreferences();
    logPrefs.enable(LogType.BROWSER, Level.ALL);
    options.setCapability("goog:loggingPrefs", logPrefs);

    webDriver = new ChromeDriver(options);
  }

  void resetDatabase() throws Exception {
    try (var pg = Engine.createEngine(postgresConfig, null)) {
      var conn = pg.connection;
      conn.createStatement().execute("DROP SCHEMA IF EXISTS public CASCADE");
      conn.createStatement().execute("CREATE SCHEMA public");

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

      try (var clickhouse = Engine.createEngine(clickHouseConfig, null)) {
        conn = clickhouse.connection;
        var tables = clickhouse.getTables();
        for (var table : tables) {
          conn.createStatement().execute("DROP TABLE IF EXISTS " + table);
        }

        conn.createStatement().execute(
          """
            CREATE TABLE project_setting
            (
                user_id              String,
                project_id           String,
                item_id              Nullable(String),
                some_value           Int
            )
            ENGINE = ReplacingMergeTree()
            ORDER BY (user_id, project_id, item_id)
            PRIMARY KEY (user_id, project_id, item_id)
            SETTINGS allow_nullable_key = 1;
            """
        );

        for (int i = 1; i <= 4; i++) {
          conn.createStatement().execute(String.format(
              """
                  INSERT INTO "project_setting" (
                    user_id,
                    project_id,
                    item_id,
                    some_value
                  ) VALUES (
                    'user_%1$d',
                    'project_%1$d',
                    'item_%1$d',
                    '%1$d'
                  )
                """,
              i
            )
          );
        }
      }
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    resetDatabase();
    server = new BackdoorWebServerBuilder()
      .addDatabaseConfig(postgresConfig.nickname, postgresConfig.jdbcUrl, postgresConfig.username, postgresConfig.password)
      .addDatabaseConfig(clickHouseConfig.nickname, clickHouseConfig.jdbcUrl, clickHouseConfig.username, clickHouseConfig.password)
      .withPort(PORT)
      .addUser(loggedInUser.username(), loggedInUser.password())
      .withSecretKey("fakesecretkey")
      .build();
    server.start();

    go("/");
    webDriver.manage().deleteAllCookies();
    if (shouldLoggedIn) {
      webDriver.manage().addCookie(new Cookie(
        "backdoor",
        BackdoorWebServer.makeAuthCookieValueForUser(new User[]{loggedInUser}, server.secretKey, Instant.now().plus(1, ChronoUnit.DAYS))
      ));
    }
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
    webDriver.get("http://localhost:" + PORT + path);
  }

  String getCurrentPath() {
    return webDriver.getCurrentUrl().substring(("http://localhost:" + PORT).length());
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
      } catch (ElementNotInteractableException ex) {
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


  int waitUntilTimeoutInMillis = 5000;

  @FunctionalInterface
  public interface InterruptibleSupplier {
    boolean get() throws InterruptedException;
  }

  @FunctionalInterface
  public interface VoidFn {
    void invoke() throws InterruptedException;
  }

  public void waitUntil(VoidFn fn) throws InterruptedException {
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
    var actualErrors = elems(tid("error-panel") + " p").stream().map(p -> p.getText().trim());

    assertArrayEquals(errors, actualErrors.toArray());
  }
}
