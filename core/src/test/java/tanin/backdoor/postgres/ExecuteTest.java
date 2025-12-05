package tanin.backdoor.postgres;

import org.junit.jupiter.api.Test;
import tanin.backdoor.Base;

import static org.junit.jupiter.api.Assertions.*;

public class ExecuteTest extends Base {
  @Test
  void runExplain() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    fillCodeMirror("explain select 1");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));
    assertFalse(hasElem(tid("menu-items", "postgres", null, "menu-item-query"))); // Execute isn't Query and doesn't add to the left nav.

    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
          QUERY PLAN
        1"""
    );
  }

  @Test
  void updateSql() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    fillCodeMirror("update \"user\" set username = 'test_user_3_updated' where username = 'test_user_3'");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));
    assertFalse(hasElem(tid("menu-item-query")));

    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
          modified_count
        1
         1"""
    );

    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));
    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
    click(tid("sheet-view-column-header", "username", null, "sort-button"));
    waitUntil(() -> assertColumnValues(
      "username", "test_user_1", "test_user_2", "test_user_3_updated", "test_user_4"
    ));
  }

  @Test
  void deleteSql() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    fillCodeMirror("delete from \"user\" where username = 'test_user_3'");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));
    assertFalse(hasElem(tid("menu-item-query")));

    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
          modified_count
        1
         1"""
    );

    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));
    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
    waitUntil(() -> assertColumnValues(
      "username", "test_user_1", "test_user_2", "test_user_4"
    ));
  }

  @Test
  void makeViewAndThenExecute() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    fillCodeMirror("select 1");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));
    waitUntil(() -> assertTrue(hasElem(tid("menu-items", "postgres", null, "menu-item-query"))));

    waitUntil(() -> {
      assertEquals(
        """
            ?column?
          1
           1""",
        elem(tid("sheet-view-content")).getText()
      );
    });

    fillCodeMirror("explain select 1");
    click(tid("run-sql-button"));
    waitUntil(() -> assertEquals(2, elems(tid("sheet-tab")).size()));
  }
}
