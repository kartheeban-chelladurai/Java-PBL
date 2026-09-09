package exception;

/**
 * Checked exception thrown when a transfer is structurally impossible,
 * for example transferring money from an account to itself.
 */
public class InvalidTransferException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidTransferException(String message) {
        super(message);
    }
}
