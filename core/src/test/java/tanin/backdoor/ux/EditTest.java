package tanin.backdoor.ux;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import tanin.backdoor.Base;

import static org.junit.jupiter.api.Assertions.*;

public class EditTest extends Base {
  @Test
  void editToNullAndRevertField() throws InterruptedException {
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    click(tid("menu-items", "postgres", null, "menu-item-table", "user"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "user"))));
    click(tid("sheet-column-value", "password", null, "edit-field-button"));
    click(tid("set-to-null"));
    click(tid("submit-button"));
    waitUntil(() -> assertEquals("null", elem(tid("sheet-column-value", "password")).getText().trim()));

    click(tid("sheet-column-value", "password", null, "edit-field-button"));
    click(tid("disabled-new-value-overlay"));
    waitUntil(() -> assertFalse(hasElem(tid("disabled-new-value-overlay"))));

    fill(tid("new-value"), "new-password");
    click(tid("submit-button"));
    waitUntil(() -> assertEquals("new-password", elem(tid("sheet-column-value", "password")).getText().trim()));
  }
}
