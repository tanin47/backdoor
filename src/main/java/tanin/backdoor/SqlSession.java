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

  SqlSession(String url) throws SQLException, URISyntaxException {
    this.connection = makeConnection(url);
  }

  public ResultSet executeQuery(String sql) throws SQLException {
    logger.info("Executing query: " + sql.replaceAll("[\\s\n]+", " "));
    return connection.createStatement().executeQuery(sql);
  }

  public void execute(String sql) throws SQLException {
    logger.info("Executing: " + sql.replaceAll("[\\s\n]+", " "));
    connection.createStatement().execute(sql);
  }

  public int executeUpdate(String sql) throws SQLException {
    logger.info("Executing upate: " + sql.replaceAll("[\\s\n]+", " "));
    return connection.createStatement().executeUpdate(sql);
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

  @Override
  public void close() throws Exception {
    this.connection.close();
  }
}
