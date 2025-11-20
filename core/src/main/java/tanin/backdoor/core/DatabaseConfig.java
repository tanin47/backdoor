package tanin.backdoor.core;

public class DatabaseConfig {
  public String nickname;
  public String jdbcUrl;
  public String username;
  public String password;

  public DatabaseConfig(String nickname, String jdbcUrl, String username, String password) {
    this.nickname = nickname;
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
  }
}
