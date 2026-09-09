package model;

/**
 * Base type for every kind of bank account.
 *
 * Demonstrates: abstraction (abstract class + abstract methods), encapsulation
 * (private fields exposed through getters/setters) and static members
 * (a running counter of the accounts currently held by the bank).
 */
public abstract class Account {

    /** Slab boundaries shared by every account type (amounts in rupees). */
    protected static final double SLAB_ONE_LIMIT = 50000.0;
    protected static final double SLAB_TWO_LIMIT = 100000.0;

    /** Running total of accounts currently open in the bank. */
    private static int totalAccounts = 0;

    private final int accountNumber;
    private String accountHolderName;
    private double balance;

    protected Account(int accountNumber, String accountHolderName, double balance) {
        this.accountNumber = accountNumber;
        this.accountHolderName = accountHolderName;
        this.balance = balance;
        totalAccounts++;
    }

    /** Tax deducted when {@code amount} leaves this account. */
    public abstract double calculateTax(double amount);

    /** Human readable account type, also used as the on-disk type marker. */
    public abstract String getAccountType();

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    /** Adds money to the balance. Callers validate the amount first. */
    public void credit(double amount) {
        this.balance += amount;
    }

    /** Removes money from the balance. Callers validate the balance first. */
    public void debit(double amount) {
        this.balance -= amount;
    }

    public static int getTotalAccounts() {
        return totalAccounts;
    }

    /** Called when an account is closed so the counter tracks open accounts. */
    public static void decrementTotalAccounts() {
        if (totalAccounts > 0) {
            totalAccounts--;
        }
    }

    /** Resets the counter; used when the account list is reloaded from disk. */
    public static void resetTotalAccounts() {
        totalAccounts = 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Account No   : ").append(accountNumber).append('\n');
        builder.append("Holder Name  : ").append(accountHolderName).append('\n');
        builder.append("Account Type : ").append(getAccountType()).append('\n');
        builder.append("Balance      : Rs. ").append(String.format("%.2f", balance));
        return builder.toString();
    }
}
