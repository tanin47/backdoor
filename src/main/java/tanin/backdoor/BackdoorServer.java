package tanin.backdoor;


import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.web.*;
import org.altcha.altcha.Altcha;
import org.postgresql.Driver;
import tanin.backdoor.engine.AuthCookie;
import tanin.backdoor.engine.Engine;
import tanin.ejwf.MinumBuilder;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static com.renomad.minum.web.RequestLine.Method.GET;
import static com.renomad.minum.web.RequestLine.Method.POST;

public class BackdoorServer {
  public static class AuthFailureException extends Exception {
  }

  public enum SqlType {
    SELECT,
    ADMINISTRATIVE,
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
  }

  public static void main(String[] args) throws SQLException, URISyntaxException {
    var databaseConfigs = new ArrayList<DatabaseConfig>();
    int port = 0;
    int sslPort = 0;
    var users = new ArrayList<User>();
    var secretKey = EncryptionHelper.generateRandomString(32);

    if (MinumBuilder.IS_LOCAL_DEV) {
//      databaseConfigs.add(new DatabaseConfig("postgres", "postgres://127.0.0.1:5432/backdoor_test", "backdoor_test_user", "test"));
//      databaseConfigs.add(new DatabaseConfig("clickhouse", "jdbc:ch://localhost:8123", "abacus_dev_user", "dev"));
      databaseConfigs.add(new DatabaseConfig("postgres", "postgres://127.0.0.1:5432/backdoor_test", null, null));
      databaseConfigs.add(new DatabaseConfig("clickhouse", "jdbc:ch://localhost:8123?user=backdoor&password=test_ch", null, null));
//      users.add(new User("backdoor_test", "1234"));
      secretKey = "testkey";
      port = 9090;
    }

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-url":
          if (i + 1 < args.length) {
            var urls = args[++i].split(",");

            for (var url : urls) {
              databaseConfigs.add(new DatabaseConfig("database_" + databaseConfigs.size(), url.trim(), null, null));
            }
          }
          break;
        case "-port":
          if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
          break;
        case "-ssl-port":
          if (i + 1 < args.length) sslPort = Integer.parseInt(args[++i]);
          break;
        case "-secret-key":
          if (i + 1 < args.length) secretKey = args[++i];
          break;
        case "-user":
          if (i + 1 < args.length) {
            var pairs = args[++i].split(",");

            for (var pair : pairs) {
              String[] userAndPass = pair.split(":", 2);
              if (userAndPass.length != 2) {
                throw new IllegalArgumentException("Invalid user argument. The format should follow: `user:pass,user2:pass2`");
              }
              users.add(new User(userAndPass[0], userAndPass[1]));
            }
          }
          break;
      }
    }

    if (port == 0) {
      throw new RuntimeException("You must specify the port using `-port <PORT>`");
    }

    var main = new BackdoorServer(
      databaseConfigs.toArray(new DatabaseConfig[0]),
      port,
      sslPort,
      users.toArray(new User[0]),
      secretKey
    );
    var minum = main.start();
    minum.block();
  }

  DatabaseConfig[] databaseConfigs;
  int port;
  int sslPort;
  private FullSystem minum;
  User[] users;
  String secretKey;
  ThreadLocal<AuthCookie> auth = new ThreadLocal<>();

  public BackdoorServer(
    DatabaseConfig[] databaseConfigs,
    int port,
    int sslPort,
    User[] users,
    String secretKey
  ) {
    this.databaseConfigs = databaseConfigs;
    this.port = port;
    this.sslPort = sslPort;

    if (users != null) {
      for (User user : users) {
        if (user.username().isBlank()) {
          throw new IllegalArgumentException("Username cannot be empty");
        }
        if (user.password().isBlank()) {
          throw new IllegalArgumentException("Password cannot be empty");
        }
      }

      this.users = users;
    } else {
      this.users = new User[0];
    }

    this.secretKey = secretKey;
  }

  public static String makeSqlLiteral(String sql) {
    return "'" + sql.replace("'", "''") + "'";
  }

  public static String makeSqlName(String sql) {
    return '"' + sql.replace("\"", "").replace(";", "") + '"';
  }

  String makeHtml(String path) throws IOException {
    return makeHtml(path, null);
  }

  String makeHtml(String path, JsonObject props) throws IOException {
    var layout = TemplateProcessor.buildProcessor(new String(BackdoorServer.class.getResourceAsStream("/html/layout.html").readAllBytes()));
    var targetHtml = TemplateProcessor.buildProcessor(new String(BackdoorServer.class.getResourceAsStream("/html/" + path).readAllBytes()));

    var propsMap = Map.<String, String>of();

    if (props != null) {
      propsMap = Map.of("props", props.toString());
    }

    return layout.renderTemplate(
      Map.of(
        "content", targetHtml.renderTemplate(propsMap),
        "IS_LOCAL_DEV_JSON", Json.value(MinumBuilder.IS_LOCAL_DEV).toString()
      )
    );
  }

  private static final IResponse redirectAuthResp = Response.buildResponse(
    StatusLine.StatusCode.CODE_302_FOUND,
    Map.of(
      "Location", "/login"
    ),
    ""
  );
  private static final IResponse postAuthResp = Response.buildResponse(
    StatusLine.StatusCode.CODE_401_UNAUTHORIZED,
    Map.of(
      "Content-Type", "application/json"
    ),
    "{}"
  );

  IResponse decideAuthError(IRequest req) {
    if (req.getRequestLine().getMethod() == RequestLine.Method.GET) {
      return redirectAuthResp;
    } else {
      return postAuthResp;
    }
  }

  void checkAuth(IRequest req) throws Exception {
    var cookieHeaders = req.getHeaders().valueByKey("Cookie");
    if (cookieHeaders == null || cookieHeaders.isEmpty()) {
      throw new AuthFailureException();
    }

    var auth = extractAuthFromCookie(cookieHeaders);

    if (auth == null) {
      throw new AuthFailureException();
    }

    if (auth.expires().isBefore(Instant.now())) {
      // The encrypted credential expires. Requires another login.
      throw new AuthFailureException();
    }

    for (var user : auth.users()) {
      if (getUser(user.username(), user.password()) != null) {
        this.auth.set(auth);
        return;
      }
    }

    throw new AuthFailureException();
  }

  private static final String authCookieKey = "backdoor";

  public static String makeCookieValueForUser(User[] users, String secretKey, Instant expires) throws Exception {
    return EncryptionHelper.encryptText(
      new AuthCookie(users, expires).toJson().toString(),
      secretKey
    );
  }

  public static String makeSetCookieForUser(User[] users, String secretKey, Instant expires) throws Exception {
    return authCookieKey + "=" + makeCookieValueForUser(users, secretKey, expires);
  }

  private AuthCookie extractAuthFromCookie(List<String> cookies) throws Exception {
    var cookie = Arrays.stream(cookies.getFirst().split(";"))
      .filter(s -> s.trim().startsWith(authCookieKey + "="))
      .findFirst()
      .orElse(null);

    if (cookie == null) {
      return null;
    }

    var base64Credentials = cookie.trim().substring((authCookieKey + "=").length()).trim();
    try {
      var credentials = EncryptionHelper.decryptText(base64Credentials, secretKey);
      return AuthCookie.fromJson(Json.parse(credentials));
    } catch (IllegalArgumentException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException |
             ParseException e) {
      return null;
    }
  }

  private User getUser(String username, String password) throws Exception {
    User found = null;

    if (users != null) {
      found = Arrays.stream(users)
        .filter(u -> u.username().equals(username) && u.password().equals(password))
        .findFirst()
        .orElse(null);
    }

    if (found == null) {
      for (var databaseConfig : databaseConfigs) {
        var potentialDatabaseUser = new User(username, password, databaseConfig.nickname);
        try (var engine = Engine.createEngine(databaseConfig, potentialDatabaseUser)) {
          var rs = engine.executeQuery("SELECT 123");
          rs.next();
          if (rs.getInt(1) == 123) {
            return potentialDatabaseUser;
          }

        } catch (Engine.InvalidCredentialsException e) {
          found = null;
        } catch (Exception e) {
          throw e;
        }
      }
    }

    return found;
  }

  ThrowingFunction<IRequest, IResponse> handleEndpoint(Boolean requireAuth, ThrowingFunction<IRequest, IResponse> handler) {
    return req -> {
      try {
        try {
          checkAuth(req);
        } catch (AuthFailureException ex) {
          if (requireAuth) {
            throw new EarlyExitException(decideAuthError(req));
          }
        } catch (Exception ex) {
          throw ex;
        }

        return handler.apply(req);
      } catch (IllegalArgumentException | SQLException e) {
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
      } catch (EarlyExitException e) {
        return e.response;
      } finally {
        auth.set(null);
      }
    };
  }

  ThrowingFunction<IRequest, IResponse> handleEndpoint(ThrowingFunction<IRequest, IResponse> handler) {
    return handleEndpoint(true, handler);
  }

  Engine makeEngine(String databaseNickname) throws SQLException, URISyntaxException, Engine.InvalidCredentialsException {
    var databaseConfig = Arrays.stream(databaseConfigs).filter(d -> d.nickname.equals(databaseNickname)).findFirst().orElse(null);

    if (databaseConfig == null) {
      throw new IllegalArgumentException("Database not found: " + databaseNickname);
    }

    var user = Arrays.stream(auth.get().users()).filter(u -> databaseNickname.equals(u.databaseNickname())).findFirst().orElse(null);

    return Engine.createEngine(databaseConfig, user);
  }

  public FullSystem start() throws SQLException {
    minum = MinumBuilder.start(this.port, this.sslPort);
    var wf = minum.getWebFramework();

    DriverManager.registerDriver(new Driver());

    wf.registerPath(
      GET,
      "altcha",
      handleEndpoint(
        false,
        req -> {
          var options = new Altcha.ChallengeOptions();
          options.secureRandomNumber = true;
          options.maxNumber = 1_000_000L;
          options.saltLength = 12L;
          options.setExpiresInSeconds(2 * 60);
          options.hmacKey = secretKey;
          var challenge = Altcha.createChallenge(options);
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of(
              "Content-Type", "application/json"
            ),
            Json
              .object()
              .add("algorithm", challenge.algorithm)
              .add("challenge", challenge.challenge)
              .add("maxnumber", challenge.maxnumber)
              .add("salt", challenge.salt)
              .add("signature", challenge.signature)
              .toString()
          );
        }
      )
    );

    wf.registerPath(
      GET,
      "login",
      handleEndpoint(
        false,
        req -> {
          return Response.htmlOk(makeHtml("login.html"));
        }
      )
    );

    wf.registerPath(
      POST,
      "login",
      handleEndpoint(
        false,
        req -> {
          var json = Json.parse(req.getBody().asString());
          var username = json.asObject().get("username").asString();
          var password = json.asObject().get("password").asString();
          var altcha = json.asObject().get("altcha").asString();
          checkAltcha(altcha);
          var user = getUser(username, password);

          if (user != null) {
            return Response.buildResponse(
              StatusLine.StatusCode.CODE_200_OK,
              Map.of(
                "Content-Type", "application/json",
                "Set-Cookie", makeSetCookieForUser(new User[]{user}, secretKey, Instant.now().plus(1, ChronoUnit.DAYS)) + "; Max-Age=86400; Secure; HttpOnly"
              ),
              Json.object().toString()
            );
          } else {
            return Response.buildResponse(
              StatusLine.StatusCode.CODE_400_BAD_REQUEST,
              Map.of("Content-Type", "application/json"),
              Json
                .object()
                .add("errors", Json.array().add("The username or password is invalid."))
                .toString()
            );
          }
        }
      )
    );


    wf.registerPath(
      POST,
      "api/login-additional",
      handleEndpoint(
        req -> {
          var json = Json.parse(req.getBody().asString());
          var database = json.asObject().get("database").asString();
          var username = json.asObject().get("username").asString();
          var password = json.asObject().get("password").asString();
          var altcha = json.asObject().get("altcha").asString();
          checkAltcha(altcha);

          var databaseConfig = Arrays.stream(databaseConfigs).filter(d -> d.nickname.equals(database)).findFirst().orElse(null);
          var potentialUser = new User(username, password, database);

          try (var ignored = Engine.createEngine(databaseConfig, potentialUser)) {
            var users = new ArrayList<>(List.of(auth.get().users()));
            users.add(potentialUser);

            return Response.buildResponse(
              StatusLine.StatusCode.CODE_200_OK,
              Map.of(
                "Content-Type", "application/json",
                "Set-Cookie", makeSetCookieForUser(users.toArray(new User[0]), secretKey, Instant.now().plus(1, ChronoUnit.DAYS)) + "; Max-Age=86400; Secure; HttpOnly"
              ),
              Json.object().toString()
            );
          } catch (Engine.InvalidCredentialsException e) {
            return Response.buildResponse(
              StatusLine.StatusCode.CODE_400_BAD_REQUEST,
              Map.of("Content-Type", "application/json"),
              Json
                .object()
                .add("errors", Json.array().add("The username or password is invalid."))
                .toString()
            );
          }
        }
      )
    );

    wf.registerPath(
      GET,
      "logout",
      handleEndpoint(
        false,
        req -> {
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_302_FOUND,
            Map.of(
              "Set-Cookie", makeSetCookieForUser(new User[0], "dontcare", Instant.now()) + "; Max-Age=0; Secure; HttpOnly",
              "Location", "/login"
            ),
            ""
          );
        }
      )
    );

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
      )
    );

    wf.registerPath(
      POST,
      "api/delete-row",
      handleEndpoint(req -> {
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
      })
    );

    wf.registerPath(
      POST,
      "api/edit-field",
      handleEndpoint(req -> {
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
      })
    );

    wf.registerPath(
      POST,
      "api/drop-table",
      handleEndpoint(req -> {
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
      })
    );

    wf.registerPath(
      POST,
      "api/rename-table",
      handleEndpoint(req -> {
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
      })
    );


    wf.registerPath(
      POST,
      "api/load-query",
      handleEndpoint(req -> {
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
      })
    );

    wf.registerPath(
      POST,
      "api/load-table",
      handleEndpoint(req -> {
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
      })
    );

    return minum;
  }

  private void checkAltcha(String altcha) {
    var isValid = false;

    try {
      isValid = Altcha.verifySolution(altcha, secretKey, true);
    } catch (Exception e) {
      logger.info("Altcha raised an exception while verifying the captcha.");
    }

    if (!isValid) {
      throw new EarlyExitException(Response.buildResponse(
        StatusLine.StatusCode.CODE_400_BAD_REQUEST,
        Map.of("Content-Type", "application/json"),
        Json
          .object()
          .add("errors", Json.array().add("The captcha is invalid. Please check \"I'm not a robot\" again."))
          .toString()
      ));
    }
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
