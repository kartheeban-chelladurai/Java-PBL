package exception;

/**
 * Checked exception thrown when a loan id does not exist.
 */
public class LoanNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public LoanNotFoundException(int loanId) {
        super("No loan found with loan ID " + loanId + ".");
    }
}
