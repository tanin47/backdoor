package tanin.backdoor;

public record User(String username, String password, String databaseNickname) {

  public User(String username, String password) {
    this(username, password, null);
  }
}
