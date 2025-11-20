package tanin.backdoor.core.engine;

import com.eclipsesource.json.JsonValue;
import tanin.backdoor.core.*;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import static tanin.backdoor.core.BackdoorCoreServer.makeSqlLiteral;
import static tanin.backdoor.core.BackdoorCoreServer.makeSqlName;

public abstract class Engine implements AutoCloseable {
  public static class InvalidCredentialsException extends Exception {
    public InvalidCredentialsException(String message) {
      super(message);
    }
  }

  public static class OverwritingUserAndCredentialedJdbcConflictedException extends Exception {
  }

  public DatabaseConfig databaseConfig;
  public Connection connection;

  Engine(DatabaseConfig config, User overwritingUser) throws SQLException, URISyntaxException, InvalidCredentialsException, OverwritingUserAndCredentialedJdbcConflictedException {
    this.databaseConfig = config;

    try {
      connect(config, null); // Try it without the overwriting user.

      if (overwritingUser != null) {
        // If the database config is already valid, we don't allow using the overwriting user.
        throw new OverwritingUserAndCredentialedJdbcConflictedException();
      }
    } catch (InvalidCredentialsException e) {
      if (overwritingUser != null) {
        connect(config, overwritingUser);
      } else {
        throw new InvalidCredentialsException(e.getMessage());
      }
    }
  }

  protected abstract void connect(DatabaseConfig config, User overwritingUser) throws SQLException, InvalidCredentialsException, URISyntaxException;

  public abstract Column[] getColumns(String table) throws SQLException;

  public abstract String[] getTables() throws SQLException;

  public abstract void update(String table, Column column, String newValue, Filter[] filters) throws Exception;

  public abstract void delete(String table, Filter[] filters) throws Exception;

  public abstract void rename(String table, String newTableName) throws SQLException;

  public abstract BackdoorCoreServer.SqlType getSqlType(String sql) throws SQLException;

  public abstract Column.ColumnType convertRawType(String rawType);

  public abstract JsonValue getJsonValue(ResultSet rs, int columnIndex, Column column) throws SQLException;

  public Stats getStats(
    String sql,
    Filter[] filters
  ) throws SQLException {
    var whereClause = makeWhereClause(filters);
    var rs = connection.createStatement().executeQuery("SELECT COUNT(*) AS numberOfRows FROM (" + sql + ") " + whereClause);
    var tableStats = new Stats(0);
    while (rs.next()) {
      tableStats.numberOfRows = rs.getInt("numberOfRows");
    }
    return tableStats;
  }

  public String makeLoadTableSql(String table, Column[] columns) {
    return "SELECT " + String.join(", ", Arrays.stream(columns).map(c -> makeSqlName(c.name)).toArray(String[]::new)) +
      " FROM " + makeSqlName(table);
  }

  public ResultSet executeQueryWithParams(String sql, Filter[] filters, Sort[] sorts, int offset, int limit) throws SQLException {
    var whereClause = makeWhereClause(filters);

    var orderByClause = "";
    if (sorts != null && sorts.length > 0) {
      orderByClause = " ORDER BY " + String.join(", ", Arrays.stream(sorts).map(s -> makeSqlName(s.name) + " " + s.direction).toArray(String[]::new));
    }

    return connection.createStatement().executeQuery(
      "SELECT * FROM (" + sql + ") " +
        whereClause +
        orderByClause +
        " LIMIT " + limit +
        " OFFSET " + offset
    );
  }

  public String makeWhereClause(Filter[] filters) {
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

  public ResultSet select(String tableName, Column column, Filter[] filters) throws SQLException {
    var whereClause = makeWhereClause(filters);
    return connection.createStatement().executeQuery(
      "SELECT " + makeSqlName(column.name) + " FROM " + makeSqlName(tableName) + whereClause
    );
  }

  public void drop(String table, boolean useCascade) throws SQLException {
    var maybeCascade = useCascade ? " CASCADE" : "";
    connection.createStatement().execute("DROP TABLE IF EXISTS " + makeSqlName(table) + maybeCascade);
  }

  public ResultSet executeQuery(String sql) throws SQLException {
    return connection.createStatement().executeQuery(sql);
  }

  static {
    try {
      DriverManager.registerDriver(new org.postgresql.Driver());
      DriverManager.registerDriver(new com.clickhouse.jdbc.Driver());
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static Engine createEngine(DatabaseConfig config, User overwritingUser) throws SQLException, URISyntaxException, InvalidCredentialsException, OverwritingUserAndCredentialedJdbcConflictedException {
    if (config.jdbcUrl.startsWith("jdbc:postgres") || config.jdbcUrl.startsWith("postgres")) {
      return new PostgresEngine(config, overwritingUser);
    } else if (config.jdbcUrl.startsWith("jdbc:ch:")) {
      return new ClickHouseEngine(config, overwritingUser);
    } else {
      throw new UnsupportedOperationException(config.jdbcUrl + " is not supported. Please make your feature request at https://github.com/tanin47/backdoor.");
    }
  }

  public int executeUpdate(String sql) throws SQLException {
    return connection.createStatement().executeUpdate(sql);
  }
}
