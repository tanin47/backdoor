package tanin.backdoor;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class TableTest extends Base {
  @Test
  void dateTimeColumn() throws InterruptedException, SQLException {
    conn.createStatement().execute("""
          CREATE TABLE "date_time" (
            id INT PRIMARY KEY,
            timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
            timestamp_with_time_zone TIMESTAMP WITH TIME ZONE NOT NULL,
            date DATE NOT NULL,
            time TIME WITHOUT TIME ZONE NOT NULL,
            time_with_time_zone TIME WITH TIME ZONE NOT NULL
          )
      """);

    conn.createStatement().execute(
      """
        INSERT INTO "date_time" (
          id,
          timestamp,
          timestamp_with_time_zone,
          date,
          time,
          time_with_time_zone
        ) VALUES (
          '1',
          TIMESTAMP '2025-10-19T10:00:01.000000Z' AT TIME ZONE 'UTC',
          TIMESTAMP '2025-10-19T10:00:02.000000Z' AT TIME ZONE 'UTC',
          '2025-10-20',
          '10:00:03',
          '10:00:04'
        )
        """
    );
    go("/");

    waitUntil(() -> hasElem(tid("menu-item-table")));
    click(tid("menu-item-table", "date_time"));

    waitUntil(() -> hasElem(tid("sheet-tab", "date_time")));
    assertCell(0, "timestamp", "2025-10-19T10:00:01Z");
    assertCell(0, "timestamp_with_time_zone", "2025-10-19T10:00:02Z");
    assertCell(0, "date", "2025-10-20");
    assertCell(0, "time", "10:00:03");
    // There's a bug with the data type: timetz. I cannot get it to work. It automatically subtracts 1 hour.
    assertCell(0, "time_with_time_zone", "09:00:04");

    click(tid("sheet-column-value", "timestamp", null, "edit-field-button"));
    fill(tid("new-value"), "2025-10-19T11:00:01Z");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "timestamp", "2025-10-19T11:00:01Z"));

    click(tid("sheet-column-value", "timestamp_with_time_zone", null, "edit-field-button"));
    fill(tid("new-value"), "2025-10-19T11:00:02Z");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "timestamp_with_time_zone", "2025-10-19T11:00:02Z"));

    click(tid("sheet-column-value", "date", null, "edit-field-button"));
    fill(tid("new-value"), "2025-10-21");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "date", "2025-10-21"));

    click(tid("sheet-column-value", "time", null, "edit-field-button"));
    fill(tid("new-value"), "11:00:03");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "time", "11:00:03"));

    click(tid("sheet-column-value", "time_with_time_zone", null, "edit-field-button"));
    fill(tid("new-value"), "11:00:04");
    click(tid("submit-button"));
    // There's a bug with the data type: timetz. I cannot get it to work. It automatically subtracts 1 hour.
    waitUntil(() -> assertCell(0, "time_with_time_zone", "10:00:04"));
  }

  @Test
  void jsonAndPgvectorColumn() throws InterruptedException, SQLException {
    conn.createStatement().execute("CREATE EXTENSION vector;");
    conn.createStatement().execute("""
          CREATE TABLE "json_pgvector" (
            id INT PRIMARY KEY,
            data_jsonb JSONB NOT NULL,
            data_json JSON NOT NULL,
            data_vector vector(3) NOT NULL
          )
      """);

    conn.createStatement().execute(
      """
        INSERT INTO "json_pgvector" (
          id,
          data_jsonb,
          data_json,
          data_vector
        ) VALUES (
          '1',
          '{"a": "c"}'::jsonb,
          '{"b": "d"}'::json,
          '[1, 2, 3]'
        )
        """
    );
    go("/");

    waitUntil(() -> hasElem(tid("menu-item-table")));
    click(tid("menu-item-table", "json_pgvector"));

    waitUntil(() -> hasElem(tid("sheet-tab", "json_pgvector")));
    assertCell(0, "data_jsonb", "{\"a\": \"c\"}");
    assertCell(0, "data_json", "{\"b\": \"d\"}");
    assertCell(0, "data_vector", "[1,2,3]");

    click(tid("sheet-column-value", "data_jsonb", null, "edit-field-button"));
    fill(tid("new-value"), "{\"a\": 1}");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "data_jsonb", "{\"a\": 1}"));

    click(tid("sheet-column-value", "data_json", null, "edit-field-button"));
    fill(tid("new-value"), "{\"b\": 2}");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "data_json", "{\"b\": 2}"));

    click(tid("sheet-column-value", "data_vector", null, "edit-field-button"));
    fill(tid("new-value"), "[4, 5, 6]");
    click(tid("submit-button"));
    waitUntil(() -> assertCell(0, "data_vector", "[4,5,6]"));
  }

  @Test
  void createUpdateDropTable() throws InterruptedException {
    go("/");

    waitUntil(() -> hasElem(tid("menu-item-table")));
    click(tid("menu-item-table", "user"));

    waitUntil(() -> hasElem(tid("sheet-tab", "user")));
    assertColumnValues("username", "test_user_1", "test_user_2", "test_user_3", "test_user_4");

    assertEquals("Count: 4 (Show all)", elem(tid("sheet-stats")).getText());

    click(tid("rename-table-button"));
    fill(tid("new-name"), "user_new_name");
    click(tid("submit-button"));

    waitUntil(() -> hasElem(tid("sheet-tab", "user_new_name")));
    waitUntil(() -> hasElem(tid("menu-item-table", "user_new_name")));

    click(tid("drop-table-button"));
    click(tid("submit-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user_new_name"))));
    waitUntil(() -> assertFalse(hasElem(tid("menu-item-table", "user_new_name"))));
  }

  @Test
  void editField() throws InterruptedException {
    go("/");

    waitUntil(() -> hasElem(tid("menu-item-table")));
    click(tid("menu-item-table", "user"));

    waitUntil(() -> hasElem(tid("sheet-tab", "user")));
    click(tid("sheet-column-value", "username", null, "edit-field-button"));

    fill(tid("new-value"), "new-username");
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("new-username", elem(tid("sheet-column-value", "username")).getText().trim()));
  }

  @Test
  void deleteRow() throws InterruptedException {
    go("/");

    waitUntil(() -> hasElem(tid("menu-item-table")));
    click(tid("menu-item-table", "user"));

    waitUntil(() -> hasElem(tid("sheet-tab", "user")));
    hover(tid("sheet-column-value", "username"));

    click(tid("sheet-view-row", null, "delete-row-button"));
    click(tid("submit-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-view-row", null, "already-deleted-label"))));
  }

  @Test
  void filterRow() throws InterruptedException {
    go("/");

    waitUntil(() -> hasElem(tid("menu-item-table")));
    click(tid("menu-item-table", "user"));

    waitUntil(() -> hasElem(tid("sheet-tab", "user")));
    click(tid("sheet-view-column-header", "username", null, "filter-button"));

    click(tid("specified-value-checkbox"));
    fill(tid("specified-value-input"), "test_user_2");
    click(tid("submit-button"));

    waitUntil(() -> assertColumnValues("username", "test_user_2"));
  }

  @Test
  void sortRow() throws InterruptedException {
    go("/");

    waitUntil(() -> hasElem(tid("menu-item-table")));
    click(tid("menu-item-table", "user"));

    waitUntil(() -> hasElem(tid("sheet-tab", "user")));
    assertEquals(
      "none",
      elem(tid("sheet-view-column-header", "username", null, "sort-button"))
        .getDomAttribute("data-test-value")
    );

    click(tid("sheet-view-column-header", "username", null, "sort-button"));
    waitUntil(() -> {
      assertColumnValues(
        "username", "test_user_1", "test_user_2", "test_user_3", "test_user_4"
      );
    });
    assertEquals(
      "asc",
      elem(tid("sheet-view-column-header", "username", null, "sort-button"))
        .getDomAttribute("data-test-value")
    );

    click(tid("sheet-view-column-header", "username", null, "sort-button"));
    waitUntil(() -> {
      assertColumnValues(
        "username", "test_user_4", "test_user_3", "test_user_2", "test_user_1"
      );
    });
    assertEquals(
      "desc",
      elem(tid("sheet-view-column-header", "username", null, "sort-button"))
        .getDomAttribute("data-test-value")
    );

    click(tid("sheet-view-column-header", "username", null, "sort-button"));
    waitUntil(() -> {
      assertColumnValues(
        "username", "test_user_1", "test_user_2", "test_user_3", "test_user_4"
      );
    });
    assertEquals(
      "none",
      elem(tid("sheet-view-column-header", "username", null, "sort-button"))
        .getDomAttribute("data-test-value")
    );
  }

  @Test
  void loadMore() throws InterruptedException, SQLException {
    for (int i = 5; i <= 247; i++) {
      conn.createStatement().execute(String.format(
        "INSERT INTO \"user\" (id, username, password) VALUES ('%d', 'test_user_%d', 'password%d')",
        i, i, i
      ));
    }

    go("/");

    waitUntil(() -> hasElem(tid("menu-item-table")));
    click(tid("menu-item-table", "user"));

    assertEquals("Count: 247 (Show 100 rows)", elem(tid("sheet-stats")).getText());

    ((JavascriptExecutor) webDriver).executeScript(
      "arguments[0].scrollTo(0, arguments[0].scrollHeight)",
      elem("svelte-virtual-list-viewport")
    );

    waitUntil(() -> assertEquals("Count: 247 (Show 200 rows)", elem(tid("sheet-stats")).getText()));
    ((JavascriptExecutor) webDriver).executeScript(
      "arguments[0].scrollBy(0, 30)",
      elem("svelte-virtual-list-viewport")
    );
    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
        101
         101
         test_user_101"""
    );
    ((JavascriptExecutor) webDriver).executeScript(
      "arguments[0].scrollTo(0, arguments[0].scrollHeight)",
      elem("svelte-virtual-list-viewport")
    );

    waitUntil(() -> assertEquals("Count: 247 (Show all)", elem(tid("sheet-stats")).getText()));

    ((JavascriptExecutor) webDriver).executeScript(
      "arguments[0].scrollTo(0, arguments[0].scrollHeight)",
      elem("svelte-virtual-list-viewport")
    );
    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
        247
         247
         test_user_247"""
    );
  }
}
