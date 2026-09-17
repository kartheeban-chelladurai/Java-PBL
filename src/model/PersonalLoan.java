package model;

/**
 * Personal loan: 10% simple interest per annum.
 */
public class PersonalLoan extends Loan {

    public static final String TYPE = "PERSONAL";
    public static final double ANNUAL_INTEREST_RATE = 10.0;

    public PersonalLoan(int accountNumber, double principalAmount, int tenureMonths) {
        super(accountNumber, principalAmount, tenureMonths);
    }

    public PersonalLoan(int loanId, int accountNumber, double principalAmount, int tenureMonths,
                        double amountPaid, String status, String loanDate) {
        super(loanId, accountNumber, principalAmount, tenureMonths, amountPaid, status, loanDate);
    }

    @Override
    public double getAnnualInterestRate() {
        return ANNUAL_INTEREST_RATE;
    }

    @Override
    public String getLoanType() {
        return TYPE;
    }
}
