package tanin.backdoor.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

public record CommandLineUser(String username, String password) {
  public JsonValue toJson() {
    return Json
      .object()
      .add("username", username)
      .add("password", password);
  }

  public static CommandLineUser fromJson(JsonValue json) {
    var obj = json.asObject();

    return new CommandLineUser(
      obj.get("username").asString(),
      obj.get("password").asString()
    );
  }
}
