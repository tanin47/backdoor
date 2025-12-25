package tanin.backdoor.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

import java.time.Instant;

public record DynamicUser(String id, String username, String hashedPassword, Instant passwordExpiredAt) {
  public JsonValue toJson() {
    var obj = Json
      .object()
      .add("id", id)
      .add("username", username)
      .add("hashedPassword", hashedPassword);

    if (passwordExpiredAt != null) {
      obj.add("passwordExpiredAt", passwordExpiredAt.toEpochMilli());
    }

    return obj;
  }

  public static DynamicUser fromJson(JsonValue json) {
    var obj = json.asObject();
    var passwordExpiredAt = obj.get("passwordExpiredAt");

    return new DynamicUser(
      obj.get("id").asString(),
      obj.get("username").asString(),
      obj.get("hashedPassword").asString(),
      passwordExpiredAt != null && !passwordExpiredAt.isNull() ? Instant.ofEpochMilli(passwordExpiredAt.asLong()) : null
    );
  }
}
