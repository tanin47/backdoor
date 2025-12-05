package tanin.backdoor.core;

import com.eclipsesource.json.JsonObject;

public class Helpers {
  public static String getString(JsonObject obj, String key, String defaultValue) {
    var value = obj.get(key);

    if (value == null || value.isNull()) {
      return defaultValue;
    }

    return value.asString();
  }

  public static String getString(JsonObject obj, String key) {
    return getString(obj, key, null);
  }

  public static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
    var value = obj.get(key);

    if (value == null || value.isNull()) {
      return defaultValue;
    }

    return value.asBoolean();
  }
}
