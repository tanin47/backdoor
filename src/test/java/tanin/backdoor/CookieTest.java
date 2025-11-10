package tanin.backdoor;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CookieTest extends Base {
  @Test
  void expiringCookie() throws Exception {
    go("/");

    webDriver.manage().addCookie(new Cookie(
      "backdoor",
      BackdoorServer.makeAuthCookieValueForUser(
        new User[]{loggedInUser},
        server.secretKey,
        Instant.now().plus(10, ChronoUnit.SECONDS))
    ));

    webDriver.navigate().refresh();
    Thread.sleep(11000);
    assertEquals("/", getCurrentPath());
    webDriver.navigate().refresh();
    waitUntil(() -> assertEquals("/login", getCurrentPath()));
  }
}
