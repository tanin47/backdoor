package tanin.migratedb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MigrateTest extends Base {
  @Test
  void migrateAndAutoRollback() throws Exception {
    var scriptDir = new MigrateDb.MigrateScriptDir(MigrateTest.class, "/testsql");
    var migrate = new MigrateDb(POSTGRES_DATABASE_URL, scriptDir);
    migrate.migrate();

    var scripts = MigrateDb.getMigrateScripts(scriptDir);
    var alreadyMigratedScripts = migrate.alreadyMigratedScriptService.getAll();
    assertSameScripts(scripts, alreadyMigratedScripts);

    // Simulate changing 2.sqx
    migrate.databaseConnection.execute("UPDATE migrate_db_already_migrated_script SET up_script = 'something else' WHERE id = 2;");
    alreadyMigratedScripts = migrate.alreadyMigratedScriptService.getAll();
    assertEquals("something else", alreadyMigratedScripts[1].up());

    // The previous scripts are reverted, and the new scripts are applied.
    migrate.migrate();
    alreadyMigratedScripts = migrate.alreadyMigratedScriptService.getAll();
    assertSameScripts(scripts, alreadyMigratedScripts);

    migrate.databaseConnection.executeQuery("SELECT * FROM migrate_db_lock", rs -> {
      assertFalse(rs.next()); // no lock
    });
  }

  @Test
  void getMigrateScripts() {
    var scripts = MigrateDb.getMigrateScripts(new MigrateDb.MigrateScriptDir(MigrateTest.class, "/testsql"));
    assertEquals(3, scripts.length);

    assertEquals(1, scripts[0].id());
    assertEquals(
      """
        CREATE TABLE "migratedb_test_user"
        (
            id TEXT PRIMARY KEY DEFAULT ('user-' || gen_random_uuid()),
            username TEXT NOT NULL,
            hashed_password TEXT NOT NULL,
            password_expired_at TIMESTAMP
        );
        """.trim(),
      scripts[0].up()
    );
    assertEquals(
      "DROP TABLE \"migratedb_test_user\";",
      scripts[0].down()
    );

    assertEquals(2, scripts[1].id());
    assertEquals(
      "ALTER TABLE \"migratedb_test_user\" DROP COLUMN \"password_expired_at\";",
      scripts[1].up()
    );
    assertEquals(
      "ALTER TABLE \"migratedb_test_user\" ADD COLUMN \"password_expired_at\" TIMESTAMP;",
      scripts[1].down()
    );

    assertEquals(3, scripts[2].id());
    assertEquals(
      "ALTER TABLE \"migratedb_test_user\" ADD COLUMN \"age\" INT;",
      scripts[2].up()
    );
    assertEquals(
      "ALTER TABLE \"migratedb_test_user\" DROP COLUMN \"age\";",
      scripts[2].down()
    );
  }
}
