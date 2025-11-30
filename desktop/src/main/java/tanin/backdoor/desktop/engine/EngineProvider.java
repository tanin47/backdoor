package tanin.backdoor.desktop.engine;

import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;
import tanin.backdoor.core.engine.Engine;

import java.net.URISyntaxException;
import java.sql.SQLException;

public class EngineProvider implements tanin.backdoor.core.engine.EngineProvider {
  @Override
  public Engine createEngine(DatabaseConfig config, User overwritingUser) throws SQLException, URISyntaxException, Engine.InvalidCredentialsException, Engine.OverwritingUserAndCredentialedJdbcConflictedException, Engine.UnreachableServerException, Engine.InvalidDatabaseNameProbablyException, Engine.GenericConnectionException {
    if (config.jdbcUrl.startsWith("jdbc:sqlite:")) {
      return new SqliteEngine(config, overwritingUser);
    } else {
      return tanin.backdoor.core.engine.EngineProvider.super.createEngine(config, overwritingUser);
    }
  }
}
