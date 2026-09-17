package exception;

/**
 * Checked exception thrown when a customer fails one of the eligibility rules.
 * The reason is carried on the exception so the console can print it under a
 * "Reason:" heading.
 */
public class LoanNotEligibleException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String reason;

    public LoanNotEligibleException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
