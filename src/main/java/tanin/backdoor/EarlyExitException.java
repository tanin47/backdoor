package tanin.backdoor;

import com.renomad.minum.web.IResponse;

public class EarlyExitException extends RuntimeException {
  IResponse response;
  public EarlyExitException(IResponse response) {
    this.response = response;
  }
}
