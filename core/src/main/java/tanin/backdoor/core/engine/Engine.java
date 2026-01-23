package tanin.backdoor.core.engine;

import com.eclipsesource.json.JsonValue;
import tanin.backdoor.core.*;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Logger;

import static tanin.backdoor.core.BackdoorCoreServer.makeSqlLiteral;
import static tanin.backdoor.core.BackdoorCoreServer.makeSqlName;

public abstract class Engine implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(Engine.class.getName());

  public static class InvalidCredentialsException extends Exception {
    public InvalidCredentialsException(String message) {
      super(message);
    }
  }

  public static class InvalidDatabaseNameProbablyException extends Exception {
    public InvalidDatabaseNameProbablyException(String message) {
      super(message);
    }
  }

  public static class GenericConnectionException extends Exception {
    public GenericConnectionException(String message) {
      super(message);
    }
  }

  public static class UnreachableServerException extends Exception {
    public UnreachableServerException(String message) {
      super(message);
    }
  }

  public static class OverwritingUserAndCredentialedJdbcConflictedException extends Exception {
  }

  static {
    DriverManager.setLoginTimeout(5);
  }

  public DatabaseConfig databaseConfig;
  public Connection connection;

  public abstract static class Value {
  }

  public static class UseDefaultValue extends Value {
  }

  public static class UseNull extends Value {
  }

  public static class UseSpecifiedValue extends Value {
    public final String value;

    public UseSpecifiedValue(String value) {
      this.value = value;
    }
  }

  protected Engine(DatabaseConfig config, DatabaseUser overwritingUser) throws SQLException, URISyntaxException, InvalidCredentialsException, OverwritingUserAndCredentialedJdbcConflictedException, UnreachableServerException, InvalidDatabaseNameProbablyException, GenericConnectionException {
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

  protected abstract void connect(DatabaseConfig config, DatabaseUser overwritingUser) throws SQLException, InvalidCredentialsException, URISyntaxException, UnreachableServerException, InvalidDatabaseNameProbablyException, GenericConnectionException;

  public abstract Column[] getColumns(String table) throws SQLException;

  public abstract String[] getTables() throws SQLException;

  public abstract void insert(String table, Column[] columns, Engine.Value[] values) throws Exception;

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
    var tableStats = new Stats(0);
    executeQuery(
      "SELECT COUNT(*) AS numberOfRows FROM (" + sql + ") " + whereClause,
      rs -> {
        while (rs.next()) {
          tableStats.numberOfRows = rs.getInt("numberOfRows");
        }
      }
    );
    return tableStats;
  }

  public String makeLoadTableSql(String table, Column[] columns) {
    return "SELECT " + String.join(", ", Arrays.stream(columns).map(c -> makeSqlName(c.name)).toArray(String[]::new)) +
      " FROM " + makeSqlName(table);
  }

  public void executeQueryWithParams(String sql, Filter[] filters, Sort[] sorts, int offset, int limit, ProcessResultSet processResultSet) throws SQLException {
    var whereClause = makeWhereClause(filters);

    var orderByClause = "";
    if (sorts != null && sorts.length > 0) {
      orderByClause = " ORDER BY " + String.join(", ", Arrays.stream(sorts).map(s -> makeSqlName(s.name) + " " + s.direction).toArray(String[]::new));
    }

    executeQuery(
      "SELECT * FROM (" + sql + ") " +
        whereClause +
        orderByClause +
        " LIMIT " + limit +
        " OFFSET " + offset,
      processResultSet
    );
  }

  public String makeWhereClause(Filter[] filters) {
    var whereClause = "";
    if (filters != null && filters.length > 0) {
      var clauses = Arrays
        .stream(filters)
        .map(s -> {
          String operatorAndValue;

          if (s.operator == Filter.Operator.IS_NULL) {
            operatorAndValue = " IS NULL";
          } else if (s.operator == Filter.Operator.IS_NOT_NULL) {
            operatorAndValue = " IS NOT NULL";
          } else if (s.operator == Filter.Operator.EQUAL) {
            operatorAndValue = " = " + makeSqlLiteral(s.value);
          } else {
            throw new RuntimeException("Unknown operator: " + s.operator);
          }

          return makeSqlName(s.name) + " " + operatorAndValue;
        })
        .toArray(String[]::new);
      whereClause = " WHERE " + String.join(" AND ", clauses);
    }
    return whereClause;
  }

  public void select(String tableName, Column column, Filter[] filters, ProcessResultSet processResultSet) throws SQLException {
    var whereClause = makeWhereClause(filters);
    executeQuery(
      "SELECT " + makeSqlName(column.name) + " FROM " + makeSqlName(tableName) + whereClause,
      processResultSet
    );
  }

  public void drop(String table, boolean useCascade) throws SQLException {
    var maybeCascade = useCascade ? " CASCADE" : "";
    execute("DROP TABLE IF EXISTS " + makeSqlName(table) + maybeCascade);
  }

  public interface ProcessResultSet {
    void process(ResultSet rs) throws SQLException;
  }

  public void executeQuery(
    String sql,
    ProcessResultSet processResultSet
  ) throws SQLException {
    logger.info("Executing query: " + sql);
    try (var stmt = connection.createStatement()) {
      try (var rs = stmt.executeQuery(sql)) {
        processResultSet.process(rs);
      }
    }
  }

  public boolean execute(String sql) throws SQLException {
    logger.info("Executing: " + sql);
    try (var stmt = connection.createStatement()) {
      return stmt.execute(sql);
    }
  }

  public int executeUpdate(String sql) throws SQLException {
    logger.info("Executing update: " + sql);
    try (var stmt = connection.createStatement()) {
      return stmt.executeUpdate(sql);
    }
  }
}
