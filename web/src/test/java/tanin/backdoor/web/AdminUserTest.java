package tanin.backdoor.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminUserTest extends Base {
  @Test
  void addUser() throws Exception {
    var password = "abcdefg";
    go("/admin/user");
    click(tid("add-new-user-button"));
    fill(tid("username"), "test-added-user");
    fill(tid("password"), password);
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    var user = backdoorUserService.getByUsername("test-added-user");
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

    backdoorUserService.create("test-added-user", "sjfslkjljf");

    fill(tid("username"), "test-added-user");
    fill(tid("password"), "123456");
    click(tid("submit-button"));
    checkErrorPanel("The username is already used by another user. Please choose a different username.");
  }

  @Test
  void editUser() throws Exception {
    backdoorUserService.create("test-user", "sjfslkjljf");
    var user = backdoorUserService.getByUsername("test-user");
    go("/admin/user");
    click(tid("edit-button"));
    fill(tid("username"), "modified-user");
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    user = backdoorUserService.getById(user.id());
    assertEquals("modified-user", user.username());
  }

  @Test
  void deleteUser() throws Exception {
    backdoorUserService.create("test-user", "sjfslkjljf");
    var user = backdoorUserService.getByUsername("test-user");
    go("/admin/user");
    click(tid("delete-button"));
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    assertNull(backdoorUserService.getById(user.id()));
  }

  @Test
  void resetPassword() throws Exception {
    backdoorUserService.create("test-user", "sjfslkjljf");
    var user = backdoorUserService.getByUsername("test-user");
    backdoorUserService.setPassword(user.id(), "123456", null);
    user = backdoorUserService.getByUsername("test-user");
    assertNull(user.passwordExpiredAt());

    backdoorUserService.setPassword(user.id(), "123456", null);
    go("/admin/user");
    click(tid("reset-password-button"));
    fill(tid("password"), "abcdefg");
    click(tid("submit-button"));
    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));

    user = backdoorUserService.getByUsername("test-user");
    assertNotNull(user.passwordExpiredAt());
    assertTrue(PasswordHasher.verifyPassword("abcdefg", user.hashedPassword()));
  }

  @Test
  void validateResetPassword() throws Exception {
    backdoorUserService.create("test-user", "sjfslkjljf");
    var user = backdoorUserService.getByUsername("test-user");
    backdoorUserService.setPassword(user.id(), "123456", null);
    user = backdoorUserService.getByUsername("test-user");
    assertNull(user.passwordExpiredAt());

    backdoorUserService.setPassword(user.id(), "123456", null);
    go("/admin/user");
    click(tid("reset-password-button"));
    fill(tid("password"), "123");
    click(tid("submit-button"));
    checkErrorPanel("The password must be at least 6 characters long.");

    // Password doesn't change.
    user = backdoorUserService.getByUsername("test-user");
    assertNull(user.passwordExpiredAt());
    assertTrue(PasswordHasher.verifyPassword("123456", user.hashedPassword()));
  }
}
