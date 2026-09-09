package exception;

/**
 * Checked exception thrown when a new account reuses an existing account number.
 */
public class DuplicateAccountException extends Exception {

    private static final long serialVersionUID = 1L;

    public DuplicateAccountException(int accountNumber) {
        super("Account number " + accountNumber + " is already in use. Choose another one.");
    }
}
