package tanin.migratedb;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class LockTest extends Base {
  @Test
  void throwTimeout() throws Exception {
    var scriptDir = new MigrateDb.MigrateScriptDir(LockTest.class, "/timeoutsql");
    var thread = new Thread(() -> {
      try {
        var migrate = new MigrateDb(POSTGRES_DATABASE_URL, scriptDir);
        migrate.migrate();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    thread.start();
    Thread.sleep(500); // Ensure the thread starts running...

    var ex = assertThrows(TimeoutException.class, () -> {
      var migrate = new MigrateDb(POSTGRES_DATABASE_URL, scriptDir);
      migrate.lockService.timeoutInSeconds = 3;
      migrate.migrate();
    });
    assertEquals(
      "Unable to acquire the lock for MigrateDB. If you are certain no process is running, please clear the table `migrate_db_lock` and try again.",
      ex.getMessage()
    );
    thread.interrupt();

    var conn = new DatabaseConnection(POSTGRES_DATABASE_URL);
    conn.executeQuery("SELECT * FROM migrate_db_lock", rs -> {
      assertTrue(rs.next()); // The lock still exists because Thread.interrupt() skips the finally block.
      assertFalse(rs.next()); // Only one record of lock exists.
    });
  }
}
