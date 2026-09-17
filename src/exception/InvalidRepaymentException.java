package exception;

/**
 * Checked exception thrown when a repayment cannot be accepted - it is not a
 * positive amount, it is larger than the outstanding balance, or the loan has
 * already been fully repaid.
 */
public class InvalidRepaymentException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidRepaymentException(String message) {
        super(message);
    }
}
