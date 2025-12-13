package tanin.backdoor.desktop.engine;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import org.sqlite.SQLiteException;
import tanin.backdoor.core.*;
import tanin.backdoor.core.engine.Engine;
import tanin.backdoor.desktop.nativeinterface.Base;
import tanin.backdoor.desktop.nativeinterface.MacOsApi;

import java.net.URISyntaxException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

import static org.sqlite.SQLiteErrorCode.SQLITE_NOTADB;
import static tanin.backdoor.core.BackdoorCoreServer.makeSqlLiteral;
import static tanin.backdoor.core.BackdoorCoreServer.makeSqlName;

public class SqliteEngine extends Engine {
  private static final Logger logger = Logger.getLogger(SqliteEngine.class.getName());

  static {
    try {
      DriverManager.registerDriver(new org.postgresql.Driver());
      logger.info("Registered the SQLite driver");
    } catch (SQLException e) {
      logger.severe("Unable to register the SQLite driver: " + e);
      throw new RuntimeException(e);
    }
  }

  SqliteEngine(DatabaseConfig config, User overwritingUser) throws SQLException, URISyntaxException, InvalidCredentialsException, OverwritingUserAndCredentialedJdbcConflictedException, UnreachableServerException, InvalidDatabaseNameProbablyException, GenericConnectionException {
    super(config, overwritingUser);
  }

  String filePath;

  @Override
  protected void connect(DatabaseConfig config, User overwritingUser) throws SQLException, InvalidCredentialsException, URISyntaxException, UnreachableServerException, InvalidDatabaseNameProbablyException, GenericConnectionException {
    filePath = config.jdbcUrl.substring("jdbc:sqlite:".length());
    try {
      if (Base.CURRENT_OS == Base.OperatingSystem.MAC) {
        MacOsApi.N.startAccessingSecurityScopedResource(filePath);
      }
      connection = DriverManager.getConnection(config.jdbcUrl);
      execute("SELECT 'backdoor_test_connection_for_sqlite'");
    } catch (SQLiteException e) {
      if (e.getResultCode() == SQLITE_NOTADB) {
        throw new GenericConnectionException("The selected file isn't a SQLite database");
      } else {
        throw e;
      }
    }
  }

  @Override
  public Column[] getColumns(String table) throws SQLException {
    var columns = new ArrayList<Column>();
    try (var rs = executeQuery(
      "SELECT name, type, \"notnull\", pk FROM pragma_table_info('" + table + "')"
    )) {
      while (rs.next()) {
        var name = rs.getString("name");
        var type = rs.getString("type");
        var nullable = rs.getInt("notnull") == 0;
        var isPrimaryKey = rs.getInt("pk") > 0;

        columns.add(new Column(
          name,
          convertRawType(type),
          type,
          name.length(),
          isPrimaryKey,
          nullable
        ));
      }
    }
    return columns.toArray(new Column[0]);
  }

  @Override
  public String[] getTables() throws SQLException {
    var tables = new ArrayList<String>();
    try (var rs = executeQuery(
      "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    )) {
      while (rs.next()) {
        tables.add(rs.getString("name"));
      }
    }
    return tables.toArray(new String[0]);
  }

  @Override
  public void insert(String table, Column[] columns, String[] values) throws Exception {
    execute(
      "INSERT INTO " + makeSqlName(table) + "(" +
        String.join(",", Arrays.stream(columns).map(c -> makeSqlName(c.name)).toArray(String[]::new)) +
        ") VALUES (" +
        String.join(
          ",",
          Arrays.stream(values)
            .map(v -> {
              if (v == null) {
                return "NULL";
              } else {
                return makeSqlLiteral(v);
              }
            })
            .toArray(String[]::new)
        ) +
        ")"
    );
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

    if (Base.CURRENT_OS == Base.OperatingSystem.MAC) {
      MacOsApi.N.stopAccessingSecurityScopedResource(filePath);
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

    boolean isModifyingTable = false;
    try (var rs = executeQuery("explain " + sql)) {
      while (rs.next()) {
        if (rs.getString("opcode").equals("OpenWrite")) {
          isModifyingTable = true;
          break;
        }
      }
    }

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
