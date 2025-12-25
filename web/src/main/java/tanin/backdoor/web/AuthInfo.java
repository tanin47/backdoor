package tanin.backdoor.web;

import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.DatabaseUser;
import tanin.backdoor.core.LoggedInUser;

import java.time.Instant;

public record AuthInfo(
  DynamicUser dynamicUser,
  SourceCodeUser sourceCodeUser,
  DatabaseUser[] databaseUsers,
  DatabaseConfig[] adHocDatabaseConfigs,
  Instant expires
) {
  public LoggedInUser toLoggedInUser() {
    String username = null;
    boolean canManageDynamicUsers = false;

    if (sourceCodeUser != null) {
      username = sourceCodeUser.username();
      canManageDynamicUsers = true;
    } else if (dynamicUser != null) {
      username = dynamicUser.username();
    } else if (databaseUsers.length > 0) {
      username = databaseUsers[0].username();
    }

    if (username == null) {
      return null;
    } else {
      return new LoggedInUser(username, canManageDynamicUsers);
    }
  }
}
