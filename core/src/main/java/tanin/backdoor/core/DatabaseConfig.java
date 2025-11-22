package tanin.backdoor.core;

public class DatabaseConfig {
  public String nickname;
  public String jdbcUrl;
  public String username;
  public String password;
  public boolean isAdHoc;

  public DatabaseConfig(String nickname, String jdbcUrl, String username, String password) {
    this(
      nickname,
      jdbcUrl,
      username,
      password,
      false
    );
  }

  public DatabaseConfig(String nickname, String jdbcUrl, String username, String password, boolean isAdHoc) {
    this.nickname = nickname;
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
    this.isAdHoc = isAdHoc;
  }
}
