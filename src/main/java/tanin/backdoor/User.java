package tanin.backdoor;

public record User(String username, String password, boolean isPg) {
  public User(String username, String password) {
    this(username, password, false);
  }
}
