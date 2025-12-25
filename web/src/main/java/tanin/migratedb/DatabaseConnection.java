package tanin.migratedb;

import tanin.backdoor.core.engine.Engine;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public class DatabaseConnection implements AutoCloseable {
  private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());
  public Connection connection;

  DatabaseConnection(String url) throws SQLException, URISyntaxException {
    var props = new Properties();

    if (url.startsWith("jdbc:postgres")) {
      // do nothing
    } else if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
      var uri = new URI(url);
      var host = uri.getHost();
      var port = uri.getPort();
      var database = uri.getPath().substring(1);
      var portStr = port == -1 ? "" : ":" + port;

      var userInfo = uri.getUserInfo();

      if (userInfo != null) {
        var userAndPassword = userInfo.split(":");

        if (userAndPassword.length == 2) {
          props.setProperty("user", userAndPassword[0]);
          props.setProperty("password", userAndPassword[1]);
        }
      }

      url = "jdbc:postgresql://" + host + portStr + "/" + database;
    }
    connection = DriverManager.getConnection(url, props);
  }

  public void execute(String sql) throws SQLException {
    execute(sql, new Object[0]);
  }

  public void execute(String sql, Object[] args) throws SQLException {
    logger.info("Executing: " + sql);
    try (var stmt = connection.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) {
        stmt.setObject(i + 1, args[i]);
      }
      stmt.execute();
    }
  }

  public interface ProcessResultSet {
    void process(ResultSet rs) throws SQLException;
  }

  public void executeQuery(
    String sql,
    Engine.ProcessResultSet processResultSet
  ) throws SQLException {
    logger.info("Executing query: " + sql);
    try (var stmt = connection.createStatement()) {
      try (var rs = stmt.executeQuery(sql)) {
        processResultSet.process(rs);
      }
    }
  }

  @Override
  public void close() throws Exception {
    if (connection != null) {
      connection.close();
    }
  }
}
