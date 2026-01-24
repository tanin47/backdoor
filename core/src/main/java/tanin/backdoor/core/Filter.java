package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

public class Filter {
  public enum Operator {
    EQUAL,
    IS_NULL,
    IS_NOT_NULL
  }


  public String name;
  public String value;
  public Operator operator;

  public Filter(String name, String value, Operator operator) {
    this.name = name;
    this.value = value;
    this.operator = operator;
  }

  public JsonValue toJson() {
    return Json.object()
      .add("name", name)
      .add("value", value)
      .add("operator", operator.toString());
  }
}
