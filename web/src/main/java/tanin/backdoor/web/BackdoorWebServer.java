package tanin.backdoor.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.ParseException;
import com.renomad.minum.web.*;
import org.altcha.altcha.Altcha;
import tanin.backdoor.core.BackdoorCoreServer;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.EncryptionHelper;
import tanin.backdoor.core.User;
import tanin.backdoor.core.engine.AuthCookie;
import tanin.backdoor.core.engine.Engine;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.security.InvalidKeyException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

import static com.renomad.minum.web.RequestLine.Method.*;

public class BackdoorWebServer extends BackdoorCoreServer {

  static final String CSRF_COOKIE_KEY = "Csrf";
  private static final Logger logger = Logger.getLogger(BackdoorWebServer.class.getName());
  private static final String AUTH_COOKIE_KEY = "backdoor";
  private static final Set<RequestLine.Method> CSRF_READ_METHODS = new HashSet<>(List.of(GET, HEAD, OPTIONS));

  User[] users;
  public String secretKey;
  ThreadLocal<AuthCookie> auth = new ThreadLocal<>();

  BackdoorWebServer(
    DatabaseConfig[] databaseConfigs,
    int port,
    int sslPort,
    User[] users,
    String secretKey
  ) {
    super(databaseConfigs, port, sslPort, null);
    this.secretKey = secretKey;

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
  }

  public User getUserByDatabaseConfig(DatabaseConfig databaseConfig) {
    return Arrays.stream(this.auth.get().users())
      .filter(u -> u.databaseNickname() != null && u.databaseNickname().equals(databaseConfig.nickname))
      .findFirst()
      .orElse(null);
  }

  public static String makeCsrfTokenSetCookieLine(String csrfToken, boolean isSecure) throws Exception {
    var securePortion = isSecure ? " Secure;" : "";
    return CSRF_COOKIE_KEY + "=" + csrfToken + "; Max-Age=86400; Path=/;" + securePortion + " HttpOnly";
  }

  public static String makeAuthCookieValueForUser(User[] users, DatabaseConfig[] adHocDatabaseConfigs, String secretKey, Instant expires) throws Exception {
    return EncryptionHelper.encryptText(
      new AuthCookie(users, adHocDatabaseConfigs, expires).toJson().toString(),
      secretKey
    );
  }

  public static String makeAuthCookie(User[] users, DatabaseConfig[] adHocDatabaseConfigs, String secretKey, Instant expires) throws Exception {
    return AUTH_COOKIE_KEY + "=" + makeAuthCookieValueForUser(users, adHocDatabaseConfigs, secretKey, expires);
  }

  private String extractOrMakeCsrfCookieValue(IRequest request, boolean makeIfMissing) {
    var cookie = extractCookieByKey(CSRF_COOKIE_KEY, request.getHeaders().valueByKey("Cookie"));

    if (cookie == null) {
      if (makeIfMissing) {
        return EncryptionHelper.generateRandomString(16);
      } else {
        return null;
      }
    } else {
      return cookie.substring((CSRF_COOKIE_KEY + "=").length());
    }
  }

  private AuthCookie extractAuthFromCookie(List<String> cookies) throws Exception {
    var cookie = extractCookieByKey(AUTH_COOKIE_KEY, cookies);

    if (cookie == null) {
      return null;
    }

    var base64Credentials = cookie.trim().substring((AUTH_COOKIE_KEY + "=").length()).trim();
    try {
      var credentials = EncryptionHelper.decryptText(base64Credentials, secretKey);
      return AuthCookie.fromJson(Json.parse(credentials));
    } catch (IllegalArgumentException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException |
             ParseException e) {
      return null;
    }
  }

  private static String makeAuthSetCookieLine(User[] users, DatabaseConfig[] adHocDatabaseConfigs, String secretKey, Instant expires, boolean isSecure) throws Exception {
    var securePortion = isSecure ? " Secure;" : "";
    return makeAuthCookie(users, adHocDatabaseConfigs, secretKey, expires) + "; Max-Age=86400; Path=/;" + securePortion + " HttpOnly";
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
        } catch (Engine.InvalidCredentialsException |
                 Engine.OverwritingUserAndCredentialedJdbcConflictedException ignored) {
        } catch (Exception e) {
          throw e;
        }
      }
    }

    return found;
  }

  private static final IResponse REDIRECT_AUTH_RESP = Response.buildResponse(
    StatusLine.StatusCode.CODE_302_FOUND,
    Map.of(
      "Location", "/login"
    ),
    ""
  );
  private static final IResponse POST_AUTH_RESP = Response.buildResponse(
    StatusLine.StatusCode.CODE_401_UNAUTHORIZED,
    Map.of(
      "Content-Type", "application/json"
    ),
    "{}"
  );

  IResponse decideAuthError(IRequest req) {
    if (req.getRequestLine().getMethod() == RequestLine.Method.GET) {
      return REDIRECT_AUTH_RESP;
    } else {
      return POST_AUTH_RESP;
    }
  }

  void checkAuth(IRequest req) throws Exception {
    var path = req.getRequestLine().getPathDetails().getIsolatedPath();

    if (
      path.equals("login") ||
        path.equals("altcha") ||
        path.equals("healthcheck") ||
        path.startsWith("assets/") ||
        path.equals("logout")
    ) {
      // These paths don't need authentication.
      return;
    }

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

  public FullSystem start() throws SQLException {
    var minum = super.start();

    var wf = minum.getWebFramework();

    wf.registerPreHandler((inputs) -> {
      var request = inputs.clientRequest();

      try {
        checkAuth(request);
      } catch (AuthFailureException e) {
        return decideAuthError(request);
      }

      var method = request.getRequestLine().getMethod();
      var csrfTokenHeaders = request.getHeaders().valueByKey("Csrf-Token");
      String csrfToken = null;
      if (csrfTokenHeaders != null) {
        csrfToken = request.getHeaders().valueByKey("Csrf-Token").stream().findFirst().orElse(null);
      }
      var csrfCookieValue = extractOrMakeCsrfCookieValue(request, false);

      var isCsrfValid = CSRF_READ_METHODS.contains(method);
      isCsrfValid = isCsrfValid || (csrfToken != null && csrfToken.equals(csrfCookieValue));

      IResponse response;
      if (isCsrfValid) {
        try {
          response = inputs.endpoint().apply(inputs.clientRequest());
        } catch (EarlyExitException e) {
          return e.response;
        }
      } else {
        logger.info("The CSRF token is invalid. Expected: " + csrfCookieValue + ", Actual: " + csrfToken);
        response = Response.buildResponse(
          StatusLine.StatusCode.CODE_401_UNAUTHORIZED,
          Map.of("Content-Type", "application/json"),
          Json.object()
            .add("errors", Json.array().add("The session has expired. Please refresh the page and try again."))
            .toString()
        );
      }

      return response;
    });


    wf.registerPath(
      GET,
      "altcha",
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
    );

    wf.registerPath(
      GET,
      "healthcheck",
      req -> {
        return Response.buildResponse(StatusLine.StatusCode.CODE_200_OK, Map.of("Content-Type", "text/plain"), "OK Backdoor");
      }
    );

    wf.registerPath(
      GET,
      "login",
      req -> {
        var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");
        var csrfToken = extractOrMakeCsrfCookieValue(req, true);
        return Response.htmlOk(
          makeHtml("login.html", csrfToken, Paradigm.WEB),
          Map.of("Set-Cookie", makeCsrfTokenSetCookieLine(csrfToken, !isLocalHost))
        );
      }
    );

    wf.registerPath(
      POST,
      "login",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var username = json.asObject().get("username").asString();
        var password = json.asObject().get("password").asString();
        var altcha = json.asObject().get("altcha").asString();
        var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");
        checkAltcha(altcha);
        var user = getUser(username, password);

        if (user != null) {
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of(
              "Content-Type", "application/json",
              "Set-Cookie", makeAuthSetCookieLine(new User[]{user}, new DatabaseConfig[0], secretKey, Instant.now().plus(1, ChronoUnit.DAYS), !isLocalHost)
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
    );


    wf.registerPath(
      POST,
      "api/login-additional",
      req -> {
        var json = Json.parse(req.getBody().asString());
        var database = json.asObject().get("database").asString();
        var username = json.asObject().get("username").asString();
        var password = json.asObject().get("password").asString();
        var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");
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
              "Set-Cookie", makeAuthSetCookieLine(users.toArray(new User[0]), auth.get().adHocDatabaseConfigs(), secretKey, Instant.now().plus(1, ChronoUnit.DAYS), !isLocalHost)
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
    );

    wf.registerPath(
      GET,
      "logout",
      req -> {
        var isSecure = req.getRequestLine().queryString().get("secure") != null;
        return Response.buildResponse(
          StatusLine.StatusCode.CODE_302_FOUND,
          Map.of(
            "Set-Cookie", makeAuthSetCookieLine(new User[0], new DatabaseConfig[0], "dontcare", Instant.now(), isSecure),
            // We need double-logout to clear both secure and non-secure cookie.
            "Location", isSecure ? "/" : "/logout?secure=true"
          ),
          ""
        );
      }
    );

    return minum;
  }

  @Override
  protected DatabaseConfig[] getAdHocDatabaseConfigs() {
    return this.auth.get().adHocDatabaseConfigs();
  }

  @Override
  protected IResponse handleAddingValidDataSource(IRequest req, DatabaseConfig adHocDatabaseConfig) throws Exception {
    var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");

    var adHocDatabaseConfigs = new ArrayList<DatabaseConfig>(List.of(this.auth.get().adHocDatabaseConfigs()));
    adHocDatabaseConfigs.add(adHocDatabaseConfig);

    return Response.buildResponse(
      StatusLine.StatusCode.CODE_200_OK,
      Map.of(
        "Content-Type", "application/json",
        "Set-Cookie", makeAuthSetCookieLine(this.auth.get().users(), adHocDatabaseConfigs.toArray(new DatabaseConfig[0]), this.secretKey, Instant.now().plus(1, ChronoUnit.DAYS), !isLocalHost)
      ),
      Json.object().toString()
    );
  }

  @Override
  protected IResponse handleRemovingValidDataSource(IRequest req, DatabaseConfig removedDatabaseConfig) throws Exception {
    var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");

    var adHocDatabaseConfigs = Arrays.stream(this.auth.get().adHocDatabaseConfigs())
      .filter(c -> !c.nickname.equals(removedDatabaseConfig.nickname))
      .toArray(DatabaseConfig[]::new);

    return Response.buildResponse(
      StatusLine.StatusCode.CODE_200_OK,
      Map.of(
        "Content-Type", "application/json",
        "Set-Cookie", makeAuthSetCookieLine(this.auth.get().users(), adHocDatabaseConfigs, this.secretKey, Instant.now().plus(1, ChronoUnit.DAYS), !isLocalHost)
      ),
      Json.object().toString()
    );
  }

  protected IResponse processIndexPage(IRequest req) throws Exception {
    var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");
    var csrfToken = extractOrMakeCsrfCookieValue(req, true);
    return Response.htmlOk(
      makeHtml("index.html", csrfToken, Paradigm.WEB),
      Map.of("Set-Cookie", makeCsrfTokenSetCookieLine(csrfToken, !isLocalHost))
    );
  }
}
