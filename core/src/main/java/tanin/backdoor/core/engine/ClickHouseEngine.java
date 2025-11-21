package tanin.backdoor.core.engine;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import tanin.backdoor.core.*;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;

import static tanin.backdoor.core.BackdoorCoreServer.makeSqlLiteral;
import static tanin.backdoor.core.BackdoorCoreServer.makeSqlName;

public class ClickHouseEngine extends Engine {
  private static final Logger logger = Logger.getLogger(ClickHouseEngine.class.getName());

  static {
    try {
      DriverManager.registerDriver(new com.clickhouse.jdbc.Driver());
      logger.info("Registered the ClickHouse driver");
    } catch (SQLException e) {
      logger.severe("Unable to register the ClickHouse driver: " + e);
      throw new RuntimeException(e);
    }
  }

  ClickHouseEngine(DatabaseConfig config, User overwritingUser) throws SQLException, InvalidCredentialsException, OverwritingUserAndCredentialedJdbcConflictedException, URISyntaxException {
    super(config, overwritingUser);
  }

  @Override
  protected void connect(DatabaseConfig config, User overwritingUser) throws SQLException, InvalidCredentialsException {
    var props = new Properties();

    if (overwritingUser != null) {
      props.setProperty("user", overwritingUser.username());
      props.setProperty("password", overwritingUser.password());
    } else {
      if (config.username != null) {
        props.setProperty("user", config.username);
      }
      if (config.password != null) {
        props.setProperty("password", config.password);
      }
    }
    props.setProperty("clickhouse_setting_mutations_sync", "2");
    props.setProperty("clickhouse_setting_enable_time_time64_type", "1");

    try {
      connection = DriverManager.getConnection(config.jdbcUrl, props);
      execute("SELECT 'backdoor_test_connection_for_click_house'");
    } catch (SQLException e) {
      if (e.getMessage().contains("Authentication failed") || e.getMessage().contains("AUTHENTICATION_FAILED")) {
        throw new InvalidCredentialsException(e.getMessage());
      } else {
        throw e;
      }
    }
  }

  @Override
  public Column[] getColumns(String table) throws SQLException {
    var rs = executeQuery("SELECT currentDatabase();");
    rs.next();
    var databaseName = rs.getString(1);

    var columns = new ArrayList<Column>();
    rs = executeQuery(
      "select name, type, is_in_primary_key FROM system.columns WHERE database = " +
        makeSqlLiteral(databaseName) + " AND `table` = " + makeSqlLiteral(table) +
        " ORDER BY position ASC;"
    );
    while (rs.next()) {
      var name = rs.getString("name");
      var rawType = rs.getString("type");
      columns.add(new Column(
        name,
        convertRawType(rawType),
        rawType,
        name.length(),
        rs.getInt("is_in_primary_key") == 1,
        rawType.startsWith("Nullable(")
      ));
    }

    return columns.toArray(new Column[0]);
  }

  @Override
  public String[] getTables() throws SQLException {
    var tables = new ArrayList<String>();
    var rs = executeQuery(
      "SHOW TABLES;"
    );
    while (rs.next()) {
      tables.add(rs.getString("name"));
    }

    return tables.toArray(new String[0]);
  }

  @Override
  public void update(String table, Column column, String newValue, Filter[] filters) throws Exception {
    if (filters.length == 0) {
      throw new Exception();
    }
    var whereClause = makeWhereClause(filters);

    if (whereClause.isEmpty()) {
      throw new Exception();
    }

    execute(
      "ALTER TABLE " + makeSqlName(table) +
        " UPDATE " + makeSqlName(column.name) + " = " +
        (newValue == null ? "NULL" : makeSqlLiteral(newValue)) +
        whereClause + ";"
    );
  }

  @Override
  public void delete(String table, Filter[] filters) throws Exception {
    if (filters.length == 0) {
      throw new Exception();
    }
    var whereClause = makeWhereClause(filters);

    if (whereClause.isEmpty()) {
      throw new Exception();
    }
    execute(
      "ALTER TABLE " + makeSqlName(table) + " DELETE " + whereClause
    );
  }

  @Override
  public void rename(String table, String newTableName) throws SQLException {
    execute("RENAME TABLE " + makeSqlName(table) + " TO " + makeSqlName(newTableName));
  }

  @Override
  public void close() throws Exception {
    if (connection != null) {
      connection.close();
    }
  }

  @Override
  public BackdoorCoreServer.SqlType getSqlType(String sql) {
    var sanitized = sql.toLowerCase().trim();
    if (sanitized.startsWith("explain")) {
      return BackdoorCoreServer.SqlType.EXPLAIN;
    }

    if (sanitized.startsWith("with") || sanitized.startsWith("select") || sanitized.startsWith("show")) {
      return BackdoorCoreServer.SqlType.SELECT;
    }

    return BackdoorCoreServer.SqlType.ADMINISTRATIVE;
  }

  @Override
  public Column.ColumnType convertRawType(String rawType) {
    var sanitized = rawType.toLowerCase();

    if (sanitized.contains("datetime")) {
      return Column.ColumnType.TIMESTAMP;
    } else if (sanitized.contains("time")) {
      return Column.ColumnType.TIME;
    } else if (sanitized.contains("date")) {
      return Column.ColumnType.DATE;
    } else if (
      sanitized.startsWith("int") ||
        sanitized.startsWith("bigint") ||
        sanitized.startsWith("mediumint") ||
        sanitized.startsWith("smallint") ||
        sanitized.startsWith("unit")
    ) {
      return Column.ColumnType.INTEGER;
    } else if (
      sanitized.startsWith("float") ||
        sanitized.startsWith("double") ||
        sanitized.startsWith("decimal")
    ) {
      return Column.ColumnType.DOUBLE;
    }

    return switch (rawType.toLowerCase()) {
      case "boolean", "bool" -> Column.ColumnType.BOOLEAN;
      default -> Column.ColumnType.STRING;
    };
  }

  @Override
  public JsonValue getJsonValue(ResultSet rs, int columnIndex, Column column) throws SQLException {
    var value = rs.getObject(columnIndex);
    if (value == null) {
      return Json.NULL;
    }

    var rawType = column.rawType.toLowerCase();
    if (rawType.equals("json") && value instanceof HashMap<?, ?>) {
      return Json.value(convertHashmapToJson((HashMap<Object, Object>) value).toString());
    }

    if (column.type == Column.ColumnType.TIME) {
      if (rawType.startsWith("time64(")) {
        // the type would look like time64(NUMBER)
        int precision = Integer.parseInt(rawType.substring("time64(".length(), rawType.length() - 1));
        var number = rs.getBigDecimal(columnIndex);
        long fraction = 0;
        if (precision <= 3) {
          number = number.multiply(BigDecimal.valueOf(10).pow(3 - precision));
        } else {
          BigDecimal[] comps = number.divideAndRemainder(BigDecimal.valueOf(10).pow(precision - 3));
          number = comps[0];
          fraction = comps[1].longValue();
        }

        return Json.value(new Time(number.longValue()) + "." + String.format("%09d", fraction));
      } else {
        return Json.value(new Time(rs.getLong(columnIndex) * 1000L).toString());
      }
    }

    if (column.type == Column.ColumnType.TIMESTAMP) {
      var timestamp = rs.getTimestamp(columnIndex);
      if (rawType.startsWith("datetime64(")) {
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS").withZone(ZoneOffset.UTC);
        return Json.value(formatter.format(timestamp.toInstant()));
      } else {
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
        return Json.value(formatter.format(timestamp.toInstant()));
      }
    }

    return switch (column.type) {
      case INTEGER -> Json.value(rs.getLong(columnIndex));
      case DOUBLE -> Json.value(rs.getDouble(columnIndex));
      case BOOLEAN -> Json.value(rs.getBoolean(columnIndex));
      case TIMESTAMP -> Json.value(rs.getTimestamp(columnIndex).toInstant().toString());
      case DATE -> Json.value(rs.getDate(columnIndex).toString());
      case TIME -> throw new RuntimeException();
      default -> Json.value(rs.getString(columnIndex));
    };
  }

  private JsonValue convertHashmapToJson(HashMap<Object, Object> value) {
    var json = new JsonObject();
    for (var entry : value.entrySet()) {
      var key = entry.getKey().toString();
      var val = entry.getValue();

      if (val == null) {
        json.add(key, Json.NULL);
      } else if (val instanceof HashMap<?, ?>) {
        json.add(key, convertHashmapToJson((HashMap<Object, Object>) val));
      } else if (val instanceof Boolean) {
        json.add(key, Json.value((Boolean) val));
      } else if (val instanceof Number) {
        if (val instanceof Float || val instanceof Double || val instanceof BigDecimal) {
          json.add(key, Json.value(((Number) val).doubleValue()));
        } else {
          json.add(key, Json.value(((Number) val).longValue()));
        }
      } else {
        json.add(key, Json.value(val.toString()));
      }
    }
    return json;
  }
}
