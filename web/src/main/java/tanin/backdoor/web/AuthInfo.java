package tanin.backdoor.web;

import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.DatabaseUser;

import java.time.Instant;

public record AuthInfo(
  DynamicUser dynamicUser,
  SourceCodeUser sourceCodeUser,
  DatabaseUser[] databaseUsers,
  DatabaseConfig[] adHocDatabaseConfigs,
  Instant expires
) {

  AuthCookie toAuthCookie() {
    return new AuthCookie(
      dynamicUser == null ? null : dynamicUser.id(),
      sourceCodeUser == null ? null : sourceCodeUser.username(),
      databaseUsers,
      adHocDatabaseConfigs,
      expires
    );
  }
}
