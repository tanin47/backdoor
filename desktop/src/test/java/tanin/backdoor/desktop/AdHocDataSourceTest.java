package tanin.backdoor.desktop;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import tanin.backdoor.core.DatabaseConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AdHocDataSourceTest extends Base {
  @Test
  void expand() throws InterruptedException {
    go("/");
    assertEquals("unloaded", elem(tid("database-item", "sqlite")).getDomAttribute("data-database-status"));
    click(tid("database-item", "sqlite"));
    assertEquals("loaded", elem(tid("database-item", "sqlite")).getDomAttribute("data-database-status"));
    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "sqlite", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void addEditAndDeleteSqlite() throws InterruptedException {
    go("/");
    click(tid("add-new-data-source-button"));
    fill(tid("url"), SQLITE_DATABASE_URL);
    fill(tid("nickname"), "adhoc-test");
    fill(tid("username"), "");
    fill(tid("password"), "");
    click(tid("submit-button"));

    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    assertEquals(
      List.of("sqlite", "adhoc-test"),
      elems(tid("database-item")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );

    assertEquals("loaded", elem(tid("database-item", "adhoc-test")).getDomAttribute("data-database-status"));
    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "adhoc-test", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );

    click(tid("database-item", "adhoc-test", null, "more-option-data-source-button"));
    click(tid("edit-data-source-button"));
    fill(tid("nickname"), "adhoc-test-updated");
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    assertEquals(
      List.of("sqlite", "adhoc-test-updated"),
      elems(tid("database-item")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );

    assertEquals("loaded", elem(tid("database-item", "adhoc-test-updated")).getDomAttribute("data-database-status"));
    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "adhoc-test-updated", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );

    click(tid("database-item", "adhoc-test-updated", null, "more-option-data-source-button"));
    click(tid("delete-data-source-button"));
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    assertEquals(
      List.of("sqlite"),
      elems(tid("database-item")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void loadFailedDatabaseAndEdit() throws Exception {
    server.handleUpdatingAdHocDataSourceConfigs(null, new DatabaseConfig[]{
      new DatabaseConfig("test-failed", "jdbc:sqlite:read-only.db?open_mode=1", null, null, true)
    });

    go("/");
    assertEquals("unloaded", elem(tid("database-item", "test-failed")).getDomAttribute("data-database-status"));
    click(tid("database-item", "test-failed"));
    waitUntil(() -> assertContains(
      elem(tid("edit-data-source-modal")).getText(),
      "Unable to connect to the data source. Please update the data source's information and try again."
    ));
    ((JavascriptExecutor) webDriver).executeScript("window.SET_URL_FOR_TESTING('" + SQLITE_DATABASE_URL + "?open_mode=1')");
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    assertEquals("loaded", elem(tid("database-item", "test-failed")).getDomAttribute("data-database-status"));
    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "test-failed", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }
}
