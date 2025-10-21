package tanin.backdoor;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public class Sort {

  public String name;
  public String direction;

  Sort(String name, String direction) {
    this.name = name;
    this.direction = direction;
  }

  JsonObject toJson() {
    return Json.object()
      .add("name", name)
      .add("direction", direction);
  }
}
