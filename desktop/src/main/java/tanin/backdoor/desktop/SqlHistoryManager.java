package tanin.backdoor.desktop;

import java.sql.*;
import java.util.logging.Logger;

public class SqlHistoryManager {
  private static final Logger logger = Logger.getLogger(SqlHistoryManager.class.getName());
  private static final String DB_URL = "jdbc:sqlite:sql_history.db";

  static {
    try {
      DriverManager.registerDriver(new org.sqlite.JDBC());
      logger.info("Registered the SQLite driver");
    } catch (SQLException e) {
      logger.severe("Unable to register the SQLite driver: " + e);
      throw new RuntimeException(e);
    }

    createTable();
  }

  public static void createTable() {
    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("""
              CREATE TABLE IF NOT EXISTS sql_history (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  sql TEXT NOT NULL,
                  database TEXT NOT NULL,
                  executed_at BIGINT NOT NULL
              )
          """);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create the table", e);
    }
  }

  public static void resetForTesting() {
    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("DROP TABLE IF EXISTS sql_history");
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to clear SQL history", e);
    }

    createTable();
  }

  public static void addEntry(SqlHistoryEntry entry) {
    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement(
        "INSERT INTO sql_history (sql, database, executed_at) VALUES (?, ?, ?)"
      )) {
        stmt.setString(1, entry.sql());
        stmt.setString(2, entry.database());
        stmt.setLong(3, entry.executedAt());
        stmt.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to save SQL history entry", e);
    }
  }


  public static java.util.List<SqlHistoryEntry> getEntries(String keyword) {
    String sql = "SELECT sql, database, executed_at FROM sql_history";
    if (keyword != null && !keyword.isBlank()) {
      sql += " WHERE LOWER(sql) LIKE ?";
    }
    sql += " ORDER BY executed_at DESC";

    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        if (keyword != null && !keyword.isBlank()) {
          stmt.setString(1, "%" + keyword.toLowerCase() + "%");
        }
        try (ResultSet rs = stmt.executeQuery()) {
          java.util.List<SqlHistoryEntry> entries = new java.util.ArrayList<>();
          while (rs.next()) {
            entries.add(new SqlHistoryEntry(
              rs.getString("sql"),
              rs.getString("database"),
              rs.getLong("executed_at")
            ));
          }
          return entries;
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get SQL history entries", e);
    }
  }
}
