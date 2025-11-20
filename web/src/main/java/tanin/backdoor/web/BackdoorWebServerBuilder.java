package tanin.backdoor.web;

import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.User;

import java.util.ArrayList;

import static tanin.backdoor.core.EncryptionHelper.generateRandomString;

public class BackdoorWebServerBuilder {

  ArrayList<DatabaseConfig> databaseConfigs = new ArrayList<>();
  int port = 0;
  int sslPort = 0;
  ArrayList<User> users = new ArrayList<>();
  // TODO: Support configuring the secret key
  String secretKey = generateRandomString(32);

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

  public BackdoorWebServerBuilder addUser(String username, String password) {
    this.users.add(new User(username, password));
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
      users.toArray(new User[0]),
      secretKey
    );
  }
}
