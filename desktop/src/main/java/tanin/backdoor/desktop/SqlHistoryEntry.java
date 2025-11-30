package tanin.backdoor.desktop;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public record SqlHistoryEntry(String sql, String database, long executedAt) {
  public JsonObject toJson() {
    return Json.object()
      .add("sql", sql)
      .add("database", database)
      .add("executedAt", executedAt);
  }
}
