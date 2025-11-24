package tanin.backdoor.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LoginAdditionalTest extends Base {
  LoginAdditionalTest() {
    shouldLoggedIn = false;
  }

  @BeforeEach
  void beforeEach() throws Exception {
    server.stop();

    server = new BackdoorWebServer(
      new DatabaseConfig[]{
        new DatabaseConfig("postgres", POSTGRES_DATABASE_URL, null, null),
        new DatabaseConfig("clickhouse", CLICKHOUSE_DATABASE_URL, null, null),
      },
      PORT,
      -1,
      new User[0],
      "dontcare"
    );
    server.start();

    go("/login");
    webDriver.manage().deleteAllCookies();
    webDriver.manage().addCookie(new Cookie(
      "backdoor",
      BackdoorWebServer.makeAuthCookieValueForUser(
        new User[]{new User("backdoor_test_user", "test", "postgres")},
        new DatabaseConfig[0],
        server.secretKey,
        Instant.now().plus(1, ChronoUnit.DAYS)
      )
    ));
    go("/");

    click(tid("database-lock-item", "clickhouse"));
  }

  @Test
  void didNotClickCaptcha() throws InterruptedException {
    fill(tid("username"), "backdoor");
    fill(tid("password"), "test");
    click(tid("submit-button"));

    checkErrorPanel("The captcha is invalid. Please check \"I'm not a robot\" again.");
    assertEquals("false", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
  }

  @Test
  void loginToClickHouse() throws InterruptedException, SQLException {
    fill(tid("username"), "backdoor");
    fill(tid("password"), "test_ch");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(15000, () -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));

    waitUntil(() -> assertFalse(hasElem(tid("submit-button"))));
    assertEquals(
      List.of("project_setting"),
      elems(tid("menu-items", "clickhouse", null, "menu-item-table")).stream().map(e -> e.getDomAttribute("data-test-value")).toList()
    );
  }

  @Test
  void incorrectClickHouseUser() throws InterruptedException {
    fill(tid("username"), "backdoor_test_user");
    fill(tid("password"), "test");
    click(".altcha-checkbox input[type='checkbox']");
    waitUntil(() -> {
      assertEquals("true", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
    });
    click(tid("submit-button"));
    checkErrorPanel("The username or password is invalid.");
    assertEquals("false", elem(".altcha-checkbox input[type='checkbox']").getDomProperty("checked"));
  }
}
