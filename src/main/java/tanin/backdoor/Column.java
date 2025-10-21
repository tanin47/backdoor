package tanin.backdoor;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public class Column {

  String name;
  String type;
  int maxCharacterLength;
  boolean isPrimaryKey;
  boolean isNullable;

  Column(String name, String type, int maxCharacterLength, boolean isPrimaryKey, boolean isNullable) {
    this.name = name;
    this.type = type;
    this.maxCharacterLength = maxCharacterLength;
    this.isPrimaryKey = isPrimaryKey;
    this.isNullable = isNullable;
  }

  JsonObject toJson() {
    return Json.object()
      .add("name", name)
      .add("type", type)
      .add("maxCharacterLength", maxCharacterLength)
      .add("isPrimaryKey", isPrimaryKey)
      .add("isNullable", isNullable);
  }
}
