package exception;

/**
 * Checked exception thrown when closing an account that still holds money.
 * The remaining balance is carried on the exception so the menu can offer a
 * settlement withdrawal before retrying the close.
 */
public class AccountNotEmptyException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int accountNumber;
    private final double remainingBalance;

    public AccountNotEmptyException(int accountNumber, double remainingBalance) {
        super(String.format("Account %d still holds Rs.%.2f. Its balance must be zero before it can be closed.",
                accountNumber, remainingBalance));
        this.accountNumber = accountNumber;
        this.remainingBalance = remainingBalance;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public double getRemainingBalance() {
        return remainingBalance;
    }
}
