package tanin.backdoor;


import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.web.*;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.postgresql.Driver;
import tanin.ejwf.MinumBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static com.renomad.minum.web.RequestLine.Method.GET;
import static com.renomad.minum.web.RequestLine.Method.POST;

public class BackdoorServer {

  private enum SqlType {
    SELECT,
    EXPLAIN,
    MODIFY
  }

  private static final Logger logger = Logger.getLogger(BackdoorServer.class.getName());

  static {
    try (var configFile = BackdoorServer.class.getResourceAsStream("/backdoor_default_logging.properties")) {
      LogManager.getLogManager().readConfiguration(configFile);
      logger.info("The log config (backdoor_default_logging.properties) has been loaded.");
    } catch (IOException e) {
      logger.warning("Could not load the log config file (backdoor_default_logging.properties): " + e.getMessage());
    }

    try {
      DriverManager.registerDriver(new org.postgresql.Driver());
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws SQLException, URISyntaxException {
    logger.info("Detected command-line arguments: " + Arrays.toString(args));

    String url = null;
    int port = 0;
    int sslPort = 0;
    User[] users = null;

    if (MinumBuilder.getIsLocalDev()) {
      url = "postgres://backdoor_test_user:test@127.0.0.1:5432/backdoor_test";
      port = 9090;
      users = new User[]{new User("backdoor", "1234")};
    }

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-url":
          if (i + 1 < args.length) url = args[++i];
          break;
        case "-port":
          if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
          break;
        case "-ssl-port":
          if (i + 1 < args.length) sslPort = Integer.parseInt(args[++i]);
          break;
        case "-user":
          if (i + 1 < args.length) {
            var comps = args[++i].split(",");
            var index = 0;
            var buffer = new ArrayList<User>();
            while (index < comps.length) {
              try {
                buffer.add(new User(comps[index++], comps[index++]));
              } catch (ArrayIndexOutOfBoundsException e) {
                throw new RuntimeException("`-user` is not in a valid format. The numbers of usernames and passwords must match. The usernames and passwords must not contain a space nor a comma.");
              }
            }
            users = buffer.toArray(new User[0]);
          }
          break;
      }
    }

    if (url == null) {
      throw new RuntimeException("You must specify the database url using `-url <YOUR_DATABASE_URL>`");
    }

    if (port == 0) {
      throw new RuntimeException("You must specify the port using `-port <PORT>`");
    }

    if (users == null || users.length == 0) {
      throw new RuntimeException("You must specify the users using `-user <USERNAME>,<PASSWORD>,<USERNAME2>,<PASSWORD2>`");
    }

    var main = new BackdoorServer(url, port, sslPort, users);
    var minum = main.start();
    minum.block();
  }

  String databaseUrl;
  int port;
  int sslPort;
  private FullSystem minum;
  User[] users;
  String hostName;
  boolean isLocalDev = MinumBuilder.getIsLocalDev();
  ThreadLocal<String> loggedInUser = new ThreadLocal<>();

  public BackdoorServer(
    String databaseUrl,
    int port,
    User[] users
  ) {
    this(databaseUrl, port, 0, users);
  }

  public BackdoorServer(
    String databaseUrl,
    int port,
    int sslPort,
    User[] users
  ) {
    this.databaseUrl = databaseUrl;
    this.port = port;
    this.sslPort = sslPort;

    for (User user : users) {
      assert user.username() != null && !user.username().isEmpty();
      assert user.password() != null && !user.password().isEmpty();
    }

    this.users = users;

    this.hostName = extractHost(this.databaseUrl);
  }

  private String extractHost(String databaseUrl) {
    try {
      URI uri;
      if (databaseUrl.startsWith("jdbc:postgresql://")) {
        uri = new URI(databaseUrl.substring("jdbc:".length()));
      } else if (databaseUrl.startsWith("postgresql://") || databaseUrl.startsWith("postgres://")) {
        uri = new URI(databaseUrl);
      } else {
        throw new IllegalArgumentException("Invalid JDBC or Postgresql URL format");
      }
      return uri.getHost();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid URL format: " + e.getMessage());
    }
  }

  static String makeSqlLiteral(String sql) {
    return "'" + sql.replace("'", "''") + "'";
  }

  static String makeSqlName(String sql) {
    return '"' + sql.replace("\"", "").replace(";", "") + '"';
  }

  String makeHtml(String path) throws IOException {
    return makeHtml(path, null);
  }

  String makeHtml(String path, @Nullable JsonObject props) throws IOException {
    var layout = TemplateProcessor.buildProcessor(new String(BackdoorServer.class.getResourceAsStream("/html/layout.html").readAllBytes()));
    var targetHtml = TemplateProcessor.buildProcessor(new String(BackdoorServer.class.getResourceAsStream("/html/" + path).readAllBytes()));

    var propsMap = Map.<String, String>of();

    if (props != null) {
      propsMap = Map.of("props", props.toString());
    }

    return layout.renderTemplate(
      Map.of(
        "content", targetHtml.renderTemplate(propsMap),
        "TARGET_HOSTNAME", this.hostName,
        "TARGET_HOSTNAME_JSON", Json.value(this.hostName).toString(),
        "IS_LOCAL_DEV_JSON", Json.value(this.isLocalDev).toString()
      )
    );
  }

  void checkAuth(IRequest req) {
    var authErrorResponse = Response.buildResponse(
      StatusLine.StatusCode.CODE_401_UNAUTHORIZED,
      Map.of(
        "WWW-Authenticate", "Basic realm=\"Backdoor\"",
        "Content-Type", "text/plain"
      ),
      "Require correct username and password. Please contact your administrator."
    );

    var authHeaders = req.getHeaders().valueByKey("Authorization");
    if (authHeaders == null || authHeaders.isEmpty() || !authHeaders.getFirst().startsWith("Basic ")) {
      throw new EarlyExitException(authErrorResponse);
    }

    var authHeader = authHeaders.getFirst();

    var base64Credentials = authHeader.substring("Basic ".length()).trim();
    var credentials = new String(Base64.getDecoder().decode(base64Credentials));
    var parts = credentials.split(":", 2);

    if (parts.length != 2) {
      throw new EarlyExitException(authErrorResponse);
    }

    var username = parts[0];
    var password = parts[1];

    var found = Arrays.stream(users)
      .filter(u -> u.username().equals(username) && u.password().equals(password))
      .findFirst()
      .orElse(null);

    if (found == null) {
      throw new EarlyExitException(authErrorResponse);
    }

    loggedInUser.set(found.username());
  }

  ThrowingFunction<IRequest, IResponse> handleEndpoint(ThrowingFunction<IRequest, IResponse> handler) {
    return req -> {
      try {
        checkAuth(req);
        return handler.apply(req);
      } catch (EarlyExitException e) {
        return e.response;
      } catch (SQLException e) {
        logger.log(Level.WARNING, e.getMessage(), e);

        return Response.buildResponse(
          StatusLine.StatusCode.CODE_400_BAD_REQUEST,
          Map.of(
            "Content-Type", "application/json"
          ),
          Json.object()
            .add("errors", Json.array().add(e.getMessage()))
            .toString()
        );
      } finally {
        loggedInUser.set(null);
      }
    };
  }

  public static Connection makeConnection(String url) throws SQLException, URISyntaxException {
    if (url.startsWith("jdbc:postgresql://")) {
      return DriverManager.getConnection(url);
    } else if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
      var uri = new URI(url);
      var userAndPassword = uri.getUserInfo().split(":");
      var user = userAndPassword[0];
      var password = userAndPassword[1];
      var host = uri.getHost();
      var port = uri.getPort();
      var database = uri.getPath().substring(1);

      var props = new Properties();
      props.setProperty("user", user);
      props.setProperty("password", password);

      var connUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;

      return DriverManager.getConnection(connUrl, props);
    } else {
      throw new IllegalArgumentException("PostgreSQL or JDBC URL is invalid");
    }
  }

  SqlSession makeSqlSession() throws SQLException, URISyntaxException {
    return new SqlSession(databaseUrl, loggedInUser.get());
  }

  public FullSystem start() throws SQLException {
    minum = MinumBuilder.start(this.port, this.sslPort);
    var wf = minum.getWebFramework();

    DriverManager.registerDriver(new Driver());

    wf.registerPath(
      GET,
      "",
      handleEndpoint(req -> {
        return Response.htmlOk(makeHtml("index.html"));
      })
    );

    wf.registerPath(
      POST,
      "api/get-relations",
      handleEndpoint(
        r -> {
          try (var session = makeSqlSession()) {
            var rs = session.executeQuery(
              "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name ASC;"
            );
            var tablesJson = Json.array();
            while (rs.next()) {
              tablesJson.add(rs.getString("table_name"));
            }

            return Response.buildResponse(
              StatusLine.StatusCode.CODE_200_OK,
              Map.of("Content-Type", "application/json"),
              Json.object()
                .add("tables", tablesJson)
                .toString()
            );
          }
        }
      )
    );

    wf.registerPath(
      POST,
      "api/delete-row",
      handleEndpoint(req -> {
        var json = Json.parse(req.getBody().asString());
        var tableName = json.asObject().get("table").asString();
        var primaryKeyValue = json.asObject().get("primaryKeyValue").asString();
        var primaryKeyColumn = json.asObject().get("primaryKeyColumn").asString();

        try (var session = makeSqlSession()) {
          session.execute(
            "DELETE FROM " + makeSqlName(tableName) + " WHERE " + makeSqlName(primaryKeyColumn) + " = " + makeSqlLiteral(primaryKeyValue) + ";"
          );
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json.object().toString()
          );
        }
      })
    );

    wf.registerPath(
      POST,
      "api/edit-field",
      handleEndpoint(req -> {
        var json = Json.parse(req.getBody().asString());
        var tableName = json.asObject().get("table").asString();
        var columnName = json.asObject().get("column").asString();
        var newValue = json.asObject().get("value").asString();
        var setToNull = json.asObject().get("setToNull").asBoolean();
        var primaryKeyColumnName = json.asObject().get("primaryKeyColumn").asString();
        var primaryKeyValue = json.asObject().get("primaryKeyValue").asString();

        try (var session = makeSqlSession()) {
          var column = getTableColumns(session, tableName).stream().filter(c -> c.name.equals(columnName)).findFirst().orElse(null);

          var whereClause = " WHERE " + makeSqlName(primaryKeyColumnName) + " = " + makeSqlLiteral(primaryKeyValue);

          session.execute(
            "UPDATE " + makeSqlName(tableName) +
              " SET " + makeSqlName(columnName) + " = " +
              (setToNull ? "NULL" : makeUpdateValue(column, newValue)) +
              whereClause + ";"
          );

          var rs = session.executeQuery(
            "SELECT " + makeSqlName(columnName) + " FROM " + makeSqlName(tableName) + whereClause
          );

          JsonValue newFetchedValue = Json.NULL;
          if (column != null) {
            while (rs.next()) {
              newFetchedValue = getJsonValue(rs, 1, column);
            }
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
      })
    );

    wf.registerPath(
      POST,
      "api/drop-table",
      handleEndpoint(req -> {
        var json = Json.parse(req.getBody().asString());
        var tableName = json.asObject().get("table").asString();
        var useCascade = json.asObject().get("useCascade").asBoolean();

        var maybeCascade = useCascade ? " CASCADE" : "";

        try (var session = makeSqlSession()) {
          session.execute(
            "DROP TABLE IF EXISTS " + makeSqlName(tableName) + maybeCascade
          );
        }
        return Response.buildResponse(
          StatusLine.StatusCode.CODE_200_OK,
          Map.of("Content-Type", "application/json"),
          Json.object().toString()
        );
      })
    );

    wf.registerPath(
      POST,
      "api/drop-view",
      handleEndpoint(req -> {
        var json = Json.parse(req.getBody().asString());
        var viewName = json.asObject().get("view").asString();

        try (var session = makeSqlSession()) {
          session.execute("DROP VIEW IF EXISTS " + makeSqlName(viewName));

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json.object().toString()
          );
        }
      })
    );

    wf.registerPath(
      POST,
      "api/rename-table",
      handleEndpoint(req -> {
        var json = Json.parse(req.getBody().asString());
        var tableName = json.asObject().get("table").asString();
        var newName = json.asObject().get("newName").asString();

        try (var session = makeSqlSession()) {
          session.execute("ALTER TABLE " + makeSqlName(tableName) + " RENAME TO " + makeSqlName(newName));

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json.object().toString()
          );
        }
      })
    );


    wf.registerPath(
      POST,
      "api/load-query",
      handleEndpoint(req -> {
        var json = Json.parse(req.getBody().asString());
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

          return new Sort(o.get("name").asString(), o.get("direction").asString());
        }).toArray(Sort[]::new);

        try (var session = makeSqlSession()) {
          ResultSet rs = null;
          int modifyCount = 0;
          SqlType sqlType = getSqlType(session, sql);
          if (sqlType == SqlType.SELECT) {
            rs = executeQueryWithParams(session, sql, filters, sorts, offset, 100);
          } else if (sqlType == SqlType.EXPLAIN) {
            rs = session.executeQuery(sql);
          } else if (sqlType == SqlType.MODIFY) {
            rs = null;
            modifyCount = session.executeUpdate(sql);
          }

          Stats stats = new Stats(0);
          var columns = new ArrayList<Column>();
          JsonValue[][] rows;
          if (rs != null) {
            var metaData = rs.getMetaData();

            for (int i = 1; i <= metaData.getColumnCount(); i++) {
              var colName = metaData.getColumnName(i);
              columns.add(new Column(
                colName,
                metaData.getColumnTypeName(i),
                colName.length(),
                false,
                metaData.isNullable(i) == ResultSetMetaData.columnNullable
              ));
            }

            rows = readRows(columns, rs);

            if (sqlType == SqlType.SELECT) {
              stats = getStats(session, sql, filters);
            } else {
              stats = new Stats(rows.length);
            }
          } else {
            var colName = "modified_count";
            var column = new Column(
              colName,
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
      })
    );

    wf.registerPath(
      POST,
      "api/load-table",
      handleEndpoint(req -> {
        var json = Json.parse(req.getBody().asString());
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

        try (var session = makeSqlSession()) {
          var columns = getTableColumns(session, name);
          var sql = "SELECT " + String.join(", ", columns.stream().map(c -> makeSqlName(c.name)).toArray(String[]::new)) +
            " FROM " + makeSqlName(name);
          var rs = executeQueryWithParams(session, sql, filters, sorts, offset, 100);
          var rows = readRows(columns, rs);
          var stats = getStats(session, sql, filters);
          var rowsJson = Json.array();

          for (var row : rows) {
            var rowJson = Json.array();
            for (var value : row) {
              rowJson.add(value);
            }
            rowsJson.add(rowJson);
          }

          var sheet = new Sheet(
            name,
            sql,
            "table",
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
      })
    );

    return minum;
  }

  private SqlType getSqlType(SqlSession session, String sql) throws SQLException {
    if (sql.toLowerCase().startsWith("explain")) {
      return SqlType.EXPLAIN;
    }

    var rs = session.executeQuery("explain (format json) " + sql);
    rs.next();
    var result = Json.parse(rs.getString(1));
    var isModifyingTable = result.asArray().get(0).asObject().get("Plan").asObject().get("Node Type").asString().equals("ModifyTable");

    return isModifyingTable ? SqlType.MODIFY : SqlType.SELECT;
  }

  private Stats getStats(
    SqlSession session,
    String sql,
    Filter[] filters
  ) throws SQLException {
    var whereClause = getWhereClause(filters);
    var rs = session.executeQuery("SELECT COUNT(*) AS numberOfRows FROM (" + sql + ") " + whereClause);
    var tableStats = new Stats(0);
    while (rs.next()) {
      tableStats.numberOfRows = rs.getInt("numberOfRows");
    }
    return tableStats;
  }

  private String makeUpdateValue(Column column, String value) {
    var type = column.type.toLowerCase();
    if (type.contains("timestamp")) {
      return makeSqlLiteral(value) + "::timestamp AT TIME ZONE 'UTC'";
    } else {
      return makeSqlLiteral(value);
    }
  }

  private JsonValue[][] fetchTableRows(
    SqlSession session,
    ArrayList<Column> columns,
    String sql,
    Filter[] filters,
    Sort[] sorts,
    int offset,
    int limit
  ) throws SQLException {

    var rs = executeQueryWithParams(session, sql, filters, sorts, offset, limit);
    return readRows(columns, rs);
  }

  private JsonValue[][] readRows(ArrayList<Column> columns, ResultSet rs) throws SQLException {
    var rows = new ArrayList<JsonValue[]>();
    while (rs.next()) {
      var row = new JsonValue[columns.size()];
      for (int i = 0; i < columns.size(); i++) {
        var column = columns.get(i);
        var value = getJsonValue(rs, i + 1, column);

        column.maxCharacterLength = Math.max(column.maxCharacterLength, getValueLength(value, column));
        row[i] = value;
      }
      rows.add(row);
    }
    return rows.toArray(new JsonValue[0][]);
  }

  private ResultSet executeQueryWithParams(SqlSession session, String sql, Filter[] filters, Sort[] sorts, int offset, int limit) throws SQLException {
    var whereClause = getWhereClause(filters);

    var orderByClause = "";
    if (sorts != null && sorts.length > 0) {
      orderByClause = " ORDER BY " + String.join(", ", Arrays.stream(sorts).map(s -> makeSqlName(s.name) + " " + s.direction).toArray(String[]::new));
    }

    return session.executeQuery(
      "SELECT * FROM (" + sql + ") " +
        whereClause +
        orderByClause +
        " OFFSET " + offset +
        " LIMIT " + limit
    );
  }

  private String getWhereClause(Filter[] filters) {
    var whereClause = "";
    if (filters != null && filters.length > 0) {
      var clauses = Arrays
        .stream(filters)
        .map(s -> {
          var value = s.value == null ? "NULL" : makeSqlLiteral(s.value);
          var op = s.value == null ? "IS" : "=";

          return makeSqlName(s.name) + " " + op + " " + value;
        })
        .toArray(String[]::new);
      whereClause = " WHERE " + String.join(" AND ", clauses);
    }
    return whereClause;
  }

  private static ArrayList<Column> getTableColumns(SqlSession session, String tableName) throws SQLException {
    var rs = session.executeQuery(
      "SELECT c.column_name, c.data_type, c.is_nullable, " +
        "CASE WHEN tc.constraint_type = 'PRIMARY KEY' THEN true ELSE false END as is_primary_key " +
        "FROM information_schema.columns c " +
        "LEFT JOIN information_schema.key_column_usage kcu " +
        "ON c.table_schema = kcu.table_schema " +
        "AND c.table_name = kcu.table_name " +
        "AND c.column_name = kcu.column_name " +
        "LEFT JOIN information_schema.table_constraints tc " +
        "ON kcu.constraint_name = tc.constraint_name " +
        "AND kcu.table_schema = tc.table_schema " +
        "AND kcu.table_name = tc.table_name " +
        "WHERE c.table_schema = 'public' AND c.table_name = " + makeSqlLiteral(tableName) + ";"
    );
    var columns = new ArrayList<>(List.<Column>of());
    while (rs.next()) {
      var name = rs.getString("column_name");
      columns.add(new Column(
        name,
        rs.getString("data_type"),
        name.length(),
        rs.getBoolean("is_primary_key"),
        rs.getString("is_nullable").equals("YES")
      ));
    }
    return columns;
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

    return switch (column.type.toLowerCase()) {
      case "integer", "serial", "bigint", "smallint" -> ("" + value.asLong()).length();
      case "numeric", "decimal", "real", "double precision" -> ("" + value.asDouble()).length();
      case "boolean" -> ("" + value.asBoolean()).length();
      case "timestamp without time zone", "timestamp with time zone" -> timestampLength;
      case "date" -> timestampLength;
      case "time without time zone", "time with time zone" -> timestampLength;
      case "json", "jsonb", "vector" -> Math.min(value.asString().length(), 60);
      default ->
        Arrays.stream(value.asString().split("\n")).max(Comparator.comparingInt(String::length)).get().length();
    };
  }

  private JsonValue getJsonValue(ResultSet rs, int columnIndex, Column column) throws SQLException {
    var value = rs.getObject(columnIndex);
    if (value == null) {
      return Json.NULL;
    }

    return switch (column.type.toLowerCase()) {
      case "integer", "serial", "bigint", "smallint" -> Json.value(rs.getLong(columnIndex));
      case "numeric", "decimal", "real", "double precision" -> Json.value(rs.getDouble(columnIndex));
      case "boolean" -> Json.value(rs.getBoolean(columnIndex));
      case "timestamp without time zone", "timestamp with time zone" ->
        Json.value(rs.getTimestamp(columnIndex).toInstant().toString());
      case "date" -> Json.value(rs.getDate(columnIndex).toString());
      case "time without time zone", "time with time zone" -> Json.value(rs.getTime(columnIndex).toString());
      default -> Json.value(rs.getString(columnIndex));
    };
  }
}
