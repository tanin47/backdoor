package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

public class DatabaseConfig {
  public String nickname;
  public String jdbcUrl;
  public String username;
  public String password;
  public boolean isAdHoc;

  public DatabaseConfig(String nickname, String jdbcUrl, String username, String password) {
    this(
      nickname,
      jdbcUrl,
      username,
      password,
      false
    );
  }

  public DatabaseConfig(String nickname, String jdbcUrl, String username, String password, boolean isAdHoc) {
    this.nickname = nickname;
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
    this.isAdHoc = isAdHoc;
  }

  public static DatabaseConfig fromJson(JsonValue json, boolean isAdhoc) {
    if (json == null || json.isNull()) {
      return null;
    }

    var obj = json.asObject();

    return new DatabaseConfig(
      Helpers.getString(obj, "nickname"),
      Helpers.getString(obj, "jdbcUrl"),
      Helpers.getString(obj, "username"),
      Helpers.getString(obj, "password"),
      Helpers.getBoolean(obj, "isAdHoc", isAdhoc)
    );
  }

  public JsonValue toJson() {
    return Json.object()
      .add("nickname", nickname)
      .add("jdbcUrl", jdbcUrl)
      .add("username", username)
      .add("password", password)
      .add("isAdHoc", isAdHoc);
  }
}
