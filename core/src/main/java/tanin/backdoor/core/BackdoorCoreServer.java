package tanin.backdoor.core;


import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.web.*;
import tanin.backdoor.core.engine.Engine;
import tanin.ejwf.MinumBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static com.renomad.minum.web.RequestLine.Method.GET;
import static com.renomad.minum.web.RequestLine.Method.POST;

public abstract class BackdoorCoreServer {
  public static class AuthFailureException extends Exception {
  }

  public enum SqlType {
    SELECT,
    ADMINISTRATIVE,
    EXPLAIN,
    MODIFY
  }

  public enum Paradigm {
    CORE, // Only for testing
    WEB,
    DESKTOP,
  }

  private static final Logger logger = Logger.getLogger(BackdoorCoreServer.class.getName());

  static {
    try (var configFile = BackdoorCoreServer.class.getResourceAsStream("/logging.properties")) {
      LogManager.getLogManager().readConfiguration(configFile);
      logger.info("The log config (logging.properties) has been loaded.");
    } catch (IOException e) {
      logger.warning("Could not load the log config file (logging.properties): " + e.getMessage());
    }
  }

  public static String stripeSurroundingDoubleQuotes(String arg) {
    if (arg == null) {
      return null;
    }
    if (arg.length() < 2) {
      return arg;
    }
    if (arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
      return arg.substring(1, arg.length() - 1);
    }
    return arg;
  }

  public DatabaseConfig[] databaseConfigs;
  int port;
  int sslPort;
  MinumBuilder.KeyStore keyStore;
  private FullSystem minum;


  public BackdoorCoreServer(
    DatabaseConfig[] databaseConfigs,
    int port,
    int sslPort,
    MinumBuilder.KeyStore keyStore
  ) {
    this.databaseConfigs = databaseConfigs;
    this.port = port;
    this.sslPort = sslPort;
    this.keyStore = keyStore;
  }

  protected abstract User getUserByDatabaseConfig(DatabaseConfig databaseConfig);

  public static String makeSqlLiteral(String sql) {
    return "'" + sql.replace("'", "''") + "'";
  }

  public static String makeSqlName(String sql) {
    return '"' + sql.replace("\"", "").replace(";", "") + '"';
  }

  public static String makeHtml(String path, String csrfToken, Paradigm paradigm) throws IOException {
    return makeHtml(path, csrfToken, paradigm, null);
  }

  public static String makeHtml(String path, String csrfToken, Paradigm paradigm, JsonObject props) throws IOException {
    var layout = TemplateProcessor.buildProcessor(new String(BackdoorCoreServer.class.getResourceAsStream("/html/layout.html").readAllBytes()));
    var targetHtml = TemplateProcessor.buildProcessor(new String(BackdoorCoreServer.class.getResourceAsStream("/html/" + path).readAllBytes()));

    var propsMap = Map.<String, String>of();

    if (props != null) {
      propsMap = Map.of("props", props.toString());
    }

    return layout.renderTemplate(
      Map.of(
        "content", targetHtml.renderTemplate(propsMap),
        "IS_LOCAL_DEV_JSON", Json.value(MinumBuilder.IS_LOCAL_DEV).toString(),
        "CSRF_TOKEN", Json.value(csrfToken).toString(),
        "PARADIGM", Json.value(paradigm.toString()).toString()
      )
    );
  }


  public static String extractCookieByKey(String cookieKey, List<String> cookies) {
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies.getFirst().split(";"))
      .filter(s -> s.trim().startsWith(cookieKey + "="))
      .findFirst()
      .map(String::trim)
      .orElse(null);
  }

  Engine makeEngine(String databaseNickname) throws SQLException, URISyntaxException, Engine.InvalidCredentialsException, Engine.OverwritingUserAndCredentialedJdbcConflictedException {
    var databaseConfig = Arrays.stream(databaseConfigs).filter(d -> d.nickname.equals(databaseNickname)).findFirst().orElse(null);

    if (databaseConfig == null) {
      throw new IllegalArgumentException("Database not found: " + databaseNickname);
    }

    var user = getUserByDatabaseConfig(databaseConfig);
    return Engine.createEngine(databaseConfig, user);
  }

  public FullSystem start() throws SQLException {
    minum = MinumBuilder.start(this.port, this.sslPort, this.keyStore);
    var wf = minum.getWebFramework();


    wf.registerPath(
      POST,
      "api/get-relations",
      r -> {
        var databases = Json.array();

        for (var databaseConfig : databaseConfigs) {
          var databaseJson = Json.object().add("name", databaseConfig.nickname);

          try (var engine = makeEngine(databaseConfig.nickname)) {
            var tablesJson = Json.array();

            var tables = engine.getTables();
            for (var table : tables) {
              tablesJson.add(table);
            }

            databaseJson.add("tables", tablesJson);
            databaseJson.add("requireLogin", false);
          } catch (Engine.InvalidCredentialsException e) {
            databaseJson.add("requireLogin", true);
          }

          databases.add(databaseJson);
        }

        return Response.buildResponse(
          StatusLine.StatusCode.CODE_200_OK,
          Map.of("Content-Type", "application/json"),
          Json.object().add("databases", databases).toString()
        );
      }
    );

    wf.registerPath(
      POST,
      "api/delete-row",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var database = json.asObject().get("database").asString();
        var tableName = json.asObject().get("table").asString();
        var primaryKeyFilters = json.asObject().get("primaryKeys").asArray().values().stream().map(s -> {
          var o = s.asObject();
          var value = o.get("value");

          return new Filter(o.get("name").asString(), value.isNull() ? null : value.asString());
        }).toArray(Filter[]::new);

        try (var engine = makeEngine(database)) {
          engine.delete(tableName, primaryKeyFilters);

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json.object().toString()
          );
        }
      }
    );

    wf.registerPath(
      POST,
      "api/edit-field",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var database = json.asObject().get("database").asString();
        var tableName = json.asObject().get("table").asString();
        var columnName = json.asObject().get("column").asString();
        var newValue = json.asObject().get("value").asString();
        var setToNull = json.asObject().get("setToNull").asBoolean();
        var primaryKeyFilters = json.asObject().get("primaryKeys").asArray().values().stream().map(s -> {
          var o = s.asObject();
          var value = o.get("value");

          return new Filter(o.get("name").asString(), value.isNull() ? null : value.asString());
        }).toArray(Filter[]::new);

        try (var engine = makeEngine(database)) {
          var column = Arrays.stream(engine.getColumns(tableName)).filter(c -> c.name.equals(columnName)).findFirst().orElse(null);

          engine.update(tableName, column, setToNull ? null : newValue, primaryKeyFilters);
          var rs = engine.select(tableName, column, primaryKeyFilters);

          JsonValue newFetchedValue = Json.NULL;
          while (rs.next()) {
            newFetchedValue = engine.getJsonValue(rs, 1, column);
          }

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("newValue", newFetchedValue)
              .add("newCharacterLength", getValueLength(newFetchedValue, column))
              .toString()
          );
        }
      }
    );

    wf.registerPath(
      POST,
      "api/drop-table",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var database = json.asObject().get("database").asString();
        var tableName = json.asObject().get("table").asString();
        var useCascade = json.asObject().get("useCascade").asBoolean();

        try (var engine = makeEngine(database)) {
          engine.drop(tableName, useCascade);
        }
        return Response.buildResponse(
          StatusLine.StatusCode.CODE_200_OK,
          Map.of("Content-Type", "application/json"),
          Json.object().toString()
        );
      }
    );

    wf.registerPath(
      POST,
      "api/rename-table",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var database = json.asObject().get("database").asString();
        var tableName = json.asObject().get("table").asString();
        var newName = json.asObject().get("newName").asString();

        try (var engine = makeEngine(database)) {
          engine.rename(tableName, newName);

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json.object().toString()
          );
        }
      }
    );


    wf.registerPath(
      POST,
      "api/load-query",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var database = json.asObject().get("database").asString();
        var name = json.asObject().get("name").asString();
        var sql = json.asObject().get("sql").asString().trim();
        var offset = json.asObject().get("offset").asInt();
        var filters = json.asObject().get("filters").asArray().values().stream().map(s -> {
          var o = s.asObject();
          var value = o.get("value");

          return new Filter(o.get("name").asString(), value.isNull() ? null : value.asString());
        }).toArray(Filter[]::new);
        var sorts = json.asObject().get("sorts").asArray().values().stream().map(s -> {
          var o = s.asObject();
          var direction = o.get("direction").asString();

          if (direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc")) {
            // good
          } else {
            throw new IllegalStateException("Invalid sort direction: " + direction);
          }

          return new Sort(o.get("name").asString(), o.get("direction").asString());
        }).toArray(Sort[]::new);

        try (var engine = makeEngine(database)) {
          ResultSet rs = null;
          int modifyCount = 0;
          SqlType sqlType = engine.getSqlType(sql);
          if (sqlType == SqlType.SELECT) {
            rs = engine.executeQueryWithParams(sql, filters, sorts, offset, 100);
          } else if (sqlType == SqlType.EXPLAIN) {
            rs = engine.executeQuery(sql);
          } else if (sqlType == SqlType.MODIFY || sqlType == SqlType.ADMINISTRATIVE) {
            rs = null;
            modifyCount = engine.executeUpdate(sql);
          }

          Stats stats = new Stats(0);
          var columns = new ArrayList<Column>();
          JsonValue[][] rows;
          if (rs != null) {
            var metaData = rs.getMetaData();

            for (int i = 1; i <= metaData.getColumnCount(); i++) {
              var colName = metaData.getColumnName(i);
              var rawType = metaData.getColumnTypeName(i);
              columns.add(new Column(
                colName,
                engine.convertRawType(rawType),
                rawType,
                colName.length(),
                false,
                metaData.isNullable(i) == ResultSetMetaData.columnNullable
              ));
            }

            rows = readRows(engine, columns.toArray(new Column[0]), rs);

            if (sqlType == SqlType.SELECT) {
              stats = engine.getStats(sql, filters);
            } else {
              stats = new Stats(rows.length);
            }
          } else {
            var colName = "modified_count";
            var column = new Column(
              colName,
              Column.ColumnType.INTEGER,
              "int",
              colName.length(),
              false,
              false
            );

            rows = new JsonValue[1][1];
            rows[0][0] = Json.value(modifyCount);

            column.maxCharacterLength = Math.max(("" + modifyCount).length(), column.maxCharacterLength);
            columns.add(column);
          }

          var rowsJson = Json.array();

          for (var row : rows) {
            var rowJson = Json.array();
            for (var value : row) {
              rowJson.add(value);
            }
            rowsJson.add(rowJson);
          }

          var sheet = new Sheet(
            database,
            sqlType == SqlType.SELECT ? name : "",
            sql,
            sqlType == SqlType.SELECT ? "query" : "execute",
            columns.toArray(new Column[0]),
            filters,
            sorts,
            stats,
            rows
          );

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json.object()
              .add("sheet", sheet.toJson())
              .toString()
          );
        }
      }
    );

    wf.registerPath(
      POST,
      "api/load-table",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var database = json.asObject().get("database").asString();
        var name = json.asObject().get("name").asString();
        var offset = json.asObject().get("offset").asInt();
        var filters = json.asObject().get("filters").asArray().values().stream().map(s -> {
          var o = s.asObject();
          var value = o.get("value");

          return new Filter(o.get("name").asString(), value.isNull() ? null : value.asString());
        }).toArray(Filter[]::new);
        var sorts = json.asObject().get("sorts").asArray().values().stream().map(s -> {
          var o = s.asObject();

          return new Sort(o.get("name").asString(), o.get("direction").asString());
        }).toArray(Sort[]::new);

        try (var engine = makeEngine(database)) {
          var columns = engine.getColumns(name);
          var sql = engine.makeLoadTableSql(name, columns);
          var stats = engine.getStats(sql, filters);
          var rs = engine.executeQueryWithParams(sql, filters, sorts, offset, 100);
          var rows = readRows(engine, columns, rs);
          var rowsJson = Json.array();

          for (var row : rows) {
            var rowJson = Json.array();
            for (var value : row) {
              rowJson.add(value);
            }
            rowsJson.add(rowJson);
          }

          var sheet = new Sheet(
            engine.databaseConfig.nickname,
            name,
            sql,
            "table",
            columns,
            filters,
            sorts,
            stats,
            rows
          );

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json.object()
              .add("sheet", sheet.toJson())
              .toString()
          );
        }
      }
    );

    wf.registerPath(
      GET,
      "landing",
      this::processIndexPage
    );

    wf.registerPath(
      GET,
      "",
      this::processIndexPage
    );

    logger.info("Backdoor has been started (port: " + this.port + ", ssl-port: " + this.sslPort + ", databases: " + databaseConfigs.length + ").");

    return minum;
  }

  protected IResponse processIndexPage(IRequest req) throws Exception {
    return Response.htmlOk(
      makeHtml("index.html", null, Paradigm.CORE)
    );
  }

  private JsonValue[][] readRows(Engine engine, Column[] columns, ResultSet rs) throws SQLException {
    var rows = new ArrayList<JsonValue[]>();
    while (rs.next()) {
      var row = new JsonValue[columns.length];
      for (int i = 0; i < columns.length; i++) {
        var column = columns[i];
        var value = engine.getJsonValue(rs, i + 1, column);

        column.maxCharacterLength = Math.max(column.maxCharacterLength, getValueLength(value, column));
        row[i] = value;
      }
      rows.add(row);
    }
    return rows.toArray(new JsonValue[0][]);
  }

  public void stop() {
    try {
      minum.shutdown();
    } catch (Exception e) {
    }
  }

  private int getValueLength(JsonValue value, Column column) {
    if (value == Json.NULL) {
      return 4; // For NULL
    }

    var timestampLength = Instant.now().toString().length();

    var len = switch (column.type) {
      case INTEGER -> ("" + value.asLong()).length();
      case DOUBLE -> ("" + value.asDouble()).length();
      case BOOLEAN -> ("" + value.asBoolean()).length();
      case TIMESTAMP, DATE, TIME -> timestampLength;
      case STRING ->
        Arrays.stream(value.asString().split("\n")).max(Comparator.comparingInt(String::length)).get().length();
      default ->
        Arrays.stream(value.asString().split("\n")).max(Comparator.comparingInt(String::length)).get().length();
    };

    return Math.min(len, 60);
  }
}
