package tanin.backdoor.postgres;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import tanin.backdoor.Base;
import tanin.backdoor.core.engine.Engine;

import java.net.URISyntaxException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class TableTest extends Base {
  @Disabled("Timezone doesn't work correctly on CI")
  @Test
  void dateTimeColumn() throws Exception {
    try (var engine = server.engineProvider.createEngine(postgresConfig, null)) {
      engine.connection.createStatement().execute("""
            CREATE TABLE "date_time" (
              id INT PRIMARY KEY,
              timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
              timestamp_with_time_zone TIMESTAMP WITH TIME ZONE NOT NULL,
              date DATE NOT NULL,
              time TIME WITHOUT TIME ZONE NOT NULL,
              time_with_time_zone TIME WITH TIME ZONE NOT NULL
            )
        """);

      engine.connection.createStatement().execute(
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
    }
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));


    click(tid("menu-items", "postgres", null, "menu-item-table", "date_time"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "date_time"))));
    assertCell(0, "timestamp", "2025-10-19T10:00:01Z");
    assertCell(0, "timestamp_with_time_zone", "2025-10-19T10:00:02Z");
    assertCell(0, "date", "2025-10-20");
    assertCell(0, "time", "10:00:03");
    // There's a bug with the data type: timetz. I cannot get it to work. It automatically subtracts 1 hour.
    assertCell(0, "time_with_time_zone", "10:00:04");

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
  void jsonAndPgvectorColumn() throws Exception {
    try (var engine = server.engineProvider.createEngine(postgresConfig, null)) {
      engine.connection.createStatement().execute("CREATE EXTENSION vector;");
      engine.connection.createStatement().execute("""
            CREATE TABLE "json_pgvector" (
              id INT PRIMARY KEY,
              data_jsonb JSONB NOT NULL,
              data_json JSON NOT NULL,
              data_vector vector(3) NOT NULL
            )
        """);

      engine.connection.createStatement().execute(
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
    }

    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));


    click(tid("menu-items", "postgres", null, "menu-item-table", "json_pgvector"));

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
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));


    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
    assertColumnValues("username", "test_user_1", "test_user_2", "test_user_3", "test_user_4");

    assertEquals("Count: 4 (Show all)", elem(tid("sheet-stats")).getText());

    click(tid("rename-table-button"));
    fill(tid("new-name"), "user_new_name");
    click(tid("submit-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user_new_name"))));
    waitUntil(() -> assertTrue(hasElem(tid("menu-items", "postgres", null, "menu-item-table", "user_new_name"))));

    click(tid("drop-table-button"));
    click(tid("submit-button"));

    waitUntil(() -> assertFalse(hasElem(tid("sheet-tab", "user_new_name"))));
    waitUntil(() -> assertFalse(hasElem(tid("menu-items", "postgres", null, "menu-item-table", "user_new_name"))));
  }

  @Test
  void insertRowDefault() throws Exception {
    try (var pg = server.engineProvider.createEngine(postgresConfig, null)) {
      pg.connection.createStatement().execute(
        """
          CREATE TABLE "test_table" (
            id SERIAL PRIMARY KEY,
            id2 INT GENERATED ALWAYS AS IDENTITY,
            with_default_value TEXT NOT NULL DEFAULT 'some_default',
            nullable_field TEXT,
            required_field TEXT NOT NULL
          )
          """
      );
    }

    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    click(tid("menu-items", "postgres", null, "menu-item-table", "test_table"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "test_table"))));
    click(tid("insert-row-button"));

    fill(tid("insert-field", "required_field"), "some_required");
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    click(tid("close-button"));
    try (var pg = server.engineProvider.createEngine(postgresConfig, null)) {
      var conn = pg.connection;
      var rs = conn.createStatement().executeQuery("SELECT id, id2, with_default_value, nullable_field, required_field FROM \"test_table\"");
      rs.next();
      assertEquals(1, rs.getInt(1));
      assertEquals(1, rs.getInt(2));
      assertEquals("some_default", rs.getString(3));
      assertNull(rs.getString(4));
      assertEquals("some_required", rs.getString(5));
    }
  }

  @Test
  void insertRowNonDefault() throws Exception {
    try (var pg = server.engineProvider.createEngine(postgresConfig, null)) {
      pg.connection.createStatement().execute(
        """
          CREATE TABLE "test_table" (
            id SERIAL PRIMARY KEY,
            id2 INT GENERATED BY DEFAULT AS IDENTITY,
            with_default_value TEXT NOT NULL DEFAULT 'some_default',
            nullable_field TEXT,
            required_field TEXT NOT NULL
          )
          """
      );
    }

    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    click(tid("menu-items", "postgres", null, "menu-item-table", "test_table"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "test_table"))));
    click(tid("insert-row-button"));

    click(tid("default-toggle-button", "id"));
    fill(tid("insert-field", "id"), "12");
    click(tid("default-toggle-button", "id2"));
    fill(tid("insert-field", "id2"), "123");
    click(tid("default-toggle-button", "with_default_value"));
    fill(tid("insert-field", "with_default_value"), "non-default");
    click(tid("null-toggle-button", "nullable_field"));
    fill(tid("insert-field", "nullable_field"), "non-null");
    fill(tid("insert-field", "required_field"), "some_required");
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    click(tid("close-button"));
    try (var pg = server.engineProvider.createEngine(postgresConfig, null)) {
      var conn = pg.connection;
      var rs = conn.createStatement().executeQuery("SELECT id, id2, with_default_value, nullable_field, required_field FROM \"test_table\"");
      rs.next();
      assertEquals(12, rs.getInt(1));
      assertEquals(123, rs.getInt(2));
      assertEquals("non-default", rs.getString(3));
      assertEquals("non-null", rs.getString(4));
      assertEquals("some_required", rs.getString(5));
    }
  }

  @Test
  void editField() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
    click(tid("sheet-column-value", "username", null, "edit-field-button"));

    fill(tid("new-value"), "new-username");
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("new-username", elem(tid("sheet-column-value", "username")).getText().trim()));
  }

  @Disabled("Hovering doesn't work on CI")
  @Test
  void deleteRow() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));


    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
    hover(tid("sheet-column-value", "username"));

    click(tid("sheet-view-row", null, "delete-row-button"));
    click(tid("submit-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-view-row", null, "already-deleted-label"))));
  }

  @Test
  void filterRowSpecificValue() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));


    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
    click(tid("sheet-view-column-header", "username", null, "filter-button"));

    click(tid("specified-value-checkbox"));
    fill(tid("specified-value-input"), "test_user_2");
    click(tid("submit-button"));

    waitUntil(() -> assertColumnValues("username", "test_user_2"));
  }

  @Test
  void filterRowNullAndNotNull() throws Exception {
    try (var engine = server.engineProvider.createEngine(postgresConfig, null)) {
      engine.connection.createStatement().execute(
        """
             INSERT INTO "user" (
             id,
             username,
             password
           ) VALUES (
             '100',
             'null-user',
             NULL
           )
          """
      );
    }
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
    waitUntil(() -> assertColumnValues("username", "test_user_1", "test_user_2", "test_user_3", "test_user_4", "null-user"));

    click(tid("sheet-view-column-header", "password", null, "filter-button"));
    click(tid("not-null-checkbox"));
    click(tid("submit-button"));
    waitUntil(() -> assertColumnValues("username", "test_user_1", "test_user_2", "test_user_3", "test_user_4"));

    click(tid("sheet-view-column-header", "password", null, "filter-button"));
    click(tid("null-checkbox"));
    click(tid("submit-button"));
    waitUntil(() -> assertColumnValues("username", "null-user"));
  }

  @Test
  void sortRow() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));


    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
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
  void loadMore() throws Exception {
    try (var engine = server.engineProvider.createEngine(postgresConfig, null)) {
      for (int i = 5; i <= 247; i++) {
        engine.connection.createStatement().execute(String.format(
          "INSERT INTO \"user\" (id, username, password) VALUES ('%d', 'test_user_%d', 'password%d')",
          i, i, i
        ));
      }
    }

    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));


    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

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
         101
         test_user_101"""
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
         247
         test_user_247"""
    );
  }
}
