package tanin.backdoor.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import tanin.backdoor.core.DatabaseConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SetPasswordTest extends Base {
  SetPasswordTest() {
    shouldLoggedIn = false;
  }

  @BeforeEach
  void beforeEach() throws Exception {
    dynamicUserService.create("test-backdoor-user", "abcdefg");
    var user = dynamicUserService.getByUsername("test-backdoor-user");
    webDriver.manage().addCookie(new Cookie(
      "backdoor",
      BackdoorWebServer.makeAuthCookieValueForUser(
        user,
        null,
        null,
        new DatabaseConfig[0],
        server.secretKey,
        Instant.now().plus(1, ChronoUnit.DAYS)
      )
    ));
    assertNotNull(user.passwordExpiredAt());
  }

  @Test
  void forceSettingPassword() throws Exception {
    go("/");
    fill(tid("password"), "123456");
    fill(tid("confirm-password"), "123456");
    click(tid("submit-set-password-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-set-password-button"))));

    var user = dynamicUserService.getByUsername("test-backdoor-user");
    assertNull(user.passwordExpiredAt());
    assertTrue(PasswordHasher.verifyPassword("123456", user.hashedPassword()));
  }

  @Test
  void validateSettingPassword() throws Exception {
    go("/");
    fill(tid("password"), "123456");
    fill(tid("confirm-password"), "12345");
    click(tid("submit-set-password-button"));
    checkErrorPanel("The password doesn't match the confirmed password.");

    fill(tid("password"), "12345");
    fill(tid("confirm-password"), "12345");
    click(tid("submit-set-password-button"));
    checkErrorPanel("The password must be at least 6 characters long.");
  }
}
