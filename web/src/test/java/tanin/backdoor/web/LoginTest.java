package tanin.backdoor.web;

import org.junit.jupiter.api.Test;
import tanin.backdoor.core.DatabaseConfig;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
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
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    fillCodeMirror("select current_user");
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
  void usePassthroughUser() throws InterruptedException, SQLException, NoSuchAlgorithmException, KeyManagementException {
    server.databaseConfigs = new DatabaseConfig[]{
      new DatabaseConfig("postgres", POSTGRES_DATABASE_URL, null, null),
      new DatabaseConfig("clickhouse", CLICKHOUSE_DATABASE_URL, null, null),
    };
    server.sourceCodeUsers = new SourceCodeUser[0];

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

    click(tid("database-item", "postgres"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item", "postgres")).getDomAttribute("data-database-status")));
    assertEquals(
      List.of("backdoor_user", "migrate_db_already_migrated_script", "migrate_db_lock", "user"),
      elems(tid("menu-items", "postgres", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );

    assertEquals("unloaded", elem(tid("database-item", "clickhouse")).getDomAttribute("data-database-status"));
    click(tid("database-item", "clickhouse"));
    waitUntil(() -> assertTrue(hasElem(tid("additional-login-modal"))));
  }

  @Test
  void forbidPostgresUserWhenDatabaseIsAlreadyCredentialed() throws InterruptedException, SQLException, NoSuchAlgorithmException, KeyManagementException {
    server.databaseConfigs = new DatabaseConfig[]{
      new DatabaseConfig("postgres", POSTGRES_DATABASE_URL, "backdoor_test_user", "test"),
      new DatabaseConfig("clickhouse", CLICKHOUSE_DATABASE_URL, null, null),
    };
    server.sourceCodeUsers = new SourceCodeUser[0];

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

    click(tid("database-item", "postgres"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item", "postgres")).getDomAttribute("data-database-status")));
    assertEquals(
      List.of("backdoor_user", "migrate_db_already_migrated_script", "migrate_db_lock", "user"),
      elems(tid("menu-items", "postgres", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
    click(tid("database-item", "clickhouse"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item", "clickhouse")).getDomAttribute("data-database-status")));
    assertEquals(
      List.of("project_setting"),
      elems(tid("menu-items", "clickhouse", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void forbidClickHouseUserWhenDatabaseIsAlreadyCredentialed() throws InterruptedException, SQLException, NoSuchAlgorithmException, KeyManagementException {
    server.databaseConfigs = new DatabaseConfig[]{
      new DatabaseConfig("postgres", POSTGRES_DATABASE_URL, null, null),
      new DatabaseConfig("clickhouse", CLICKHOUSE_DATABASE_URL, "backdoor", "test_ch"),
    };
    server.sourceCodeUsers = new SourceCodeUser[0];

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

    click(tid("database-item", "postgres"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item", "postgres")).getDomAttribute("data-database-status")));
    assertEquals(
      List.of("backdoor_user", "migrate_db_already_migrated_script", "migrate_db_lock", "user"),
      elems(tid("menu-items", "postgres", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
    click(tid("database-item", "clickhouse"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item", "clickhouse")).getDomAttribute("data-database-status")));
    assertEquals(
      List.of("project_setting"),
      elems(tid("menu-items", "clickhouse", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void incorrectPassthroughUser() throws InterruptedException, SQLException, NoSuchAlgorithmException, KeyManagementException {
    server.databaseConfigs = new DatabaseConfig[]{new DatabaseConfig("test_db", POSTGRES_DATABASE_URL, null, null)};
    server.sourceCodeUsers = new SourceCodeUser[0];

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
