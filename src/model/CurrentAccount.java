package model;

/**
 * Current account: no tax up to Rs. 50,000, 1% up to Rs. 1,00,000 and 2% beyond.
 */
public class CurrentAccount extends Account {

    public static final String TYPE = "CURRENT";

    public CurrentAccount(int accountNumber, String accountHolderName, double balance) {
        super(accountNumber, accountHolderName, balance);
    }

    @Override
    public double calculateTax(double amount) {
        if (amount <= SLAB_ONE_LIMIT) {
            return 0.0;
        } else if (amount <= SLAB_TWO_LIMIT) {
            return amount * 0.01;
        } else {
            return amount * 0.02;
        }
    }

    @Override
    public String getAccountType() {
        return TYPE;
    }
}
