package tanin.backdoor.clickhouse;

import org.junit.jupiter.api.Test;
import tanin.backdoor.Base;

import static org.junit.jupiter.api.Assertions.*;

public class ExecuteTest extends Base {
  @Test
  void runExplain() throws InterruptedException {
    go("/");
    fillCodeMirror("explain select 1");
    select(tid("run-sql-database-select"), "clickhouse");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));
    assertFalse(hasElem(tid("menu-item-query"))); // Execute isn't Query and doesn't add to the left nav.

    assertSheetViewContent(
      """
          explain
        1
         Expression ((Project names + (Projection + Change column names to column identifiers)))
        2
           ReadFromStorage (SystemOne)"""
    );
  }

  @Test
  void updateSql() throws InterruptedException {
    go("/");
    fillCodeMirror("alter table project_setting update some_value = '123' where user_id = 'user_2'");
    select(tid("run-sql-database-select"), "clickhouse");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));
    assertFalse(hasElem(tid("menu-item-query")));

    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
          modified_count
        1
         0"""
    );

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting"));
    click(tid("sheet-view-column-header", "some_value", null, "sort-button"));
    waitUntil(() -> assertColumnValues("some_value", "1", "3", "4", "123"));
  }

  @Test
  void deleteSql() throws InterruptedException {
    go("/");
    fillCodeMirror("alter table project_setting delete where user_id = 'user_2'");
    select(tid("run-sql-database-select"), "clickhouse");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));
    assertFalse(hasElem(tid("menu-item-query")));

    assertContains(
      elem(tid("sheet-view-content")).getText(),
      """
          modified_count
        1
         0"""
    );

    click(tid("menu-items", "clickhouse", null, "menu-item-table", "project_setting"));
    click(tid("sheet-view-column-header", "user_id", null, "sort-button"));
    waitUntil(() -> assertColumnValues("user_id", "user_1", "user_3", "user_4"));
  }

  @Test
  void makeViewAndThenExecute() throws InterruptedException {
    go("/");
    fillCodeMirror("select 1");
    select(tid("run-sql-database-select"), "clickhouse");
    click(tid("run-sql-button"));

    waitUntil(() -> hasElem(tid("sheet-tab")));
    waitUntil(() -> hasElem(tid("menu-items", "clickhouse", null, "menu-item-query")));

    assertSheetViewContent(
      """
          1
        1
         1"""
    );

    fillCodeMirror("explain select 1");
    click(tid("run-sql-button"));
    waitUntil(() -> assertEquals(2, elems(tid("sheet-tab")).size()));
  }
}
