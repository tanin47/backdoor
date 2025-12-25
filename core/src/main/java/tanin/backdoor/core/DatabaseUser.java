package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

public record DatabaseUser(String username, String password, String databaseNickname) {
  public JsonValue toJson() {
    return Json
      .object()
      .add("username", username)
      .add("password", password)
      .add("databaseNickname", databaseNickname);
  }

  public static DatabaseUser fromJson(JsonValue json) {
    var obj = json.asObject();

    return new DatabaseUser(
      obj.get("username").asString(),
      obj.get("password").asString(),
      obj.get("databaseNickname").asString()
    );
  }
}
