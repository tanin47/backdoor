package tanin.backdoor.clickhouse;

import org.junit.jupiter.api.Test;
import tanin.backdoor.Base;

import static org.junit.jupiter.api.Assertions.*;

public class QueryTest extends Base {
  @Test
  void createRenameUpdateDeleteView() throws InterruptedException {
    go("/");
    fillCodeMirror("select * from \"project_setting\" order by user_id asc limit 1");
    select(tid("run-sql-database-select"), "clickhouse");
    click(tid("run-sql-button"));

    waitUntil(() -> hasElem(tid("sheet-view-colum-header-number")));
    waitUntil(() -> hasElem(tid("menu-items", "clickhouse", null, "menu-item-query")));

    assertSheetViewContent(
      """
          user_id
        project_id
        item_id
        some_value
        1
         user_1
         project_1
         item_1
         1"""
    );

    click(tid("rename-query-button"));
    fill(tid("new-name"), "bdv_test");
    click(tid("submit-button"));

    waitUntil(() -> hasElem(tid("sheet-tab", "bdv_test")));
    waitUntil(() -> hasElem(tid("menu-items", "clickhouse", null, "menu-item-query", "bdv_test")));

    fillCodeMirror("select * from \"project_setting\" order by user_id desc limit 1");
    click(tid("run-sql-button"));

    assertSheetViewContent(
      """
          user_id
        project_id
        item_id
        some_value
        1
         user_4
         project_4
         item_4
         4"""
    );

    click(tid("delete-query-button"));
    click(tid("submit-button"));

    waitUntil(() -> assertFalse(hasElem(tid("sheet-tab", "bdv_test"))));
    waitUntil(() -> assertFalse(hasElem(tid("menu-items", "clickhouse", null, "menu-item-query", "bdv_test"))));
  }

  @Test
  void makeNewSql() throws InterruptedException {
    go("/");
    fillCodeMirror("select 1");
    select(tid("run-sql-database-select"), "clickhouse");
    click(tid("run-sql-button"));

    waitUntil(() -> hasElem(tid("sheet-view-colum-header-number")));
    waitUntil(() -> hasElem(tid("menu-items", "clickhouse", null, "menu-item-query")));

    assertSheetViewContent(
      """
          1
        1
         1"""
    );

    assertTrue(hasElem(tid("update-sql-label")));

    click(tid("make-new-sql-button"));

    fillCodeMirror("select 2");
    click(tid("run-sql-button"));

    waitUntil(() -> assertEquals(2, elems(tid("sheet-tab")).size()));
    waitUntil(() -> assertEquals(2, elems(tid("menu-items", "clickhouse", null, "menu-item-query")).size()));

    assertSheetViewContent(
      """
          2
        1
         2"""
    );

    click(tid("sheet-tab"));

    assertSheetViewContent(
      """
          1
        1
         1"""
    );
  }
}
