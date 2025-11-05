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
  void didNotClickCaptcha() throws InterruptedException {
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor");
    fill(tid("password"), "test");
    click(tid("submit-button"));

    checkErrorPanel("The captcha is invalid. Please check \"I'm not a robot\" again.");
    assertEquals("false", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
  }

  @Test
  void useCustomUser() throws InterruptedException {
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(() -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("/", getCurrentPath()));

    click(".CodeMirror");
    sendKeys("select current_user");
    click(tid("run-sql-button"));

    waitUntil(() -> assertTrue(hasElem(tid("sheet-tab"))));

    assertColumnValues("current_user", "backdoor_test_user");
  }

  @Test
  void incorrectCustomUser() throws InterruptedException {
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor");
    fill(tid("password"), "test123");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(() -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));
    checkErrorPanel("The username or password is invalid.");
    assertEquals("false", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
  }

  @Test
  void usePgUser() throws InterruptedException, SQLException {
    server.stop();

    server = new BackdoorServer("postgres://127.0.0.1:5432/" + DATABASE_NAME, PORT);
    server.start();

    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor_test_user");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(() -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("/", getCurrentPath()));

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

    go("/");

    fill(tid("username"), "random_user");
    fill(tid("password"), "test123");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(() -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));
    checkErrorPanel("The username or password is invalid.");
    assertEquals("false", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
  }
}
