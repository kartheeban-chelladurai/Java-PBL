package model;

/**
 * Savings account: no tax up to Rs. 50,000, a flat 1% on anything above it.
 */
public class SavingsAccount extends Account {

    public static final String TYPE = "SAVINGS";

    public SavingsAccount(int accountNumber, String accountHolderName, double balance) {
        super(accountNumber, accountHolderName, balance);
    }

    @Override
    public double calculateTax(double amount) {
        if (amount <= SLAB_ONE_LIMIT) {
            return 0.0;
        }
        // Both the 50,001 - 1,00,000 slab and the above 1,00,000 slab are 1%.
        return amount * 0.01;
    }

    @Override
    public String getAccountType() {
        return TYPE;
    }
}
