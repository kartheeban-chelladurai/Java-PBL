package exception;

/**
 * Checked exception thrown when an account number does not exist in the bank.
 */
public class InvalidAccountException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidAccountException(String message) {
        super(message);
    }

    public InvalidAccountException(int accountNumber) {
        super("No account found with account number " + accountNumber + ".");
    }
}
