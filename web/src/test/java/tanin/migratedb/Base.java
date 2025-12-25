package tanin.migratedb;

import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Base {
  static String POSTGRES_DATABASE_URL = "postgres://backdoor_test_user:test@127.0.0.1:5432/backdoor_test";

  void resetDatabase() throws Exception {
    try (var conn = new DatabaseConnection(POSTGRES_DATABASE_URL)) {
      conn.execute("DROP SCHEMA IF EXISTS public CASCADE");
      conn.execute("CREATE SCHEMA public");
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    resetDatabase();
  }

  void assertSameScript(MigrateScript script, AlreadyMigratedScript alreadyMigratedScript) {
    assertEquals(script.id(), alreadyMigratedScript.id());
    assertEquals(script.up(), alreadyMigratedScript.up());
    assertEquals(script.down(), alreadyMigratedScript.down());
  }

  void assertSameScripts(MigrateScript[] scripts, AlreadyMigratedScript[] alreadyMigratedScripts) {
    assertEquals(scripts.length, alreadyMigratedScripts.length);

    for (int i = 0; i < scripts.length; i++) {
      assertSameScript(scripts[i], alreadyMigratedScripts[i]);
    }
  }
}
