package tanin.backdoor.web;

import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.engine.EngineProvider;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static tanin.backdoor.core.BackdoorCoreServer.makeSqlLiteral;
import static tanin.backdoor.core.BackdoorCoreServer.makeSqlName;

public class DynamicUserService {
  private final String TABLE = "backdoor_dynamic_user";
  private final DatabaseConfig dbConfig;
  private final EngineProvider engineProvider;

  public DynamicUserService(String databaseUrl) {
    this.dbConfig = new DatabaseConfig("backdoor-admin-user", databaseUrl, null, null);
    this.engineProvider = new EngineProvider() {
    };
  }

  public DynamicUser[] getAll() throws Exception {
    var users = new ArrayList<DynamicUser>();
    try (var engine = engineProvider.createEngine(dbConfig, null)) {
      engine.executeQuery(
        "SELECT id, username, hashed_password, password_expired_at FROM " + makeSqlName(TABLE) + " ORDER BY username ASC",
        rs -> {
          while (rs.next()) {
            var passwordExpiredAt = rs.getTimestamp("password_expired_at");
            users.add(new DynamicUser(
              rs.getString("id"),
              rs.getString("username"),
              rs.getString("hashed_password"),
              passwordExpiredAt == null ? null : passwordExpiredAt.toInstant()
            ));
          }
        }
      );
    }

    return users.toArray(new DynamicUser[0]);
  }

  public void create(String username, String password, Instant passwordExpiredAt) throws Exception {
    try (var engine = engineProvider.createEngine(dbConfig, null)) {
      var pairs = new String[][]{
        new String[]{"username", username},
        new String[]{"hashed_password", PasswordHasher.generateHash(password)},
        new String[]{"password_expired_at", passwordExpiredAt != null ? new Timestamp(passwordExpiredAt.toEpochMilli()).toString() : null},
      };
      engine.execute(
        "INSERT INTO " + makeSqlName(TABLE) +
          " (" + String.join(", ", Arrays.stream(pairs).map(p -> makeSqlName(p[0])).toArray(String[]::new)) +
          ") VALUES (" +
          String.join(", ", Arrays.stream(pairs).map(p -> makeSqlLiteral(p[1])).toArray(String[]::new)) +
          ")"
      );
    }
  }

  public DynamicUser getByUsername(String username) throws Exception {
    AtomicReference<DynamicUser> user = new AtomicReference<>();
    try (var engine = engineProvider.createEngine(dbConfig, null)) {
      engine.executeQuery(
        "SELECT id, username, hashed_password, password_expired_at FROM " + makeSqlName(TABLE) + " WHERE username = " + makeSqlLiteral(username),
        rs -> {
          while (rs.next()) {
            var passwordExpiredAt = rs.getTimestamp("password_expired_at");
            user.set(new DynamicUser(
              rs.getString("id"),
              rs.getString("username"),
              rs.getString("hashed_password"),
              passwordExpiredAt == null ? null : passwordExpiredAt.toInstant()
            ));
          }
        }
      );
    }
    return user.get();
  }

  public void updateUsername(String id, String newUsername) throws Exception {
    try (var engine = engineProvider.createEngine(dbConfig, null)) {
      engine.execute(
        "UPDATE " + makeSqlName(TABLE) +
          " SET username = " + makeSqlLiteral(newUsername) +
          " WHERE id = " + makeSqlLiteral(id)
      );
    }
  }

  public void setPassword(String id, String newPassword, Instant expires) throws Exception {
    try (var engine = engineProvider.createEngine(dbConfig, null)) {
      engine.execute(
        "UPDATE " + makeSqlName(TABLE) +
          " SET hashed_password = " + makeSqlLiteral(PasswordHasher.generateHash(newPassword)) + ", " +
          " password_expired_at = " + (expires == null ? "NULL" : makeSqlLiteral(new Timestamp(expires.toEpochMilli()).toString())) + " " +
          " WHERE id = " + makeSqlLiteral(id)
      );
    }
  }


  public DynamicUser getById(String id) throws Exception {
    AtomicReference<DynamicUser> user = new AtomicReference<>();
    try (var engine = engineProvider.createEngine(dbConfig, null)) {
      engine.executeQuery(
        "SELECT id, username, hashed_password, password_expired_at FROM " + makeSqlName(TABLE) + " WHERE id = " + makeSqlLiteral(id),
        rs -> {
          while (rs.next()) {
            var passwordExpiredAt = rs.getTimestamp("password_expired_at");
            user.set(new DynamicUser(
              rs.getString("id"),
              rs.getString("username"),
              rs.getString("hashed_password"),
              passwordExpiredAt == null ? null : passwordExpiredAt.toInstant()
            ));
          }
        }
      );
    }
    return user.get();
  }

  public void delete(String id) throws Exception {
    try (var engine = engineProvider.createEngine(dbConfig, null)) {
      engine.execute(
        "DELETE FROM " + makeSqlName(TABLE) +
          " WHERE id = " + makeSqlLiteral(id)
      );
    }
  }
}
