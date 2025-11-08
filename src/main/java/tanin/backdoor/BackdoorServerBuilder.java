package tanin.backdoor;

import java.util.ArrayList;

import static tanin.backdoor.EncryptionHelper.generateRandomString;

public class BackdoorServerBuilder {

  ArrayList<DatabaseConfig> databaseConfigs = new ArrayList<>();
  int port = 0;
  int sslPort = 0;
  ArrayList<User> users = new ArrayList<>();
  // TODO: Support configuring the secret key
  String secretKey = generateRandomString(32);

  public BackdoorServerBuilder withPort(int port) {
    this.port = port;
    return this;
  }

  public BackdoorServerBuilder withSslPort(int sslPort) {
    this.sslPort = sslPort;
    return this;
  }

  public BackdoorServerBuilder withSecretKey(String secretKey) {
    this.secretKey = secretKey;
    return this;
  }

  public BackdoorServerBuilder addUser(String username, String password) {
    this.users.add(new User(username, password));
    return this;
  }

  public BackdoorServerBuilder addDatabaseConfig(String nickname, String url, String username, String password) {
    this.databaseConfigs.add(new DatabaseConfig(nickname, url, username, password));
    return this;
  }

  public BackdoorServer build() {
    return new BackdoorServer(
      databaseConfigs.toArray(new DatabaseConfig[0]),
      port,
      sslPort,
      users.toArray(new User[0]),
      secretKey
    );
  }
}
