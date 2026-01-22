package tanin.backdoor.ux;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import tanin.backdoor.Base;
import tanin.backdoor.core.engine.Engine;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.time.Instant;

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

  @Test
  void useNowTimestamp() throws Exception {
    try (var engine = server.engineProvider.createEngine(postgresConfig, null)) {
      engine.connection.createStatement().execute("""
            CREATE TABLE "date_time" (
              id INT PRIMARY KEY,
              timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL
            )
        """);

      engine.connection.createStatement().execute(
        """
          
             INSERT INTO "date_time" (
             id,
             timestamp
           ) VALUES (
             '1',
             TIMESTAMP '2025-10-19T10:00:01.000000Z' AT TIME ZONE 'UTC'
           )
          """
      );
    }
    go("/");
    click(tid("database-item"));
    waitUntil(() -> assertEquals("loaded", elem(tid("database-item")).getDomAttribute("data-database-status")));

    click(tid("menu-items", "postgres", null, "menu-item-table", "date_time"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab", "date_time"))));
    click(tid("sheet-column-value", "timestamp", null, "edit-field-button"));
    click(tid("timestamp-now-button"));
    click(tid("submit-button"));
    waitUntil(() -> {
      var timestamp = Instant.parse(elem(tid("sheet-column-value", "timestamp")).getText().trim());
      assertTrue((Instant.now().getEpochSecond() - timestamp.getEpochSecond()) < 10);
    });
  }
}
