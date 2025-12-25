package tanin.backdoor.web;

import tanin.backdoor.core.DatabaseConfig;

import java.util.ArrayList;

import static tanin.backdoor.core.EncryptionHelper.generateRandomString;

public class BackdoorWebServerBuilder {

  ArrayList<DatabaseConfig> databaseConfigs = new ArrayList<>();
  int port = 0;
  int sslPort = 0;
  ArrayList<SourceCodeUser> users = new ArrayList<>();
  String secretKey = generateRandomString(32);
  String backdoorDatabaseJdbcUrl = null;

  public BackdoorWebServerBuilder withPort(int port) {
    this.port = port;
    return this;
  }

  public BackdoorWebServerBuilder withSslPort(int sslPort) {
    this.sslPort = sslPort;
    return this;
  }

  public BackdoorWebServerBuilder withSecretKey(String secretKey) {
    this.secretKey = secretKey;
    return this;
  }

  public BackdoorWebServerBuilder withBackdoorDatabaseJdbcUrl(String url) {
    this.backdoorDatabaseJdbcUrl = url;
    return this;
  }

  public BackdoorWebServerBuilder addUser(String username, String password) {
    this.users.add(new SourceCodeUser(username, password));
    return this;
  }

  public BackdoorWebServerBuilder addDatabaseConfig(String nickname, String url, String username, String password) {
    this.databaseConfigs.add(new DatabaseConfig(nickname, url, username, password));
    return this;
  }

  public BackdoorWebServer build() {
    return new BackdoorWebServer(
      databaseConfigs.toArray(new DatabaseConfig[0]),
      port,
      sslPort,
      users.toArray(new SourceCodeUser[0]),
      secretKey,
      backdoorDatabaseJdbcUrl
    );
  }
}
