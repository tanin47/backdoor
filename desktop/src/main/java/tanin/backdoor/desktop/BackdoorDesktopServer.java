package tanin.backdoor.desktop;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import com.renomad.minum.web.*;
import tanin.backdoor.core.BackdoorCoreServer;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;
import tanin.backdoor.desktop.engine.EngineProvider;
import tanin.ejwf.MinumBuilder;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import static com.renomad.minum.web.RequestLine.Method.POST;

public class BackdoorDesktopServer extends BackdoorCoreServer {


  private static final Logger logger = Logger.getLogger(BackdoorDesktopServer.class.getName());
  private static final String VERSION;

  static {
    var properties = new Properties();
    try (var stream = BackdoorDesktopServer.class.getResourceAsStream("/version.properties")) {
      properties.load(stream);
      VERSION = properties.getProperty("version");
    } catch (Exception e) {
      logger.warning("Failed to load version.properties: " + e.getMessage());
      throw new RuntimeException("Failed to load version.properties", e);
    }
  }

  public String authKey;

  public Browser browser;

  BackdoorDesktopServer(
    DatabaseConfig[] databaseConfigs,
    int sslPort,
    String authKey,
    MinumBuilder.KeyStore keyStore
  ) {
    super(databaseConfigs, -1, sslPort, keyStore);
    this.authKey = authKey;
    this.engineProvider = new EngineProvider();
  }

  public User getUserByDatabaseConfig(DatabaseConfig databaseConfig) {
    return null;
  }

  public static final String AUTH_KEY_COOKIE_KEY = "Auth";

  private Browser.OnFileSelected onFileSelected = null;

  public FullSystem start() throws SQLException, NoSuchAlgorithmException, KeyManagementException {
    var minum = super.start();

    var wf = minum.getWebFramework();

    wf.registerPreHandler((inputs) -> {
      var request = inputs.clientRequest();
      var authKeyFromQueryString = request.getRequestLine().getPathDetails().getQueryString().get("authKey");
      var authKeyFromCookie = Optional.ofNullable(extractCookieByKey(AUTH_KEY_COOKIE_KEY, request.getHeaders().valueByKey("Cookie")))
        .map(v -> v.substring((AUTH_KEY_COOKIE_KEY + "=").length())).orElse(null);

      if (
        this.authKey.equals(authKeyFromQueryString) ||
          this.authKey.equals(authKeyFromCookie) ||
          request.getRequestLine().getPathDetails().getIsolatedPath().equals("__webpack_hmr")
      ) {
        // ok
      } else {
        logger.info("The auth key is invalid. Got: " + authKeyFromQueryString + " and " + authKeyFromCookie);
        return Response.buildResponse(
          StatusLine.StatusCode.CODE_401_UNAUTHORIZED,
          Map.of("Content-Type", "text/plain"),
          "The auth key is invalid."
        );
      }

      try {
        logger.info(request.getRequestLine().getMethod() + " " + request.getRequestLine().getPathDetails().getIsolatedPath());
        var response = inputs.endpoint().apply(inputs.clientRequest());
        logger.info(request.getRequestLine().getMethod() + " " + request.getRequestLine().getPathDetails().getIsolatedPath() + " " + response.getStatusCode());
        return response;
      } catch (SQLException e) {
        return Response.buildResponse(
          StatusLine.StatusCode.CODE_400_BAD_REQUEST,
          Map.of("Content-Type", "application/json"),
          Json.object()
            .add("errors", Json.array(e.getMessage()))
            .toString()
        );
      } catch (Throwable e) {
        logger.log(Level.SEVERE, request.getRequestLine().getMethod() + " " + request.getRequestLine().getPathDetails().getIsolatedPath() + " raised an error.", e);
        throw e;
      }
    });

    wf.registerPath(
      POST,
      "api/save-sql-history-entry",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var sql = json.asObject().get("sql").asString();
        var database = json.asObject().get("database").asString();
        var executedAt = json.asObject().get("executedAt").asLong();

        SqlHistoryManager.addEntry(new SqlHistoryEntry(sql, database, executedAt));

        return Response.buildResponse(
          StatusLine.StatusCode.CODE_200_OK,
          Map.of("Content-Type", "application/json"),
          Json.object().toString()
        );
      }
    );

    wf.registerPath(
      POST,
      "api/get-sql-history-entries",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var keyword = Optional.ofNullable(json.asObject().get("keyword")).filter(JsonValue::isString).map(JsonValue::asString).orElse(null);

        var entries = SqlHistoryManager.getEntries(keyword);

        var entriesJson = Json.array();
        for (var entry : entries) {
          entriesJson.add(entry.toJson());
        }

        return Response.buildResponse(
          StatusLine.StatusCode.CODE_200_OK,
          Map.of("Content-Type", "application/json"),
          Json.object().add("entries", entriesJson).toString()
        );
      }
    );

    // We cannot use webview_bind due to the synchronous nature of it. The callback has to be blocked.
    // However, if the callback is blocked, then the file dialog which needs to run on the main thread wouldn't show.
    wf.registerPath(
      POST,
      "select-file",
      req -> {
        var json = Json.parse(req.getBody().asString());
        boolean isSaved = json.asObject().get("isSaved").asBoolean();
        onFileSelected = filePath -> {
          System.out.println("Opening file: " + filePath);

          browser.eval("window.triggerFileSelected(" + Json.object().add("filePath", filePath).toString() + ")");
        };

        browser.openFileDialog(isSaved, onFileSelected);

        return Response.buildResponse(
          StatusLine.StatusCode.CODE_200_OK,
          Map.of("Content-Type", "application/json"),
          Json.object().toString()
        );
      }
    );

    return minum;
  }

  private static final String PREFERENCES_API_DATABASE_CONFIG_KEY = "DATABASE_CONFIGS";

  @Override
  protected DatabaseConfig[] getAdHocDatabaseConfigs() {
    var preferences = Preferences.userNodeForPackage(MinumBuilder.MODE.getClass());
    var databaseConfigsJson = preferences.get(PREFERENCES_API_DATABASE_CONFIG_KEY, null);

    if (databaseConfigsJson == null) {
      return new DatabaseConfig[0];
    }

    var json = Json.parse(databaseConfigsJson);
    var configs = new ArrayList<DatabaseConfig>();

    json.asArray().forEach(c -> {
      var config = DatabaseConfig.parse(c);
      if (config != null) {
        configs.add(config);
      }
    });

    return configs.toArray(new DatabaseConfig[0]);
  }

  @Override
  protected IResponse handleUpdatingAdHocDataSourceConfigs(IRequest req, DatabaseConfig[] allAdHocDatabaseConfigs) throws Exception {
    var preferences = Preferences.userNodeForPackage(MinumBuilder.MODE.getClass());
    var configsJson = Json.array();
    for (var config : allAdHocDatabaseConfigs) {
      configsJson.add(config.toJson());
    }
    preferences.put(
      PREFERENCES_API_DATABASE_CONFIG_KEY,
      configsJson.toString()
    );
    preferences.flush();

    return Response.buildResponse(
      StatusLine.StatusCode.CODE_200_OK,
      Map.of("Content-Type", "application/json"),
      Json.object().toString()
    );
  }

  protected IResponse processIndexPage(IRequest req) throws Exception {
    return Response.htmlOk(
      makeHtml("index.html", null, Paradigm.DESKTOP, VERSION),
      Map.of(
        "Set-Cookie", AUTH_KEY_COOKIE_KEY + "=" + this.authKey + "; Max-Age=86400; Path=/; Secure; HttpOnly"
      )
    );
  }
}
