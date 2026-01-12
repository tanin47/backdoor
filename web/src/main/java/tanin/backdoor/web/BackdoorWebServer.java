package tanin.backdoor.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.ParseException;
import com.renomad.minum.web.*;
import org.altcha.altcha.Altcha;
import org.postgresql.util.PSQLException;
import tanin.backdoor.core.*;
import tanin.backdoor.core.engine.Engine;
import tanin.jmigrate.JMigrate;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static com.renomad.minum.web.RequestLine.Method.*;

public class BackdoorWebServer extends BackdoorCoreServer {
  private static final Logger logger = Logger.getLogger(BackdoorWebServer.class.getName());

  static {
    try (var configFile = BackdoorWebServer.class.getResourceAsStream("/logging.properties")) {
      LogManager.getLogManager().readConfiguration(configFile);
      logger.info("The log config (logging.properties) has been loaded.");
    } catch (IOException e) {
      logger.warning("Could not load the log config file (logging.properties): " + e.getMessage());
    }
  }

  static final String CSRF_COOKIE_KEY = "Csrf";
  private static final String AUTH_COOKIE_KEY = "backdoor";
  private static final Set<RequestLine.Method> CSRF_READ_METHODS = new HashSet<>(List.of(GET, HEAD, OPTIONS));
  private static final String VERSION;
  public static Properties SENTRY_PROPERTIES;

  static {
    var properties = new Properties();
    try (var stream = BackdoorWebServer.class.getResourceAsStream("/version.properties")) {
      properties.load(stream);
      VERSION = properties.getProperty("version");
    } catch (Exception e) {
      logger.warning("Failed to load version.properties: " + e.getMessage());
      throw new RuntimeException("Failed to load version.properties", e);
    }

    try (var stream = BackdoorWebServer.class.getResourceAsStream("/sentry.properties")) {
      SENTRY_PROPERTIES = new Properties();
      SENTRY_PROPERTIES.load(stream);
    } catch (Exception e) {
      logger.warning("Failed to load sentry.properties: " + e.getMessage());
      throw new RuntimeException("Failed to load sentry.properties", e);
    }
  }


  public SourceCodeUser[] sourceCodeUsers;
  public String secretKey;
  public String backdoorDatabaseJdbcUrl;
  private DynamicUserService dynamicUserService;
  private final GlobalSettings globalSettings;
  ThreadLocal<AuthInfo> auth = new ThreadLocal<>();

  BackdoorWebServer(
    DatabaseConfig[] databaseConfigs,
    int port,
    int sslPort,
    SourceCodeUser[] sourceCodeUsers,
    String secretKey,
    String backdoorDatabaseJdbcUrl,
    String analyticsName
  ) {
    super(
      databaseConfigs,
      port,
      sslPort,
      null,
      SENTRY_PROPERTIES.getProperty("dsn"),
      SENTRY_PROPERTIES.getProperty("release")
    );
    this.secretKey = secretKey;
    this.backdoorDatabaseJdbcUrl = backdoorDatabaseJdbcUrl;
    if (backdoorDatabaseJdbcUrl != null) {
      this.dynamicUserService = new DynamicUserService(this.backdoorDatabaseJdbcUrl);
    }
    this.globalSettings = new GlobalSettings(backdoorDatabaseJdbcUrl != null, analyticsName);

    if (sourceCodeUsers != null) {
      for (SourceCodeUser user : sourceCodeUsers) {
        if (user.username().isBlank()) {
          throw new IllegalArgumentException("Username cannot be empty");
        }
        if (user.password().isBlank()) {
          throw new IllegalArgumentException("Password cannot be empty");
        }
      }

      this.sourceCodeUsers = sourceCodeUsers;
    } else {
      this.sourceCodeUsers = new SourceCodeUser[0];
    }
  }

  public DatabaseUser getUserByDatabaseConfig(DatabaseConfig databaseConfig) {
    return Arrays.stream(this.auth.get().databaseUsers())
      .filter(u -> u.databaseNickname() != null && u.databaseNickname().equals(databaseConfig.nickname))
      .findFirst()
      .orElse(null);
  }

  public static String makeCsrfTokenSetCookieLine(String csrfToken, boolean isSecure) throws Exception {
    var securePortion = isSecure ? " Secure;" : "";
    return CSRF_COOKIE_KEY + "=" + csrfToken + "; Max-Age=86400; Path=/;" + securePortion + " HttpOnly";
  }

  public static String makeAuthCookieValueForUser(
    DynamicUser dynamicUser,
    SourceCodeUser sourceCodeUser,
    DatabaseUser[] databaseUsers,
    DatabaseConfig[] adHocDatabaseConfigs,
    String secretKey,
    Instant expires
  ) throws Exception {
    return EncryptionHelper.encryptText(
      new AuthCookie(
        dynamicUser == null ? null : dynamicUser.id(),
        sourceCodeUser == null ? null : sourceCodeUser.username(),
        databaseUsers,
        adHocDatabaseConfigs,
        expires
      ).toJson().toString(),
      secretKey
    );
  }

  public static String makeAuthCookie(
    DynamicUser dynamicUser,
    SourceCodeUser sourceCodeUser,
    DatabaseUser[] databaseUsers,
    DatabaseConfig[] adHocDatabaseConfigs,
    String secretKey,
    Instant expires
  ) throws Exception {
    return AUTH_COOKIE_KEY + "=" + makeAuthCookieValueForUser(dynamicUser, sourceCodeUser, databaseUsers, adHocDatabaseConfigs, secretKey, expires);
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

  private static String makeAuthSetCookieLine(DynamicUser dynamicUser, SourceCodeUser sourceCodeUser, DatabaseUser[] databaseUsers, DatabaseConfig[] adHocDatabaseConfigs, String secretKey, Instant expires, boolean isSecure) throws Exception {
    var securePortion = isSecure ? " Secure;" : "";
    return makeAuthCookie(dynamicUser, sourceCodeUser, databaseUsers, adHocDatabaseConfigs, secretKey, expires) + "; Max-Age=86400; Path=/;" + securePortion + " HttpOnly";
  }

  public record AuthenticatedUser(
    DynamicUser dynamicUser,
    SourceCodeUser sourceCodeUser,
    DatabaseUser databaseUser
  ) {
  }

  private boolean authenticateDatabaseUser(DatabaseConfig databaseConfig, DatabaseUser potentialDatabaseUser) throws Exception {
    var isValid = new AtomicBoolean(false);
    // TODO: We need to first test whether the database config is valid in itself. If it is, we skip it.
    try (var engine = engineProvider.createEngine(databaseConfig, potentialDatabaseUser)) {
      engine.executeQuery(
        "SELECT 123",
        rs -> {
          rs.next();
          if (rs.getInt(1) == 123) {
            isValid.set(true);
          }
        }
      );
    } catch (Engine.InvalidCredentialsException |
             Engine.OverwritingUserAndCredentialedJdbcConflictedException ignored) {
    }
    return isValid.get();
  }

  private AuthenticatedUser authenticateUser(String username, String password) throws Exception {
    if (dynamicUserService != null) {
      var user = dynamicUserService.getByUsername(username);

      if (user != null) {
        if (PasswordHasher.verifyPassword(password, user.hashedPassword())) {
          return new AuthenticatedUser(user, null, null);
        } else {
          return null;
        }
      }
    }


    if (sourceCodeUsers != null) {
      var found = Arrays.stream(sourceCodeUsers)
        .filter(u -> u.username().equals(username) && u.password().equals(password))
        .findFirst()
        .orElse(null);

      if (found != null) {
        return new AuthenticatedUser(null, found, null);
      }
    }

    for (var databaseConfig : databaseConfigs) {
      var potentialDatabaseUser = new DatabaseUser(username, password, databaseConfig.nickname);
      if (authenticateDatabaseUser(databaseConfig, potentialDatabaseUser)) {
        return new AuthenticatedUser(null, null, potentialDatabaseUser);
      }
    }

    return null;
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

  void checkAuth(IRequest req) throws Exception {
    var path = req.getRequestLine().getPathDetails().getIsolatedPath();

    if (
      path.equals("login") ||
        path.equals("altcha") ||
        path.equals("healthcheck") ||
        path.startsWith("assets/") ||
        path.equals("logout") ||
        path.equals("__webpack_hmr")
    ) {
      // These paths don't need authentication.
      return;
    }

    var cookieHeaders = req.getHeaders().valueByKey("Cookie");
    if (cookieHeaders == null || cookieHeaders.isEmpty()) {
      throw new AuthFailureException();
    }

    // TODO: Auth that we use in the server and the auth stored in the cookie don't need to be identical.
    // The auth stored in the cookie should simply store ID and we fetch it from the backend for full info.
    var authCookie = extractAuthFromCookie(cookieHeaders);

    if (authCookie == null) {
      throw new AuthFailureException();
    }

    var backdoorUser = authCookie.backdoorUserId() == null ? null : dynamicUserService.getById(authCookie.backdoorUserId());
    var commandLineUser = authCookie.commandLineUserUsername() == null ? null : Arrays.stream(sourceCodeUsers).filter(u -> u.username().equals(authCookie.commandLineUserUsername())).findFirst().orElse(null);

    var auth = new AuthInfo(
      backdoorUser,
      commandLineUser,
      authCookie.databaseUsers(),
      authCookie.adHocDatabaseConfigs(),
      authCookie.expires()
    );

    if (auth.expires().isBefore(Instant.now())) {
      // The encrypted credential expires. Requires another login.
      throw new AuthFailureException();
    }

    var valid = false;
    if (auth.dynamicUser() != null) {
      valid = true;
    }

    if (auth.sourceCodeUser() != null) {
      valid = true;
    }

    if (!valid) {
      for (var databaseUser : auth.databaseUsers()) {
        if (authenticateUser(databaseUser.username(), databaseUser.password()) != null) {
          valid = true;
          break;
        }
      }
    }

    if (valid) {
      this.auth.set(auth);
    } else {
      throw new AuthFailureException();
    }

    // Force set a permanent password if the user has a temporary password.
    if (
      req.getRequestLine().getMethod() == RequestLine.Method.GET &&
        auth.dynamicUser() != null &&
        auth.dynamicUser().passwordExpiredAt() != null
    ) {
      var csrfToken = extractOrMakeCsrfCookieValue(req, true);
      var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");
      throw new EarlyExitException(
        Response.htmlOk(
          makeHtml("resetPassword.html", csrfToken, Paradigm.WEB, VERSION, this.auth.get() != null ? this.auth.get().toLoggedInUser() : null, globalSettings),
          Map.of("Set-Cookie", makeCsrfTokenSetCookieLine(csrfToken, !isLocalHost))
        )
      );
    }

    if (req.getRequestLine().getPathDetails().getIsolatedPath().startsWith("admin/user")) {
      var loggedInUser = auth.toLoggedInUser();
      if (loggedInUser == null || !loggedInUser.canManageDynamicUsers()) {
        throw new EarlyExitException(Response.buildResponse(
          StatusLine.StatusCode.CODE_401_UNAUTHORIZED,
          Map.of("Content-Type", "text/plain"),
          "You are not allowed to manage dynamic users."
        ));
      }
    }
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

  public FullSystem start() throws Exception {
    var minum = super.start();

    var wf = minum.getWebFramework();

    wf.registerPreHandler((inputs) -> {
      var request = inputs.clientRequest();

      try {
        checkAuth(request);
      } catch (AuthFailureException e) {
        if (request.getRequestLine().getMethod() == RequestLine.Method.GET) {
          return REDIRECT_AUTH_RESP;
        } else {
          return POST_AUTH_RESP;
        }
      } catch (EarlyExitException e) {
        logger.log(Level.WARNING, request.getRequestLine().getMethod() + " " + request.getRequestLine().getPathDetails().getIsolatedPath() + " raised an error.", e);
        return e.response;
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
        } catch (SQLException e) {
          logger.log(Level.WARNING, request.getRequestLine().getMethod() + " " + request.getRequestLine().getPathDetails().getIsolatedPath() + " raised an error.", e);
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_400_BAD_REQUEST,
            Map.of("Content-Type", "application/json"),
            Json.object()
              .add("errors", Json.array(e.getMessage()))
              .toString()
          );
        } catch (EarlyExitException e) {
          logger.log(Level.WARNING, request.getRequestLine().getMethod() + " " + request.getRequestLine().getPathDetails().getIsolatedPath() + " raised an error.", e);
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
          makeHtml("login.html", csrfToken, Paradigm.WEB, VERSION, auth.get() != null ? auth.get().toLoggedInUser() : null, globalSettings),
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
        var authenticatedUser = authenticateUser(username, password);

        if (authenticatedUser != null) {
          if (
            authenticatedUser.dynamicUser != null &&
              authenticatedUser.dynamicUser.passwordExpiredAt() != null &&
              authenticatedUser.dynamicUser.passwordExpiredAt().isBefore(Instant.now())
          ) {
            return Response.buildResponse(
              StatusLine.StatusCode.CODE_400_BAD_REQUEST,
              Map.of("Content-Type", "application/json"),
              Json
                .object()
                .add("errors", Json.array().add("Your temporary password has expired. Please contact your administrator to issue a new temporary password."))
                .toString()
            );
          }

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of(
              "Content-Type", "application/json",
              "Set-Cookie", makeAuthSetCookieLine(
                authenticatedUser.dynamicUser,
                authenticatedUser.sourceCodeUser,
                authenticatedUser.databaseUser != null ? new DatabaseUser[]{authenticatedUser.databaseUser} : null,
                new DatabaseConfig[0],
                secretKey,
                Instant.now().plus(1, ChronoUnit.DAYS),
                !isLocalHost
              )
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

        if (databaseConfig == null) {
          return Response.buildResponse(
            StatusLine.StatusCode.CODE_400_BAD_REQUEST,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("errors", Json.array().add("The database name is invalid. Please reload the page and try again."))
              .toString()
          );
        }

        var potentialUser = new DatabaseUser(username, password, database);

        try (var ignored = engineProvider.createEngine(databaseConfig, potentialUser)) {
          var users = new ArrayList<>(List.of(auth.get().databaseUsers()));
          users.add(potentialUser);

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of(
              "Content-Type", "application/json",
              "Set-Cookie", makeAuthSetCookieLine(
                auth.get().dynamicUser(),
                auth.get().sourceCodeUser(),
                users.toArray(new DatabaseUser[0]),
                auth.get().adHocDatabaseConfigs(),
                secretKey,
                Instant.now().plus(1, ChronoUnit.DAYS),
                !isLocalHost
              )
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
            "Set-Cookie", makeAuthSetCookieLine(null, null, new DatabaseUser[0], new DatabaseConfig[0], "dontcare", Instant.now(), isSecure),
            // We need double-logout to clear both secure and non-secure cookie.
            "Location", isSecure ? "/" : "/logout?secure=true"
          ),
          ""
        );
      }
    );


    if (backdoorDatabaseJdbcUrl != null) {
      JMigrate.migrate(backdoorDatabaseJdbcUrl, BackdoorWebServer.class, "/sql");

      wf.registerPath(
        GET,
        "admin/user",
        req -> {
          var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");
          var csrfToken = extractOrMakeCsrfCookieValue(req, true);
          return Response.htmlOk(
            makeHtml("admin/user/index.html", csrfToken, Paradigm.WEB, VERSION, auth.get() != null ? auth.get().toLoggedInUser() : null, globalSettings),
            Map.of("Set-Cookie", makeCsrfTokenSetCookieLine(csrfToken, !isLocalHost))
          );
        }
      );

      wf.registerPath(
        POST,
        "admin/user/load",
        req -> {
          var users = dynamicUserService.getAll();
          var usersJson = Json.array();
          for (var user : users) {
            usersJson.add(user.toJson());
          }

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("users", usersJson)
              .toString()
          );
        }
      );

      wf.registerPath(
        POST,
        "admin/user/create",
        req -> {
          var json = Json.parse(req.getBody().asString());
          var username = json.asObject().get("username").asString().trim();
          var tempPassword = json.asObject().get("tempPassword").asString();

          if (username.isEmpty()) {
            throw new EarlyExitException(Response.buildResponse(
              StatusLine.StatusCode.CODE_400_BAD_REQUEST,
              Map.of("Content-Type", "application/json"),
              Json
                .object()
                .add("errors", Json.array().add("The username must not be empty."))
                .toString()
            ));
          } else if (tempPassword.length() < 6) {
            throw new EarlyExitException(Response.buildResponse(
              StatusLine.StatusCode.CODE_400_BAD_REQUEST,
              Map.of("Content-Type", "application/json"),
              Json
                .object()
                .add("errors", Json.array().add("The password must be at least 6 characters long."))
                .toString()
            ));
          }

          try {
            dynamicUserService.create(username, tempPassword, Instant.now().plus(1, ChronoUnit.DAYS));
          } catch (PSQLException e) {
            if (e.getMessage().contains("backdoor_dynamic_user_username_key")) {
              throw new EarlyExitException(Response.buildResponse(
                StatusLine.StatusCode.CODE_400_BAD_REQUEST,
                Map.of("Content-Type", "application/json"),
                Json
                  .object()
                  .add("errors", Json.array().add("The username is already used by another user. Please choose a different username."))
                  .toString()
              ));
            } else {
              throw e;
            }
          }
          var user = dynamicUserService.getByUsername(username);

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("user", user.toJson())
              .toString()
          );
        }
      );

      wf.registerPath(
        POST,
        "admin/user/update",
        req -> {
          var json = Json.parse(req.getBody().asString());
          var id = json.asObject().get("id").asString();
          var username = json.asObject().get("username").asString();

          try {
            dynamicUserService.updateUsername(id, username);
          } catch (PSQLException e) {
            if (e.getMessage().contains("backdoor_dynamic_user_username_key")) {
              throw new EarlyExitException(Response.buildResponse(
                StatusLine.StatusCode.CODE_400_BAD_REQUEST,
                Map.of("Content-Type", "application/json"),
                Json
                  .object()
                  .add("errors", Json.array().add("The username is already used by another user. Please choose a different username."))
                  .toString()
              ));
            } else {
              throw e;
            }
          }
          var user = dynamicUserService.getById(id);

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("user", user.toJson())
              .toString()
          );
        }
      );

      wf.registerPath(
        POST,
        "admin/user/set-temp-password",
        req -> {
          var json = Json.parse(req.getBody().asString());
          var id = json.asObject().get("id").asString();
          var tempPassword = json.asObject().get("tempPassword").asString();

          if (tempPassword.length() < 6) {
            throw new EarlyExitException(Response.buildResponse(
              StatusLine.StatusCode.CODE_400_BAD_REQUEST,
              Map.of("Content-Type", "application/json"),
              Json
                .object()
                .add("errors", Json.array().add("The password must be at least 6 characters long."))
                .toString()
            ));
          }

          dynamicUserService.setPassword(id, tempPassword, Instant.now().plus(24, ChronoUnit.HOURS));
          var user = dynamicUserService.getById(id);

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .add("user", user.toJson())
              .toString()
          );
        }
      );

      wf.registerPath(
        POST,
        "admin/user/delete",
        req -> {
          var json = Json.parse(req.getBody().asString());
          var id = json.asObject().get("id").asString();

          dynamicUserService.delete(id);

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .toString()
          );
        }
      );

      wf.registerPath(
        POST,
        "set-password",
        req -> {
          var json = Json.parse(req.getBody().asString());
          var password = json.asObject().get("password").asString();
          var confirmPassword = json.asObject().get("confirmPassword").asString();

          if (password.length() < 6) {
            throw new EarlyExitException(Response.buildResponse(
              StatusLine.StatusCode.CODE_400_BAD_REQUEST,
              Map.of("Content-Type", "application/json"),
              Json
                .object()
                .add("errors", Json.array().add("The password must be at least 6 characters long."))
                .toString()
            ));
          }

          if (!password.equals(confirmPassword)) {
            throw new EarlyExitException(Response.buildResponse(
              StatusLine.StatusCode.CODE_400_BAD_REQUEST,
              Map.of("Content-Type", "application/json"),
              Json
                .object()
                .add("errors", Json.array().add("The password doesn't match the confirmed password."))
                .toString()
            ));
          }

          dynamicUserService.setPassword(auth.get().dynamicUser().id(), password, null);

          return Response.buildResponse(
            StatusLine.StatusCode.CODE_200_OK,
            Map.of("Content-Type", "application/json"),
            Json
              .object()
              .toString()
          );
        }
      );
    }

    return minum;
  }

  @Override
  protected DatabaseConfig[] getAdHocDatabaseConfigs() {
    return this.auth.get().adHocDatabaseConfigs();
  }

  @Override
  protected IResponse handleUpdatingAdHocDataSourceConfigs(IRequest req, DatabaseConfig[] adHocDatabaseConfigs) throws Exception {
    var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");

    return Response.buildResponse(
      StatusLine.StatusCode.CODE_200_OK,
      Map.of(
        "Content-Type", "application/json",
        "Set-Cookie", makeAuthSetCookieLine(
          auth.get().dynamicUser(),
          auth.get().sourceCodeUser(),
          auth.get().databaseUsers(),
          adHocDatabaseConfigs,
          secretKey,
          Instant.now().plus(1, ChronoUnit.DAYS),
          !isLocalHost
        )
      ),
      Json.object().toString()
    );
  }

  protected IResponse processIndexPage(IRequest req) throws Exception {
    var isLocalHost = req.getHeaders().valueByKey("Host").stream().findFirst().orElse("").startsWith("localhost");
    var csrfToken = extractOrMakeCsrfCookieValue(req, true);
    return Response.htmlOk(
      makeHtml("index.html", csrfToken, Paradigm.WEB, VERSION, auth.get() != null ? auth.get().toLoggedInUser() : null, globalSettings),
      Map.of("Set-Cookie", makeCsrfTokenSetCookieLine(csrfToken, !isLocalHost))
    );
  }
}
