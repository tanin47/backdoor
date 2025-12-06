package tanin.backdoor.postgres;

import org.junit.jupiter.api.Test;
import tanin.backdoor.Base;

import static org.junit.jupiter.api.Assertions.*;

public class QueryTest extends Base {
  @Test
  void createRenameUpdateDeleteView() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    fillCodeMirror("select * from \"user\" order by id asc limit 1");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-view-colum-header-number"))));
    waitUntil(() -> assertTrue(hasElem(tid("menu-items", "postgres", null, "menu-item-query"))));

    assertEquals(
      """
          id
        username
        password
        1
         1
         test_user_1
         password1""",
      elem(tid("sheet-view-content")).getText()
    );

    click(tid("rename-query-button"));
    fill(tid("new-name"), "bdv_test");
    click(tid("submit-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "bdv_test"))));
    waitUntil(() -> assertTrue(hasElem(tid("menu-items", "postgres", null, "menu-item-query", "bdv_test"))));

    fillCodeMirror("select * from \"user\" order by id desc limit 1");
    click(tid("run-sql-button"));

    waitUntil(() -> {
      var expected = """
          id
        username
        password
        1
         4
         test_user_4
         password4""";
      assertEquals(expected, elem(tid("sheet-view-content")).getText());
    });

    click(tid("delete-query-button"));
    click(tid("submit-button"));

    waitUntil(() -> assertFalse(hasElem(tid("sheet-tab", "bdv_test"))));
    waitUntil(() -> assertFalse(hasElem(tid("menu-items", "postgres", null, "menu-item-query", "bdv_test"))));
  }

  @Test
  void makeNewSql() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    fillCodeMirror("select 1");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-view-colum-header-number"))));
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

    assertTrue(hasElem(tid("update-sql-label")));

    click(tid("make-new-sql-button"));

    fillCodeMirror("select 2");
    click(tid("run-sql-button"));

    waitUntil(() -> assertEquals(2, elems(tid("sheet-tab")).size()));
    waitUntil(() -> assertEquals(2, elems(tid("menu-items", "postgres", null, "menu-item-query")).size()));

    assertEquals(
      """
          ?column?
        1
         2""",
      elem(tid("sheet-view-content")).getText()
    );

    click(tid("sheet-tab"));

    assertEquals(
      """
          ?column?
        1
         1""",
      elem(tid("sheet-view-content")).getText()
    );
  }
}
