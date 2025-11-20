package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public class View {

  public String name;
  public String sql;

  View(String name, String sql) {
    this.name = name;
    this.sql = sql;
  }

  JsonObject toJson() {
    return Json.object()
      .add("name", name)
      .add("sql", sql);
  }
}
