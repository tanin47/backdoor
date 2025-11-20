package tanin.backdoor.web;

import com.renomad.minum.web.IResponse;

public class EarlyExitException extends RuntimeException {
  public IResponse response;

  public EarlyExitException(IResponse response) {
    this.response = response;
  }
}
