package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public record GlobalSettings(boolean dynamicUserEnabled, String analyticsName) {
  public JsonObject toJson() {
    return Json.object()
      .add("dynamicUserEnabled", dynamicUserEnabled)
      .add("analyticsName", analyticsName);
  }
}
