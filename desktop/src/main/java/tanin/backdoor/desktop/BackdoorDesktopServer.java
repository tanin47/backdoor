package tanin.backdoor.desktop;

import com.renomad.minum.web.*;
import tanin.backdoor.core.BackdoorCoreServer;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;
import tanin.ejwf.MinumBuilder;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class BackdoorDesktopServer extends BackdoorCoreServer {
  private static final Logger logger = Logger.getLogger(BackdoorDesktopServer.class.getName());

  static {
    try (var configFile = BackdoorCoreServer.class.getResourceAsStream("/backdoor_default_logging.properties")) {
      LogManager.getLogManager().readConfiguration(configFile);
      logger.info("The log config (backdoor_default_logging.properties) has been loaded.");
    } catch (IOException e) {
      logger.warning("Could not load the log config file (backdoor_default_logging.properties): " + e.getMessage());
    }
  }

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

      return inputs.endpoint().apply(inputs.clientRequest());
    });

    return minum;
  }

  protected IResponse processIndexPage(IRequest req) throws Exception {
    return Response.htmlOk(
      makeHtml("index.html", null),
      Map.of(
        "Set-Cookie", AUTH_KEY_COOKIE_KEY + "=" + this.authKey + "; Max-Age=86400; Path=/; Secure; HttpOnly"
      )
    );
  }
}
