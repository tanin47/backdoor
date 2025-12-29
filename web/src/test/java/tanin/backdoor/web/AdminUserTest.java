package tanin.backdoor.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import tanin.backdoor.core.DatabaseConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AdminUserTest extends Base {
  @Test
  void dynamicUserNotAllowed() throws Exception {
    dynamicUserService.create("test-backdoor-user", "abcdefg", null);
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
    go("/admin/user");
    waitUntil(() -> {
      assertContains(elem("body").getText(), "You are not allowed to manage dynamic users.");
    });
  }

  @Test
  void dynamicUserNotSeeingAdminUserButton() throws Exception {
    dynamicUserService.create("test-backdoor-user", "abcdefg", null);
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
    go("/");
    waitUntil(() -> hasElem(tid("logout-button")));
    assertFalse(hasElem(tid("admin-user-link-button")));
  }

  @Test
  void sourceCodeUserSeeAdminUserButton() throws Exception {
    go("/");
    waitUntil(() -> hasElem(tid("logout-button")));
    assertTrue(hasElem(tid("admin-user-link-button")));
  }

  @Test
  void backdoorJdbcUrlIsNotConfigured() throws Exception {
    server.stop();
    server = new BackdoorWebServerBuilder()
      .withPort(PORT)
      .addUser(loggedInUser.username(), loggedInUser.password())
      .withBackdoorDatabaseJdbcUrl(null)
      .build();
    server.start();
    webDriver.manage().addCookie(new Cookie(
      "backdoor",
      BackdoorWebServer.makeAuthCookieValueForUser(
        null,
        loggedInUser,
        null,
        new DatabaseConfig[0],
        server.secretKey,
        Instant.now().plus(1, ChronoUnit.DAYS)
      )
    ));
    go("/");
    waitUntil(() -> hasElem(tid("logout-button")));
    assertFalse(hasElem(tid("admin-user-link-button")));
  }

  @Test
  void addUser() throws Exception {
    var password = "abcdefg";
    go("/admin/user");
    click(tid("add-new-user-button"));
    fill(tid("username"), "test-added-user");
    fill(tid("password"), password);
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    var user = dynamicUserService.getByUsername("test-added-user");
    assertNotNull(user);
    assertTrue(PasswordHasher.verifyPassword(password, user.hashedPassword()));
    assertNotNull(user.passwordExpiredAt());
  }

  @Test
  void validateAddUser() throws Exception {
    go("/admin/user");
    click(tid("add-new-user-button"));
    click(tid("submit-button"));
    checkErrorPanel("The username must not be empty.");

    fill(tid("username"), "test-added-user");
    fill(tid("password"), "123");
    click(tid("submit-button"));
    checkErrorPanel("The password must be at least 6 characters long.");

    dynamicUserService.create("test-added-user", "sjfslkjljf", Instant.now().plus(1, ChronoUnit.DAYS));

    fill(tid("username"), "test-added-user");
    fill(tid("password"), "123456");
    click(tid("submit-button"));
    checkErrorPanel("The username is already used by another user. Please choose a different username.");
  }

  @Test
  void editUser() throws Exception {
    dynamicUserService.create("test-user", "sjfslkjljf", Instant.now().plus(1, ChronoUnit.DAYS));
    var user = dynamicUserService.getByUsername("test-user");
    go("/admin/user");
    click(tid("edit-button"));
    fill(tid("username"), "modified-user");
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    user = dynamicUserService.getById(user.id());
    assertEquals("modified-user", user.username());
  }

  @Test
  void deleteUser() throws Exception {
    dynamicUserService.create("test-user", "sjfslkjljf", Instant.now().plus(1, ChronoUnit.DAYS));
    var user = dynamicUserService.getByUsername("test-user");
    go("/admin/user");
    click(tid("delete-button"));
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    assertNull(dynamicUserService.getById(user.id()));
  }

  @Test
  void resetPassword() throws Exception {
    dynamicUserService.create("test-user", "sjfslkjljf", Instant.now().plus(1, ChronoUnit.DAYS));
    var user = dynamicUserService.getByUsername("test-user");
    dynamicUserService.setPassword(user.id(), "123456", null);
    user = dynamicUserService.getByUsername("test-user");
    assertNull(user.passwordExpiredAt());

    dynamicUserService.setPassword(user.id(), "123456", null);
    go("/admin/user");
    click(tid("reset-password-button"));
    fill(tid("password"), "abcdefg");
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    user = dynamicUserService.getByUsername("test-user");
    assertNotNull(user.passwordExpiredAt());
    assertTrue(PasswordHasher.verifyPassword("abcdefg", user.hashedPassword()));
  }

  @Test
  void validateResetPassword() throws Exception {
    dynamicUserService.create("test-user", "sjfslkjljf", Instant.now().plus(1, ChronoUnit.DAYS));
    var user = dynamicUserService.getByUsername("test-user");
    dynamicUserService.setPassword(user.id(), "123456", null);
    user = dynamicUserService.getByUsername("test-user");
    assertNull(user.passwordExpiredAt());

    dynamicUserService.setPassword(user.id(), "123456", null);
    go("/admin/user");
    click(tid("reset-password-button"));
    fill(tid("password"), "123");
    click(tid("submit-button"));
    checkErrorPanel("The password must be at least 6 characters long.");

    // Password doesn't change.
    user = dynamicUserService.getByUsername("test-user");
    assertNull(user.passwordExpiredAt());
    assertTrue(PasswordHasher.verifyPassword("123456", user.hashedPassword()));
  }
}
