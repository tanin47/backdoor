package tanin.backdoor.core.engine;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;

import java.time.Instant;
import java.util.ArrayList;

public record AuthCookie(
  User[] users,
  DatabaseConfig[] adHocDatabaseConfigs,
  Instant expires
) {
  public JsonValue toJson() {
    var json = Json.object();
    json.add("expires", expires.toEpochMilli());

    var userJsons = Json.array();
    for (var user : users) {
      userJsons.add(
        Json
          .object()
          .add("database", user.databaseNickname())
          .add("username", user.username())
          .add("password", user.password())
      );
    }

    json.add("users", userJsons);

    var databaseConfigJsons = Json.array();
    for (var config : adHocDatabaseConfigs) {
      databaseConfigJsons.add(
        Json
          .object()
          .add("nickname", config.nickname)
          .add("url", config.jdbcUrl)
          .add("username", config.username)
          .add("password", config.password)
      );
    }

    json.add("adHocDatabaseConfigs", databaseConfigJsons);

    return json;
  }

  public static AuthCookie fromJson(JsonValue json) {
    var obj = json.asObject();
    var expires = Instant.ofEpochMilli(obj.get("expires").asLong());

    var users = new ArrayList<User>();
    for (JsonValue jsonValue : obj.get("users").asArray()) {
      var u = jsonValue.asObject();
      users.add(new User(
        u.get("username").asString(),
        u.get("password").asString(),
        u.get("database") == null || u.get("database").isNull() ? null : u.get("database").asString()
      ));
    }

    var adHocDatabaseConfigs = new ArrayList<DatabaseConfig>();
    if (obj.get("adHocDatabaseConfigs") != null) {
      for (JsonValue jsonValue : obj.get("adHocDatabaseConfigs").asArray()) {
        var u = jsonValue.asObject();
        adHocDatabaseConfigs.add(new DatabaseConfig(
          u.get("nickname").asString(),
          u.get("url").asString(),
          u.get("username").asString(),
          u.get("password").asString(),
          true
        ));
      }
    }

    return new AuthCookie(users.toArray(new User[0]), adHocDatabaseConfigs.toArray(new DatabaseConfig[0]), expires);
  }
}
