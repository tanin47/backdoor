package tanin.backdoor.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SecurityTest extends Base {
  SecurityTest() {
    shouldLoggedIn = false;
  }

  @Test
  void expiringCookie() throws Exception {
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    webDriver.manage().addCookie(new Cookie(
      "backdoor",
      BackdoorWebServer.makeAuthCookieValueForUser(
        new User[]{loggedInUser},
        new DatabaseConfig[0],
        server.secretKey,
        Instant.now().plus(10, ChronoUnit.SECONDS))
    ));

    go("/");

    Thread.sleep(11000);
    assertEquals("/", getCurrentPath());
    webDriver.navigate().refresh();
    waitUntil(() -> assertEquals("/login", getCurrentPath()));
  }

  @Test
  void incorrectCsrfToken() throws Exception {
    go("/");
    waitUntil(() -> assertEquals("/login", getCurrentPath()));

    fill(tid("username"), "backdoor");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(() -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });

    webDriver.manage().addCookie(new Cookie(
      BackdoorWebServer.CSRF_COOKIE_KEY,
      "InvalidCsrfToken"
    ));

    click(tid("submit-button"));

    checkErrorPanel("The session has expired. Please refresh the page and try again.");

    webDriver.navigate().refresh();

    fill(tid("username"), "backdoor");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(() -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));

    waitUntil(() -> assertEquals("/", getCurrentPath()));
  }
}
