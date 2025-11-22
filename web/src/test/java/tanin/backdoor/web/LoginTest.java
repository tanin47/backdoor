package tanin.backdoor.web;

import org.junit.jupiter.api.Test;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoginTest extends Base {
  LoginTest() {
    shouldLoggedIn = false;
  }

  @Test
  void didNotClickCaptcha() throws InterruptedException {
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor");
    fill(tid("password"), "test");
    click(tid("submit-button"));

    checkErrorPanel("The captcha is invalid. Please check \"I'm not a robot\" again.");
    assertEquals("false", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
  }

  @Test
  void useCustomUserAndLogout() throws InterruptedException {
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("/", getCurrentPath()));

    click(".CodeMirror-code");
    sendKeys("select current_user");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));

    assertColumnValues("current_user", "backdoor_test_user");

    click(tid("logout-button"));
    waitUntil(() -> assertEquals("/login", getCurrentPath()));
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));
  }

  @Test
  void incorrectCustomUser() throws InterruptedException {
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor");
    fill(tid("password"), "test123");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));
    checkErrorPanel("The username or password is invalid.");
    assertEquals("false", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
  }

  @Test
  void usePassthroughUser() throws InterruptedException, SQLException {
    server.stop();

    server = new BackdoorWebServer(
      new DatabaseConfig[]{
        new DatabaseConfig("postgres", POSTGRES_DATABASE_URL, null, null),
        new DatabaseConfig("clickhouse", CLICKHOUSE_DATABASE_URL, null, null),

      },
      PORT,
      0,
      new User[0],
      "dontcare"
    );
    server.start();

    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor_test_user");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("/", getCurrentPath()));

    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "postgres", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );

    assertTrue(hasElem(tid("database-lock-item", "clickhouse")));
  }

  @Test
  void forbidPostgresUserWhenDatabaseIsAlreadyCredentialed() throws InterruptedException, SQLException {
    server.stop();

    server = new BackdoorWebServer(
      new DatabaseConfig[]{
        new DatabaseConfig("postgres", POSTGRES_DATABASE_URL, "backdoor_test_user", "test"),
        new DatabaseConfig("clickhouse", CLICKHOUSE_DATABASE_URL, null, null),

      },
      PORT,
      0,
      new User[0],
      "dontcare"
    );
    server.start();

    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    // Postgres user doesn't work because the databasee config is already credentialed.
    fill(tid("username"), "backdoor_test_user");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));
    checkErrorPanel("The username or password is invalid.");

    // ClickHouse user works.
    fill(tid("username"), "backdoor");
    fill(tid("password"), "test_ch");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("/", getCurrentPath()));

    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "postgres", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
    assertEquals(
      List.of("project_setting"),
      elems(tid("menu-items", "clickhouse", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void forbidClickHouseUserWhenDatabaseIsAlreadyCredentialed() throws InterruptedException, SQLException {
    server.stop();

    server = new BackdoorWebServer(
      new DatabaseConfig[]{
        new DatabaseConfig("postgres", POSTGRES_DATABASE_URL, null, null),
        new DatabaseConfig("clickhouse", CLICKHOUSE_DATABASE_URL, "backdoor", "test_ch"),

      },
      PORT,
      0,
      new User[0],
      "dontcare"
    );
    server.start();

    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    // Postgres user doesn't work because the databasee config is already credentialed.
    fill(tid("username"), "backdoor");
    fill(tid("password"), "test_ch");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));
    checkErrorPanel("The username or password is invalid.");

    // ClickHouse user works.
    fill(tid("username"), "backdoor_test_user");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("/", getCurrentPath()));

    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "postgres", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
    assertEquals(
      List.of("project_setting"),
      elems(tid("menu-items", "clickhouse", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void incorrectPassthroughUser() throws InterruptedException, SQLException {
    server.stop();

    server = new BackdoorWebServer(
      new DatabaseConfig[]{new DatabaseConfig("test_db", POSTGRES_DATABASE_URL, null, null)},
      PORT,
      0,
      new User[0],
      "dontcare"
    );
    server.start();

    go("/");

    fill(tid("username"), "random_user");
    fill(tid("password"), "test123");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));
    checkErrorPanel("The username or password is invalid.");
    assertEquals("false", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
  }
}
