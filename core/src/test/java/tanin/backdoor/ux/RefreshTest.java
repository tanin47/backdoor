package tanin.backdoor.ux;

import org.junit.jupiter.api.Test;
import tanin.backdoor.Base;
import tanin.backdoor.core.engine.Engine;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RefreshTest extends Base {
  @Test
  void refreshTableList() throws Exception {
    go("/");
    click(tid("database-item", "postgres"));

    assertEquals("loaded", elem(tid("database-item", "postgres")).getDomAttribute("data-database-status"));
    assertEquals(
      List.of("user"),
      elems(tid("menu-items", "postgres", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );

    try (var engine = server.engineProvider.createEngine(postgresConfig, null)) {
      engine.connection.createStatement().execute("""
            CREATE TABLE "new_table" (
              id INT PRIMARY KEY
            )
        """);
    }

    click(tid("database-item", "postgres", null, "more-option-data-source-button"));
    click(tid("refresh-data-source-button"));

    waitUntil(() -> {
      assertEquals(
        List.of("new_table", "user"),
        elems(tid("menu-items", "postgres", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
      );
    });
  }

  @Test
  void refreshTableData() throws Exception {
    go("/");
    click(tid("database-item", "postgres"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item", "postgres")).getDomAttribute("data-database-status")));
    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

    assertColumnValues("username", "test_user_1", "test_user_2", "test_user_3", "test_user_4");

    try (var engine = server.engineProvider.createEngine(postgresConfig, null)) {
      engine.connection.createStatement().execute("""
        INSERT INTO "user" (
          id,
          username,
          password
        ) VALUES (
          '5',
          'new_test_user',
          'new_password'
        )
        """);
    }

    click(tid("refresh-button"));

    assertColumnValues("username", "test_user_1", "test_user_2", "test_user_3", "test_user_4", "new_test_user");
  }

  @Test
  void refreshQueryData() throws Exception {
    go("/");
    click(tid("database-item", "postgres"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item", "postgres")).getDomAttribute("data-database-status")));

    fillCodeMirror("select * from \"user\" order by id asc");
    click(tid("run-sql-button"));

    assertColumnValues("username", "test_user_1", "test_user_2", "test_user_3", "test_user_4");

    try (var engine = server.engineProvider.createEngine(postgresConfig, null)) {
      engine.connection.createStatement().execute("""
        INSERT INTO "user" (
          id,
          username,
          password
        ) VALUES (
          '5',
          'new_test_user',
          'new_password'
        )
        """);
    }

    click(tid("refresh-button"));

    assertColumnValues("username", "test_user_1", "test_user_2", "test_user_3", "test_user_4", "new_test_user");
  }
}
