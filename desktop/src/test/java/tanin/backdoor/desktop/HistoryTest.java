package tanin.backdoor.desktop;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HistoryTest extends Base {
  @Test
  void makeNewSqlAndSearch() throws InterruptedException {
    go("/");
    fillCodeMirror("select 1");
    click(tid("run-sql-button"));

    waitUntil(() -> {
      assertEquals(
        """
            1
          1
           1""",
        elem(tid("sheet-view-content")).getText()
      );
    });

    fillCodeMirror("select 2");
    click(tid("run-sql-button"));

    waitUntil(() -> {
      assertEquals(
        """
            2
          1
           2""",
        elem(tid("sheet-view-content")).getText()
      );
    });

    click(tid("history-button"));
    waitUntil(() -> assertTrue(hasElem(tid("sql-history-entry-sql"))));

    assertIterableEquals(
      List.of("select 2", "select 1"),
      elems(tid("sql-history-entry-sql")).stream().map(WebElement::getText).toList()
    );

    fill(tid("search-input"), "1");

    waitUntil(() -> {
      assertIterableEquals(
        List.of("select 1"),
        elems(tid("sql-history-entry-sql")).stream().map(WebElement::getText).toList()
      );
    });
  }
}
