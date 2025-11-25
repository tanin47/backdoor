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
import java.util.prefs.BackingStoreException;

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

  public static String makeHtml(String path, String csrfToken, Paradigm paradigm, String appVersion) throws IOException {
    return makeHtml(path, csrfToken, paradigm, appVersion, null);
  }

  public static String makeHtml(String path, String csrfToken, Paradigm paradigm, String appVersion, JsonObject props) throws IOException {
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
        "APP_VERSION", Json.value(appVersion).toString(),
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

  Engine makeEngine(String databaseNickname) throws SQLException, URISyntaxException, Engine.InvalidCredentialsException, Engine.OverwritingUserAndCredentialedJdbcConflictedException, Engine.UnreachableServerException, Engine.InvalidDatabaseNameProbablyException, BackingStoreException {
    var databaseConfig = Arrays.stream(getAllDatabaseConfigs()).filter(d -> d.nickname.equals(databaseNickname)).findFirst().orElse(null);

    if (databaseConfig == null) {
      throw new IllegalArgumentException("Database not found: " + databaseNickname);
    }

    return makeEngine(databaseConfig);
  }


  Engine makeEngine(DatabaseConfig databaseConfig) throws SQLException, URISyntaxException, Engine.InvalidCredentialsException, Engine.OverwritingUserAndCredentialedJdbcConflictedException, Engine.UnreachableServerException, Engine.InvalidDatabaseNameProbablyException {
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

        for (var databaseConfig : getAllDatabaseConfigs()) {
          var databaseJson = Json.object()
            .add("name", databaseConfig.nickname)
            .add("isAdHoc", databaseConfig.isAdHoc);

          try (var engine = makeEngine(databaseConfig)) {
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
        var originalSql = json.asObject().get("sql").asString().trim();
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
          SqlType sqlType = engine.getSqlType(originalSql);

          var sql = originalSql.trim().replaceAll(";$", "");

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
            originalSql,
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

    // TODO: Remove after switching to a new minum version
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

    wf.registerPath(
      POST,
      "api/add-data-source",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var nickname = json.asObject().get("nickname").asString().trim();
        var url = json.asObject().get("url").asString().trim();
        var username = json.asObject().get("username").asString().trim();
        var password = json.asObject().get("password").asString().trim();

        if (Arrays.stream(getAllDatabaseConfigs()).anyMatch(d -> d.nickname.equals(nickname))) {
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_400_BAD_REQUEST,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("errors", Json.array().add("The database '" + nickname + "' is already in use. Please pick a different nickname."))
              .toString()
          );
        }

        var adHocDatabaseConfig = new DatabaseConfig(
          nickname,
          url,
          username,
          password,
          true
        );

        try (var _engine = Engine.createEngine(adHocDatabaseConfig, null)) {
          return handleAddingValidDataSource(req, adHocDatabaseConfig);
        } catch (Engine.InvalidCredentialsException | Engine.UnreachableServerException |
                 Engine.InvalidDatabaseNameProbablyException e) {
          var message = "Unknown error. Please contact your administrator.";

          if (e instanceof Engine.InvalidCredentialsException) {
            message = "The server is reachable but either the username or password is invalid.";
          } else if (e instanceof Engine.UnreachableServerException) {
            message = "The server is unreachable.";
          } else if (e instanceof Engine.InvalidDatabaseNameProbablyException) {
            message = "The server is reachable but it's likely that the database name is invalid, but it could be wrong username or password too.";
          }

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_400_BAD_REQUEST,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("errors", Json.array().add(message))
              .toString()
          );
        } catch (UnsupportedOperationException e) {
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_400_BAD_REQUEST,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("errors", Json.array().add(e.getMessage()))
              .toString()
          );
        } catch (Exception e) {
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_400_BAD_REQUEST,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("errors", Json.array().add("Unknown error. Please contact your administrator."))
              .toString()
          );
        }
      }
    );

    wf.registerPath(
      POST,
      "api/delete-data-source",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var database = json.asObject().get("database").asString().trim();

        var removedDatabaseConfig = Arrays.stream(getAdHocDatabaseConfigs()).filter(d -> d.nickname.equals(database)).findFirst().orElse(null);

        return handleRemovingValidDataSource(req, removedDatabaseConfig);
      }
    );

    logger.info("Backdoor has been started (port: " + this.port + ", ssl-port: " + this.sslPort + ", databases: " + databaseConfigs.length + ").");

    return minum;
  }

  private DatabaseConfig[] getAllDatabaseConfigs() throws BackingStoreException {
    var all = new ArrayList<DatabaseConfig>(List.of(databaseConfigs));
    all.addAll(List.of(getAdHocDatabaseConfigs()));
    return all.toArray(new DatabaseConfig[0]);
  }

  protected abstract DatabaseConfig[] getAdHocDatabaseConfigs() throws BackingStoreException;

  protected abstract IResponse handleAddingValidDataSource(IRequest req, DatabaseConfig adHocDatabaseConfig) throws Exception;

  protected abstract IResponse handleRemovingValidDataSource(IRequest req, DatabaseConfig removedDatabaseConfig) throws Exception;

  protected IResponse processIndexPage(IRequest req) throws Exception {
    return Response.htmlOk(
      makeHtml("index.html", null, Paradigm.CORE, "core")
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
