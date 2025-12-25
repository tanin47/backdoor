package tanin.backdoor.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

public record SourceCodeUser(String username, String password) {
  public JsonValue toJson() {
    return Json
      .object()
      .add("username", username)
      .add("password", password);
  }

  public static SourceCodeUser fromJson(JsonValue json) {
    var obj = json.asObject();

    return new SourceCodeUser(
      obj.get("username").asString(),
      obj.get("password").asString()
    );
  }
}
