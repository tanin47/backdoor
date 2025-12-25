package tanin.backdoor.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.DatabaseUser;

import java.time.Instant;
import java.util.ArrayList;

public record AuthInfo(
  BackdoorUser backdoorUser,
  CommandLineUser commandLineUser,
  DatabaseUser[] databaseUsers,
  DatabaseConfig[] adHocDatabaseConfigs,
  Instant expires
) {

  AuthCookie toAuthCookie() {
    return new AuthCookie(
      backdoorUser == null ? null : backdoorUser.id(),
      commandLineUser == null ? null : commandLineUser.username(),
      databaseUsers,
      adHocDatabaseConfigs,
      expires
    );
  }
}
