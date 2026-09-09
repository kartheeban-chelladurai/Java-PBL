package exception;

/**
 * Checked exception thrown when an account cannot cover an amount plus its tax.
 */
public class InsufficientBalanceException extends Exception {

    private static final long serialVersionUID = 1L;

    private final double required;
    private final double available;

    public InsufficientBalanceException(String message) {
        super(message);
        this.required = 0.0;
        this.available = 0.0;
    }

    public InsufficientBalanceException(double required, double available) {
        super(String.format("Insufficient balance. Required Rs.%.2f (amount + tax) but only Rs.%.2f is available.",
                required, available));
        this.required = required;
        this.available = available;
    }

    public double getRequired() {
        return required;
    }

    public double getAvailable() {
        return available;
    }
}
