package redirect_service.exception;

public class RedirectNotFoundException extends RuntimeException {

    public RedirectNotFoundException() {
        super("Redirect link was not found");
    }
}
