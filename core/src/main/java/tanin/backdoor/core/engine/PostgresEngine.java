package tanin.backdoor.core.engine;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import org.postgresql.util.PSQLException;
import tanin.backdoor.core.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static tanin.backdoor.core.BackdoorCoreServer.makeSqlLiteral;
import static tanin.backdoor.core.BackdoorCoreServer.makeSqlName;

public class PostgresEngine extends Engine {
  private static final Logger logger = Logger.getLogger(PostgresEngine.class.getName());

  static {
    try {
      DriverManager.registerDriver(new org.postgresql.Driver());
      logger.info("Registered the Postgres driver");
    } catch (SQLException e) {
      logger.severe("Unable to register the Postgres driver: " + e);
      throw new RuntimeException(e);
    }
  }

  PostgresEngine(DatabaseConfig config, User overwritingUser) throws SQLException, URISyntaxException, InvalidCredentialsException, OverwritingUserAndCredentialedJdbcConflictedException {
    super(config, overwritingUser);
  }

  @Override
  protected void connect(DatabaseConfig config, User overwritingUser) throws SQLException, InvalidCredentialsException, URISyntaxException {

    var url = config.jdbcUrl;
    var props = new Properties();

    if (config.username != null) {
      props.setProperty("user", config.username);
    }
    if (config.password != null) {
      props.setProperty("password", config.password);
    }

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
    } else {
      throw new IllegalArgumentException("Postgres or JDBC URL is invalid for Postgres");
    }

    if (overwritingUser != null) {
      props.setProperty("user", overwritingUser.username());
      props.setProperty("password", overwritingUser.password());
    }

    try {
      connection = DriverManager.getConnection(url, props);
      execute("SELECT 'backdoor_test_connection_for_postgres'");
    } catch (PSQLException e) {
      if (e.getSQLState().equals("28000") || e.getSQLState().equals("28P01") || e.getSQLState().equals("08004")) {
        throw new InvalidCredentialsException(e.getMessage());
      } else {
        throw e;
      }
    }
  }

  @Override
  public Column[] getColumns(String table) throws SQLException {
    var rs = executeQuery(
      "SELECT c.column_name, c.data_type, c.is_nullable, " +
        "CASE WHEN tc.constraint_type = 'PRIMARY KEY' THEN true ELSE false END as is_primary_key " +
        "FROM information_schema.columns c " +
        "LEFT JOIN information_schema.key_column_usage kcu " +
        "ON c.table_schema = kcu.table_schema " +
        "AND c.table_name = kcu.table_name " +
        "AND c.column_name = kcu.column_name " +
        "LEFT JOIN information_schema.table_constraints tc " +
        "ON kcu.constraint_name = tc.constraint_name " +
        "AND kcu.table_schema = tc.table_schema " +
        "AND kcu.table_name = tc.table_name " +
        "WHERE c.table_schema = 'public' AND c.table_name = " + makeSqlLiteral(table) + ";"
    );
    var columns = new ArrayList<>(List.<Column>of());
    while (rs.next()) {
      var name = rs.getString("column_name");
      var rawType = rs.getString("data_type");
      columns.add(new Column(
        name,
        convertRawType(rawType),
        rawType,
        name.length(),
        rs.getBoolean("is_primary_key"),
        rs.getString("is_nullable").equals("YES")
      ));
    }
    return columns.toArray(new Column[0]);
  }

  @Override
  public String[] getTables() throws SQLException {
    var tables = new ArrayList<String>();
    var rs = executeQuery(
      "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name ASC;"
    );
    while (rs.next()) {
      tables.add(rs.getString("table_name"));
    }
    return tables.toArray(new String[0]);
  }

  @Override
  public void update(String table, Column column, String newValue, Filter[] filters) throws Exception {
    if (filters.length == 0) {
      throw new Exception();
    }
    var whereClause = makeWhereClause(filters);

    if (whereClause.isEmpty()) {
      throw new Exception();
    }

    execute(
      "UPDATE " + makeSqlName(table) +
        " SET " + makeSqlName(column.name) + " = " +
        (newValue == null ? "NULL" : makeUpdateValue(column, newValue)) +
        whereClause + ";"
    );
  }

  @Override
  public void delete(String table, Filter[] filters) throws Exception {
    if (filters.length == 0) {
      throw new Exception();
    }
    var whereClause = makeWhereClause(filters);

    if (whereClause.isEmpty()) {
      throw new Exception();
    }

    execute(
      "DELETE FROM " + makeSqlName(table) + whereClause
    );
  }

  @Override
  public void rename(String table, String newTableName) throws SQLException {
    execute("ALTER TABLE " + makeSqlName(table) + " RENAME TO " + makeSqlName(newTableName));
  }

  private String makeUpdateValue(Column column, String value) {
    var type = column.rawType.toLowerCase();
    if (type.contains("timestamp")) {
      return makeSqlLiteral(value) + "::timestamp AT TIME ZONE 'UTC'";
    } else {
      return makeSqlLiteral(value);
    }
  }

  @Override
  public void close() throws Exception {
    if (connection != null) {
      connection.close();
    }
  }

  @Override
  public BackdoorCoreServer.SqlType getSqlType(String sql) throws SQLException {
    var sanitized = sql.toLowerCase().trim();
    if (sanitized.startsWith("explain")) {
      return BackdoorCoreServer.SqlType.EXPLAIN;
    }

    if (
      !sanitized.startsWith("with") &&
        !sanitized.startsWith("select") &&
        !sanitized.startsWith("insert") &&
        !sanitized.startsWith("update") &&
        !sanitized.startsWith("delete")
    ) {
      return BackdoorCoreServer.SqlType.ADMINISTRATIVE;
    }

    var rs = executeQuery("explain (format json) " + sql);
    rs.next();
    var result = Json.parse(rs.getString(1));
    var isModifyingTable = result.asArray().get(0).asObject().get("Plan").asObject().get("Node Type").asString().equals("ModifyTable");

    return isModifyingTable ? BackdoorCoreServer.SqlType.MODIFY : BackdoorCoreServer.SqlType.SELECT;
  }

  @Override
  public Column.ColumnType convertRawType(String rawType) {
    return switch (rawType.toLowerCase()) {
      case "integer", "serial", "bigint", "smallint" -> Column.ColumnType.INTEGER;
      case "numeric", "decimal", "real", "double precision" -> Column.ColumnType.DOUBLE;
      case "boolean" -> Column.ColumnType.BOOLEAN;
      case "timestamp without time zone", "timestamp with time zone" -> Column.ColumnType.TIMESTAMP;
      case "date" -> Column.ColumnType.DATE;
      case "time without time zone", "time with time zone" -> Column.ColumnType.TIME;
      default -> Column.ColumnType.STRING;
    };
  }

  @Override
  public JsonValue getJsonValue(ResultSet rs, int columnIndex, Column column) throws SQLException {
    var value = rs.getObject(columnIndex);
    if (value == null) {
      return Json.NULL;
    }

    return switch (column.type) {
      case INTEGER -> Json.value(rs.getLong(columnIndex));
      case DOUBLE -> Json.value(rs.getDouble(columnIndex));
      case BOOLEAN -> Json.value(rs.getBoolean(columnIndex));
      case TIMESTAMP -> Json.value(rs.getTimestamp(columnIndex).toInstant().toString());
      case DATE -> Json.value(rs.getDate(columnIndex).toString());
      case TIME -> Json.value(rs.getTime(columnIndex).toString());
      default -> Json.value(rs.getString(columnIndex));
    };
  }
}
