package tanin.backdoor.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class Sheet {

  public String database;
  // TODO: Rename to table
  public String name;
  public String sql;
  public String type;
  public Column[] columns;
  public Filter[] filters;
  public Sort[] sorts;
  public Stats stats;
  public JsonValue[][] rows;

  Sheet(String database, String name, String sql, String type, Column[] columns, Filter[] filters, Sort[] sorts, Stats stats, JsonValue[][] rows) {
    this.database = database;
    this.name = name;
    this.sql = sql;
    this.type = type;
    this.columns = columns;
    this.filters = filters;
    this.sorts = sorts;
    this.stats = stats;
    this.rows = rows;
  }

  JsonObject toJson() {
    var columnsJson = Json.array();
    for (Column column : columns) {
      columnsJson.add(column.toJson());
    }

    var filtersJson = Json.array();
    if (filters != null) {
      for (Filter filter : filters) {
        filtersJson.add(filter.toJson());
      }
    }

    var sortsJson = Json.array();
    if (sorts != null) {
      for (Sort sort : sorts) {
        sortsJson.add(sort.toJson());
      }
    }

    var rowsJson = Json.array();
    for (JsonValue[] row : rows) {
      var rowJson = Json.array();
      for (JsonValue value : row) {
        rowJson.add(value);
      }

      rowsJson.add(rowJson);
    }

    return Json.object()
      .add("database", database)
      .add("name", name)
      .add("sql", sql)
      .add("type", type)
      .add("columns", columnsJson)
      .add("filters", filtersJson)
      .add("sorts", sortsJson)
      .add("stats", stats.toJson())
      .add("rows", rowsJson);
  }
}
