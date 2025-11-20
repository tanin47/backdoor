package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

public class Filter {

  public String name;
  public String value;

  Filter(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public JsonValue toJson() {
    return Json.object()
      .add("name", name)
      .add("value", value);
  }
}
