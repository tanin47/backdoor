package tanin.backdoor;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public class SqlSession implements AutoCloseable {
  private static final Logger logger = Logger.getLogger(SqlSession.class.getName());
  private final Connection connection;
  private final String username;

  SqlSession(String url, User user) throws SQLException, URISyntaxException {
    this.connection = makeConnection(url, user);
    this.username = user.username();
  }

  public ResultSet executeQuery(String sql) throws SQLException {
    logger.info("[" + username + "] Executing query: " + sql.replaceAll("[\\s\n]+", " "));
    return connection.createStatement().executeQuery(sql);
  }

  public void execute(String sql) throws SQLException {
    logger.info("[" + username + "] Executing: " + sql.replaceAll("[\\s\n]+", " "));
    connection.createStatement().execute(sql);
  }

  public int executeUpdate(String sql) throws SQLException {
    logger.info("[" + username + "] Executing update: " + sql.replaceAll("[\\s\n]+", " "));
    return connection.createStatement().executeUpdate(sql);
  }

  private static User getUser(String url, User user) throws URISyntaxException {
    var uri = new URI(url);

    if (user != null && user.isPg()) {
      return user;
    }

    String username;
    String password;
    var userInfo = uri.getUserInfo();

    if (userInfo == null) {
      throw new IllegalArgumentException(
        "You must specify a username and password through the URL or login using a Postgres user."
      );
    }

    var userAndPassword = uri.getUserInfo().split(":");

    if (userAndPassword.length == 2) {
      username = userAndPassword[0];
      password = userAndPassword[1];
    } else {
      throw new IllegalArgumentException("The username and password specified in the URL aren't in a valid format.");
    }

    return new User(username, password, false);
  }

  public static Connection makeConnection(String url, User pgUser) throws SQLException, URISyntaxException {
    String baseUrl;
    User user;
    if (url.startsWith("jdbc:postgresql://")) {
      url = url.substring("jdbc:".length());
    }

    if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
      user = getUser(url, pgUser);

      var uri = new URI(url);
      var host = uri.getHost();
      var port = uri.getPort();
      var database = uri.getPath().substring(1);
      var portStr = port == -1 ? "" : ":" + port;
      baseUrl = "jdbc:postgresql://" + host + portStr + "/" + database;
    } else {
      throw new IllegalArgumentException("PostgreSQL or JDBC URL is invalid");
    }

    var props = new Properties();
    props.setProperty("user", user.username());
    props.setProperty("password", user.password());

    return DriverManager.getConnection(baseUrl, props);
  }

  @Override
  public void close() throws Exception {
    this.connection.close();
  }
}
