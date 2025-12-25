package tanin.migratedb;

import org.postgresql.util.PSQLException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class LockService {
  private static final Logger logger = Logger.getLogger(LockService.class.getName());
  public DatabaseConnection connection;
  public int timeoutInSeconds = 10;

  LockService(DatabaseConnection connection) {
    this.connection = connection;
  }

  public interface Process {
    void process() throws SQLException;
  }

  void lock(Process process) throws SQLException, InterruptedException, TimeoutException {
    var salt = java.util.UUID.randomUUID().toString();
    try {
      acquireLock(salt);
      process.process();
    } finally {
      releaseLock(salt);
    }
  }

  public void acquireLock(String salt) throws SQLException, InterruptedException, TimeoutException {
    logger.info("Acquiring lock for MigrateDB");
    var deadline = Instant.now().plus(timeoutInSeconds, ChronoUnit.SECONDS);
    while (true) {
      if ((Instant.now().isAfter(deadline))) {
        throw new TimeoutException("Unable to acquire the lock for MigrateDB. If you are certain no process is running, please clear the table `migrate_db_lock` and try again.");
      }

      try {
        connection.execute(
          "INSERT INTO \"migrate_db_lock\" (\"locked\", \"salt\", \"acquired_at\") VALUES (?, ?, ?);",
          new Object[]{
            true,
            salt,
            new Timestamp(Instant.now().toEpochMilli())
          }
        );
        break;
      } catch (PSQLException e) {
        if (e.getSQLState().equals("23505")) {
          logger.info("Unable to acquire the lock for MigrateDB. Sleeping for 1 second...");
          Thread.sleep(1000);
        } else {
          throw e;
        }
      }
    }
    logger.info("Acquired lock for MigrateDB");
  }

  public void releaseLock(String salt) throws SQLException {
    logger.info("Releasing lock for MigrateDB");
    connection.execute(
      "DELETE FROM \"migrate_db_lock\" WHERE \"salt\" = ?",
      new Object[]{salt}
    );
    logger.info("Released lock for MigrateDB");
  }
}
