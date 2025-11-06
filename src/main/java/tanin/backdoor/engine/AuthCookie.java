package tanin.backdoor.engine;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import tanin.backdoor.User;

import java.time.Instant;
import java.util.ArrayList;

public record AuthCookie(User[] users, Instant expires) {
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

    return new AuthCookie(users.toArray(new User[0]), expires);
  }
}
