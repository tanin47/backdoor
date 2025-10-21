package tanin.backdoor;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public class Stats {

  int numberOfRows;

  Stats(int numberOfRows) {
    this.numberOfRows = numberOfRows;
  }

  JsonObject toJson() {
    return Json.object()
      .add("numberOfRows", numberOfRows);
  }
}
