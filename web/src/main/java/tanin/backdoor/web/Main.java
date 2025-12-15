package tanin.backdoor.web;

import tanin.backdoor.core.BackdoorCoreServer;
import tanin.backdoor.core.DatabaseConfig;
import tanin.backdoor.core.EncryptionHelper;
import tanin.backdoor.core.DatabaseUser;
import tanin.ejwf.MinumBuilder;

import java.util.ArrayList;

import static tanin.backdoor.core.BackdoorCoreServer.stripeSurroundingDoubleQuotes;

public class Main {

  public static void main(String[] args) throws Exception {
    BackdoorCoreServer.initSentry(BackdoorWebServer.SENTRY_PROPERTIES != null);

    var databaseConfigs = new ArrayList<DatabaseConfig>();
    int port = 0;
    int sslPort = 0;
    var users = new ArrayList<CommandLineUser>();
    var secretKey = EncryptionHelper.generateRandomString(32);
    String backdoorDatabaseJdbcUrl = null;

    if (MinumBuilder.MODE == MinumBuilder.Mode.Dev) {
      databaseConfigs.add(new DatabaseConfig("postgres", "postgres://127.0.0.1:5432/backdoor_test", "backdoor_test_user", "test"));
//      databaseConfigs.add(new DatabaseConfig("clickhouse", "jdbc:ch://localhost:8123", "abacus_dev_user", "dev"));
//      databaseConfigs.add(new DatabaseConfig("postgres", "postgres://127.0.0.1:5432/backdoor_test", null, null));
      databaseConfigs.add(new DatabaseConfig("clickhouse", "jdbc:ch://localhost:8123?user=backdoor&password=test_ch", null, null));
      users.add(new CommandLineUser("masked_test", "1234"));
      secretKey = "testkey";
      port = 9090;
      backdoorDatabaseJdbcUrl = "postgres://backdoor_test_user:test@127.0.0.1:5432/backdoor_test";
    }


    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-url":
          if (i + 1 < args.length) {
            var urls = stripeSurroundingDoubleQuotes(args[++i]).split(",");

            for (var url : urls) {
              databaseConfigs.add(new DatabaseConfig("database_" + databaseConfigs.size(), url.trim(), null, null));
            }
          }
          break;
        case "-port":
          if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
          break;
        case "-backdoor-jdbc-url":
          if (i + 1 < args.length) backdoorDatabaseJdbcUrl = args[++i];
          break;
        case "-ssl-port":
          if (i + 1 < args.length) sslPort = Integer.parseInt(args[++i]);
          break;
        case "-secret-key":
          if (i + 1 < args.length) secretKey = args[++i];
          break;
        case "-user":
          if (i + 1 < args.length) {
            var pairs = args[++i].split(",");

            for (var pair : pairs) {
              String[] userAndPass = pair.split(":", 2);
              if (userAndPass.length != 2) {
                throw new IllegalArgumentException("Invalid user argument. The format should follow: `user:pass,user2:pass2`");
              }
              users.add(new CommandLineUser(userAndPass[0], userAndPass[1]));
            }
          }
          break;
      }
    }

    if (port == 0) {
      throw new RuntimeException("You must specify the port using `-port <PORT>`");
    }

    var main = new BackdoorWebServer(
      databaseConfigs.toArray(new DatabaseConfig[0]),
      port,
      sslPort,
      users.toArray(new CommandLineUser[0]),
      secretKey,
      backdoorDatabaseJdbcUrl
    );
    var minum = main.start();
    minum.block();
  }
}
