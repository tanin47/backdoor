package tanin.backdoor.clickhouse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import tanin.backdoor.Base;
import tanin.backdoor.core.engine.Engine;

import static org.junit.jupiter.api.Assertions.*;

public class TableTest extends Base {
  @Disabled("Timezone doesn't work correctly on CI")
  @Test
  void dateTimeColumn() throws Exception {
    try (var engine = server.engineProvider.createEngine(clickHouseConfig, null)) {
      engine.connection.createStatement().execute("""
            CREATE TABLE "date_time" (
              id Int,
              date_time DateTime('UTC'),
              date_time_64 DateTime64(9, 'UTC'),
              date Date,
              time Time,
              time_64 Time64(9)
            )
            ENGINE = ReplacingMergeTree()
            ORDER BY (id)
            PRIMARY KEY (id);
        """);

      engine.connection.createStatement().execute(
        """
          
             INSERT INTO "date_time" (
             id,
             date_time,
             date_time_64,
             date,
             time,
             time_64
           ) VALUES (
             '1',
             '2025-10-19 10:00:01',
             '2025-10-19 10:00:02.000000002',
             '2025-10-20',
             '10:00:03',
             '10:00:04.000000002'
           )
          """
      );
    }
    go("/");

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "date_time"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "date_time"))));
    assertCell(0, "date_time", "2025-10-19 17:00:01");
    assertCell(0, "date_time_64", "2025-10-19 17:00:02.000000002");
    assertCell(0, "date", "2025-10-20");
    assertCell(0, "time", "02:00:03");
    assertCell(0, "time_64", "02:00:04.000000002");

    click(tid("sheet-column-value", "date_time", null, "edit-field-button"));
    fill(tid("new-value"), "2025-10-19 11:00:01");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "date_time", "2025-10-19 18:00:01"));

    click(tid("sheet-column-value", "date_time_64", null, "edit-field-button"));
    fill(tid("new-value"), "2025-10-19 11:00:02.000000003");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "date_time_64", "2025-10-19 18:00:02.000000003"));

    click(tid("sheet-column-value", "date", null, "edit-field-button"));
    fill(tid("new-value"), "2025-10-21");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "date", "2025-10-21"));

    click(tid("sheet-column-value", "time", null, "edit-field-button"));
    fill(tid("new-value"), "02:00:03");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "time", "18:00:03"));

    click(tid("sheet-column-value", "time_64", null, "edit-field-button"));
    fill(tid("new-value"), "02:00:04.000000050");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "time_64", "18:00:04.000000050"));
  }

  @Test
  void jsonColumn() throws Exception {
    try (var engine = server.engineProvider.createEngine(clickHouseConfig, null)) {
      engine.connection.createStatement().execute("""
            CREATE TABLE "json_test" (
              id INT PRIMARY KEY,
              data_json JSON NOT NULL
            )
        """);

      engine.connection.createStatement().execute(
        """
          INSERT INTO "json_test" (
            id,
            data_json
          ) VALUES (
            '1',
            '{"b": "d"}'
          )
          """
      );
    }

    go("/");

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "json_test"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "json_test"))));
    assertCell(0, "data_json", "{\"b\":\"d\"}");

    click(tid("sheet-column-value", "data_json", null, "edit-field-button"));
    fill(tid("new-value"), "{\"b\": 2}");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "data_json", "{\"b\":2}"));
  }

  @Test
  void createUpdateDropTable() throws InterruptedException {
    go("/");

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "project_setting"))));
    click(tid("sheet-view-column-header", "user_id", null, "sort-button"));
    assertColumnValues("user_id", "user_1", "user_2", "user_3", "user_4");

    assertEquals("Count: 4 (Show all)", elem(tid("sheet-stats")).getText());

    click(tid("rename-table-button"));
    fill(tid("new-name"), "project_setting_new_name");
    click(tid("submit-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "project_setting_new_name"))));
    waitUntil(() -> assertTrue(hasElem(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting_new_name"))));

    click(tid("drop-table-button"));
    click(tid("submit-button"));

    waitUntil(() -> assertFalse(hasElem(tid("sheet-tab", "project_setting_new_name"))));
    waitUntil(() -> assertFalse(hasElem(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting_new_name"))));
  }

  @Test
  void editField() throws InterruptedException {
    go("/");

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "project_setting"))));
    click(tid("sheet-view-column-header", "some_value", null, "sort-button"));
    assertColumnValues("some_value", "1", "2", "3", "4");
    click(tid("sheet-column-value", "some_value", null, "edit-field-button"));

    fill(tid("new-value"), "999");
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("999", elem(tid("sheet-column-value", "some_value")).getText().trim()));
  }

  @Disabled("Hovering doesn't work on CI")
  @Test
  void deleteRow() throws InterruptedException {
    go("/");

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "project_setting"))));
    click(tid("sheet-view-column-header", "some_value", null, "sort-button"));
    assertColumnValues("user_id", "user_1", "user_2", "user_3", "user_4");
    hover(tid("sheet-column-value", "user_id"));
    Thread.sleep(2000);

    click(tid("sheet-view-row", null, "delete-row-button"));
    click(tid("submit-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-view-row", null, "already-deleted-label"))));
  }

  @Test
  void filterRow() throws InterruptedException {
    go("/");

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "project_setting"))));
    click(tid("sheet-view-column-header", "user_id", null, "filter-button"));

    click(tid("specified-value-checkbox"));
    fill(tid("specified-value-input"), "user_2");
    click(tid("submit-button"));

    waitUntil(() -> assertColumnValues("user_id", "user_2"));
  }

  @Test
  void sortRow() throws InterruptedException {
    go("/");

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "project_setting"))));
    assertEquals(
      "none",
      elem(tid("sheet-view-column-header", "user_id", null, "sort-button"))
        .getDomAttribute("data-test-value")
    );

    click(tid("sheet-view-column-header", "user_id", null, "sort-button"));
    waitUntil(() -> {
      assertColumnValues(
        "user_id", "user_1", "user_2", "user_3", "user_4"
      );
    });
    assertEquals(
      "asc",
      elem(tid("sheet-view-column-header", "user_id", null, "sort-button"))
        .getDomAttribute("data-test-value")
    );

    click(tid("sheet-view-column-header", "user_id", null, "sort-button"));
    waitUntil(() -> {
      assertColumnValues(
        "user_id", "user_4", "user_3", "user_2", "user_1"
      );
    });
    assertEquals(
      "desc",
      elem(tid("sheet-view-column-header", "user_id", null, "sort-button"))
        .getDomAttribute("data-test-value")
    );

    click(tid("sheet-view-column-header", "user_id", null, "sort-button"));
    // It's just random sort, so we don't care now.
    waitUntil(() -> assertEquals(
      "none",
      elem(tid("sheet-view-column-header", "user_id", null, "sort-button"))
        .getDomAttribute("data-test-value")
    ));
  }

  @Test
  void loadMore() throws Exception {
    try (var engine = server.engineProvider.createEngine(clickHouseConfig, null)) {
      for (int i = 5; i <= 247; i++) {
        engine.connection.createStatement().execute(String.format(
          "INSERT INTO \"project_setting\" (user_id, project_id, item_id, some_value) VALUES ('user_%d', 'project_%d', 'item_%d', '%d')",
          i, i, i, i
        ));
      }
    }

    go("/");

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting"));
    click(tid("sheet-view-column-header", "some_value", null, "sort-button"));
    waitUntil(() -> {
      assertContains(
        elem(tid("sheet-view-content")).getText(),
        """
          1
           user_1"""
      );
    });
    assertEquals("Count: 247 (Show 100 rows)", elem(tid("sheet-stats")).getText());

    ((JavascriptExecutor) webDriver).executeScript(
      "arguments[0].scrollTo(0, arguments[0].scrollHeight)",
      elem(".virtual-table")
    );

    waitUntil(() -> assertEquals("Count: 247 (Show 200 rows)", elem(tid("sheet-stats")).getText()));
    ((JavascriptExecutor) webDriver).executeScript(
      "arguments[0].scrollBy(0, 30)",
      elem(".virtual-table")
    );
    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
        101
         user_101"""
    );
    ((JavascriptExecutor) webDriver).executeScript(
      "arguments[0].scrollTo(0, arguments[0].scrollHeight)",
      elem(".virtual-table")
    );

    waitUntil(() -> assertEquals("Count: 247 (Show all)", elem(tid("sheet-stats")).getText()));

    ((JavascriptExecutor) webDriver).executeScript(
      "arguments[0].scrollTo(0, arguments[0].scrollHeight)",
      elem(".virtual-table")
    );
    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
        247
         user_247"""
    );
  }
}
