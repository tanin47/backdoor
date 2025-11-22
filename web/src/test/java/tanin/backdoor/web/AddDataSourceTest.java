package tanin.backdoor.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AddDataSourceTest extends Base {
  @Test
  void addPostgres() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), "postgres://backdoor_test_user:test@127.0.0.1:5432/backdoor_test");
    fill(tid("nickname"), "adhoc-test");
    fill(tid("username"), "");
    fill(tid("password"), "");
    click(tid("submit-button"));

    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    assertEquals(
      List.of("postgres", "clickhouse", "adhoc-test"),
      elems(tid("menu-items")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "adhoc-test", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void addClickHouse() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), "jdbc:ch://127.0.0.1:8123/backdoor_test");
    fill(tid("nickname"), "adhoc-test");
    fill(tid("username"), "backdoor");
    fill(tid("password"), "test_ch");
    click(tid("submit-button"));

    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    assertEquals(
      List.of("postgres", "clickhouse", "adhoc-test"),
      elems(tid("menu-items")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
    assertEquals(
      List.of("project_setting"),
      elems(tid("menu-items", "adhoc-test", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void nicknameAlreadyExists() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), "jdbc:unsupportedrandom://127.0.0.1:999/something");
    fill(tid("nickname"), "postgres");
    fill(tid("username"), "");
    fill(tid("password"), "");
    click(tid("submit-button"));

    checkErrorPanel("The database 'postgres' is already in use. Please pick a different nickname.");
  }

  @Test
  void unsupportedDatabase() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), "jdbc:unsupportedrandom://127.0.0.1:999/something");
    fill(tid("nickname"), "adhoc-test");
    fill(tid("username"), "");
    fill(tid("password"), "");
    click(tid("submit-button"));

    checkErrorPanel("jdbc:unsupportedrandom://127.0.0.1:999/something is not supported. Please make your feature request at https://github.com/tanin47/backdoor.");
  }

  @Test
  void invalidPostgresCredentials() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), "postgres://backdoor_test_user:test123@127.0.0.1:5432/backdoor_test");
    fill(tid("nickname"), "adhoc-test");
    fill(tid("username"), "");
    fill(tid("password"), "");
    click(tid("submit-button"));

    checkErrorPanel("The server is reachable but either the username or password is invalid.");
  }

  @Test
  void invalidPostgresUrl() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), "postgres://128.9.9.100:5432/backdoor_test");
    fill(tid("nickname"), "adhoc-test");
    fill(tid("username"), "");
    fill(tid("password"), "");
    click(tid("submit-button"));

    checkErrorPanel(30000, "The server is unreachable.");
  }

  @Test
  void invalidPostgresDatabaseName() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), "postgres://127.0.0.1:5432/backdoor_test_not_exist");
    fill(tid("nickname"), "adhoc-test");
    fill(tid("username"), "");
    fill(tid("password"), "");
    click(tid("submit-button"));

    checkErrorPanel("The server is reachable but it's likely that the database name is invalid, but it could be wrong username or password too.");
  }

  @Test
  void invalidClickHouseCredentials() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), "jdbc:ch://127.0.0.1:8123/backdoor_test");
    fill(tid("nickname"), "adhoc-test");
    fill(tid("username"), "asakldjf");
    fill(tid("password"), "random");
    click(tid("submit-button"));

    checkErrorPanel("The server is reachable but either the username or password is invalid.");
  }

// Timeout doesn't work for ClickHouse
// We may consider moving away from its JDBC client to its Java client instead.
//  @Test
//  void invalidClickHouseUrl() throws InterruptedException {
//    go("/");
//    click(tid("add-new-data-source-button"));
//    fill(tid("url"), "jdbc:ch://128.9.9.100:5432/backdoor_test");
//    fill(tid("nickname"), "adhoc-test");
//    fill(tid("username"), "");
//    fill(tid("password"), "");
//    click(tid("submit-button"));
//
//    Thread.sleep(60000);
////    checkErrorPanel("The server is reachable but either the username or password is invalid.");
//  }
}
