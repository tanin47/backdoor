package tanin.migratedb;

import org.postgresql.util.PSQLException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class MigrateDb implements AutoCloseable {
  private static final Logger logger = Logger.getLogger(MigrateDb.class.getName());
  public final AlreadyMigratedScriptService alreadyMigratedScriptService;
  public final DatabaseConnection databaseConnection;
  public final LockService lockService;
  public final MigrateScriptDir migrateScriptDir;

  @Override
  public void close() throws Exception {
    databaseConnection.close();
  }

  public record MigrateScriptDir(Class<?> classLoader, String path) {
  }

  public MigrateDb(
    String databaseUrl,
    MigrateScriptDir migrateScriptDir
  ) throws SQLException, URISyntaxException {
    this(
      new DatabaseConnection(databaseUrl),
      migrateScriptDir
    );
  }

  public MigrateDb(
    DatabaseConnection databaseConnection,
    MigrateScriptDir migrateScriptDir
  ) {
    this(
      new AlreadyMigratedScriptService(databaseConnection),
      new LockService(databaseConnection),
      databaseConnection,
      migrateScriptDir
    );
  }

  public MigrateDb(
    AlreadyMigratedScriptService alreadyMigratedScriptService,
    LockService lockService,
    DatabaseConnection databaseConnection,
    MigrateScriptDir migrateScriptDir
  ) {
    this.lockService = lockService;
    this.databaseConnection = databaseConnection;
    this.alreadyMigratedScriptService = alreadyMigratedScriptService;
    this.migrateScriptDir = migrateScriptDir;
  }

  public static MigrateScript[] getMigrateScripts(MigrateScriptDir migrateScriptDir) {
    var scripts = new ArrayList<MigrateScript>();
    int fileIndex = 1;
    while (true) {
      try (InputStream is = migrateScriptDir.classLoader.getResourceAsStream(migrateScriptDir.path + "/" + fileIndex + ".sql")) {
        if (is == null) {
          break;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
          String[] upAndDown = extractUpAndDownScripts(reader.lines().toArray(String[]::new));
          scripts.add(new MigrateScript(fileIndex, upAndDown[0], upAndDown[1]));
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      fileIndex++;
    }

    return scripts.toArray(new MigrateScript[0]);
  }

  public void setUpIfNeeded() throws SQLException {
    try {
      databaseConnection.execute("""
        CREATE TABLE IF NOT EXISTS "migrate_db_already_migrated_script"
        (
            id          INT       NOT NULL PRIMARY KEY,
            up_script   TEXT      NOT NULL,
            down_script TEXT      NOT NULL,
            applied_at  TIMESTAMP NOT NULL
        );""");
    } catch (PSQLException e) {
      if (e.getMessage().contains("pg_type_typname_nsp_index")) {
        // ignore
      } else {
        throw e;
      }
    }

    try {
      databaseConnection.execute("""
        CREATE TABLE IF NOT EXISTS "migrate_db_lock"
        (
            locked      BOOL      NOT NULL UNIQUE,
            salt        TEXT      NOT NULL,
            acquired_at TIMESTAMP NOT NULL
        );""");
    } catch (PSQLException e) {
      if (e.getMessage().contains("pg_type_typname_nsp_index")) {
        // ignore
      } else {
        throw e;
      }
    }
  }

  public void migrate() throws SQLException, InterruptedException, TimeoutException {
    setUpIfNeeded();

    lockService.lock(() -> {
      var migrateScripts = getMigrateScripts(migrateScriptDir);
      var alreadyMigratedScripts = alreadyMigratedScriptService.getAll();

      int startIndex = migrateScripts.length;
      for (var i = 0; i < migrateScripts.length; i++) {
        if (i >= alreadyMigratedScripts.length) {
          startIndex = i;
          break;
        }

        if (!alreadyMigratedScripts[i].up().equals(migrateScripts[i].up())) {
          startIndex = i;
          break;
        }
      }

      applyDown(alreadyMigratedScripts, startIndex);
      applyUp(migrateScripts, startIndex);
    });
  }

  public void applyUp(MigrateScript[] migrateScripts, int startIndex) throws SQLException {
    for (var i = startIndex; i < migrateScripts.length; i++) {
      logger.info("Applying the up script of " + migrateScripts[i].id() + ".sql");
      try {
        databaseConnection.execute(migrateScripts[i].up());
        alreadyMigratedScriptService.insert(migrateScripts[i]);
        logger.info("Applied the up script of " + migrateScripts[i].id() + ".sql");
      } catch (Exception e) {
        logger.log(Level.SEVERE, "An error occurred while applying the up script of " + migrateScripts[i].id() + ".sql", e);
        throw new RuntimeException(e);
      }
    }
  }

  public void applyDown(AlreadyMigratedScript[] alreadyMigratedScripts, int leastRollbackedIndex) {
    for (var i = alreadyMigratedScripts.length - 1; i >= leastRollbackedIndex; i--) {
      logger.info("Applying the down script of " + alreadyMigratedScripts[i].id() + ".sql (previous version)");
      try {
        databaseConnection.execute(alreadyMigratedScripts[i].down());
        alreadyMigratedScriptService.remove(alreadyMigratedScripts[i]);
        logger.info("Applied the down script of " + alreadyMigratedScripts[i].id() + ".sql (previous version)");
      } catch (Exception e) {
        logger.log(Level.SEVERE, "An error occurred while applying the down script of " + alreadyMigratedScripts[i].id() + ".sql (previous version)", e);
        throw new RuntimeException(e);
      }
    }
  }

  private static final Pattern UP_MARKER = Pattern.compile("#\\s*---\\s*!Ups");
  private static final Pattern DOWN_MARKER = Pattern.compile("#\\s*---\\s*!Downs");

  public static String[] extractUpAndDownScripts(String[] lines) {
    StringBuilder up = new StringBuilder();
    StringBuilder down = new StringBuilder();

    var current = "none";

    for (String line : lines) {
      if (UP_MARKER.matcher(line.trim()).matches()) {
        current = "up";
      } else if (DOWN_MARKER.matcher(line.trim()).matches()) {
        current = "down";
      } else if (current.equals("up")) {
        up.append(line);
        up.append('\n');
      } else if (current.equals("down")) {
        down.append(line);
        down.append('\n');
      }
    }

    return new String[]{up.toString().trim(), down.toString().trim()};
  }
}
