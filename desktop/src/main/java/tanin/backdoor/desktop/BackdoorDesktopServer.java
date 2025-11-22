package tanin.backdoor.desktop;

import com.eclipsesource.json.Json;
import com.renomad.minum.web.*;
import tanin.backdoor.core.BackdoorCoreServer;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;
import tanin.ejwf.MinumBuilder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class BackdoorDesktopServer extends BackdoorCoreServer {
  private static final Logger logger = Logger.getLogger(BackdoorDesktopServer.class.getName());

  public String authKey;

  BackdoorDesktopServer(
    DatabaseConfig[] databaseConfigs,
    int port,
    int sslPort,
    String authKey,
    MinumBuilder.KeyStore keyStore
  ) {
    super(databaseConfigs, port, sslPort, keyStore);
    this.authKey = authKey;
  }

  public User getUserByDatabaseConfig(DatabaseConfig databaseConfig) {
    return null;
  }

  public static final String AUTH_KEY_COOKIE_KEY = "Auth";

  public FullSystem start() throws SQLException {
    var minum = super.start();

    var wf = minum.getWebFramework();

    wf.registerPreHandler((inputs) -> {
      var request = inputs.clientRequest();
      var authKeyFromQueryString = request.getRequestLine().getPathDetails().getQueryString().get("authKey");
      var authKeyFromCookie = Optional.ofNullable(extractCookieByKey(AUTH_KEY_COOKIE_KEY, request.getHeaders().valueByKey("Cookie")))
        .map(v -> v.substring((AUTH_KEY_COOKIE_KEY + "=").length())).orElse(null);

      if (this.authKey.equals(authKeyFromQueryString) || this.authKey.equals(authKeyFromCookie)) {
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
      } catch (Throwable e) {
        logger.log(Level.SEVERE, request.getRequestLine().getMethod() + " " + request.getRequestLine().getPathDetails().getIsolatedPath() + " raised an error.", e);
        throw e;
      }
    });

    return minum;
  }

  @Override
  protected DatabaseConfig[] getAdHocDatabaseConfigs() throws BackingStoreException {
    var preferences = Preferences.userNodeForPackage(BackdoorDesktopServer.class);
    var configs = new ArrayList<DatabaseConfig>();

    for (String key : preferences.keys()) {
      if (key.endsWith(".jdbcUrl")) {
        var nickname = key.substring(0, key.length() - ".jdbcUrl".length());
        var jdbcUrl = preferences.get(nickname + ".jdbcUrl", null);
        var username = preferences.get(nickname + ".username", null);
        var password = preferences.get(nickname + ".password", null);

        if (jdbcUrl != null) {
          configs.add(new DatabaseConfig(nickname, jdbcUrl, username, password));
        }
      }
    }

    return configs.toArray(new DatabaseConfig[0]);
  }

  @Override
  protected IResponse handleAddingValidDataSource(IRequest req, DatabaseConfig adHocDatabaseConfig) throws Exception {
    var preferences = Preferences.userNodeForPackage(BackdoorDesktopServer.class);
    preferences.put(adHocDatabaseConfig.nickname + ".jdbcUrl", adHocDatabaseConfig.jdbcUrl);
    preferences.put(adHocDatabaseConfig.nickname + ".username", adHocDatabaseConfig.username);
    preferences.put(adHocDatabaseConfig.nickname + ".password", adHocDatabaseConfig.password);
    preferences.flush();
    return Response.buildResponse(
      StatusLine.StatusCode.CODE_200_OK,
      Map.of("Content-Type", "application/json"),
      Json.object().toString()
    );
  }

  protected IResponse processIndexPage(IRequest req) throws Exception {
    return Response.htmlOk(
      makeHtml("index.html", null, Paradigm.DESKTOP),
      Map.of(
        "Set-Cookie", AUTH_KEY_COOKIE_KEY + "=" + this.authKey + "; Max-Age=86400; Path=/; Secure; HttpOnly"
      )
    );
  }
}
