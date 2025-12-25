package tanin.backdoor.core.engine;

import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.DatabaseUser;

import java.net.URISyntaxException;
import java.sql.SQLException;

public interface EngineProvider {
  public default Engine createEngine(DatabaseConfig config, DatabaseUser overwritingUser) throws SQLException, URISyntaxException, Engine.InvalidCredentialsException, Engine.OverwritingUserAndCredentialedJdbcConflictedException, Engine.UnreachableServerException, Engine.InvalidDatabaseNameProbablyException, Engine.GenericConnectionException {
    if (config.jdbcUrl.isBlank()) {
      throw new UnsupportedOperationException("The database URL cannot be blank.");
    } else if (config.jdbcUrl.startsWith("jdbc:postgres") || config.jdbcUrl.startsWith("postgres")) {
      return new PostgresEngine(config, overwritingUser);
    } else if (config.jdbcUrl.startsWith("jdbc:ch:")) {
      return new ClickHouseEngine(config, overwritingUser);
    } else {
      throw new UnsupportedOperationException(config.jdbcUrl + " is not supported. Please make your feature request at https://github.com/tanin47/backdoor.");
    }
  }
}
