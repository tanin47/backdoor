package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

public record LoggedInUser(String username, boolean canManageDynamicUsers) {
  public JsonValue toJson() {
    return Json.object()
      .add("username", username)
      .add("canManageDynamicUsers", canManageDynamicUsers);
  }
}
