package model;

/**
 * Education loan: 7% simple interest per annum, cheaper than a personal loan.
 */
public class EducationLoan extends Loan {

    public static final String TYPE = "EDUCATION";
    public static final double ANNUAL_INTEREST_RATE = 7.0;

    public EducationLoan(int accountNumber, double principalAmount, int tenureMonths) {
        super(accountNumber, principalAmount, tenureMonths);
    }

    public EducationLoan(int loanId, int accountNumber, double principalAmount, int tenureMonths,
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
