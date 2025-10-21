package tanin.backdoor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.HasAuthentication;
import org.openqa.selenium.UsernameAndPassword;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.sql.SQLException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class LoginTest extends Base {

  LoginTest() {
    shouldLoggedIn = false;
  }

  @Test
  void useCustomUser() throws InterruptedException {
    ((HasAuthentication) webDriver).register(UsernameAndPassword.of("backdoor", "test"));

    go("/");
    click(".CodeMirror");
    sendKeys("select current_user");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));

    assertColumnValues("current_user", "backdoor_test_user");
  }

  @Test
  void incorrectCustomUser() throws InterruptedException {
    ((HasAuthentication) webDriver).register(UsernameAndPassword.of("backdoor", "incorrect_password"));

    go("/");

    // Since the credential is invalid, Chrome will be stuck in a redirection loop.
    assertContains(elem("body").getText(), "ERR_TOO_MANY_RETRIES");
  }

  @Test
  void usePgUser() throws InterruptedException, SQLException {
    server.stop();

    server = new BackdoorServer("postgres://127.0.0.1:5432/" + DATABASE_NAME, PORT);
    server.start();

    ((HasAuthentication) webDriver).register(UsernameAndPassword.of("backdoor_test_user", "test"));

    go("/");
    click(".CodeMirror");
    sendKeys("select current_user");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));

    assertColumnValues("current_user", "backdoor_test_user");
  }


  @Test
  void incorrectPgUser() throws InterruptedException, SQLException {
    server.stop();

    server = new BackdoorServer("postgres://127.0.0.1:5432/" + DATABASE_NAME, PORT);
    server.start();

    ((HasAuthentication) webDriver).register(UsernameAndPassword.of("random_user", "test"));

    go("/");

    // Since the credential is invalid, Chrome will be stuck in a redirection loop.
    assertContains(elem("body").getText(), "ERR_TOO_MANY_RETRIES");
  }
}
