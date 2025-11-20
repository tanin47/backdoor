package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public class Column {
  public enum ColumnType {
    INTEGER,
    DOUBLE,
    BOOLEAN,
    STRING,
    TIMESTAMP,
    DATE,
    TIME,
  }


  public String name;
  public ColumnType type;
  public String rawType;
  int maxCharacterLength;
  boolean isPrimaryKey;
  boolean isNullable;

  public Column(String name, ColumnType type, String rawType, int maxCharacterLength, boolean isPrimaryKey, boolean isNullable) {
    this.name = name;
    this.type = type;
    this.rawType = rawType;
    this.maxCharacterLength = maxCharacterLength;
    this.isPrimaryKey = isPrimaryKey;
    this.isNullable = isNullable;
  }

  JsonObject toJson() {
    return Json.object()
      .add("name", name)
      .add("type", type.toString())
      .add("rawType", rawType)
      .add("maxCharacterLength", maxCharacterLength)
      .add("isPrimaryKey", isPrimaryKey)
      .add("isNullable", isNullable);
  }
}
