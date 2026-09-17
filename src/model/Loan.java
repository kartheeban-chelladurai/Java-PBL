package model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Base type for every kind of loan the bank offers.
 *
 * Mirrors the way {@link Account} is built: an abstract parent holding the
 * common state, with each subclass supplying its own interest rate through an
 * overridden method.
 *
 * All the money maths lives here so both loan types share exactly the same
 * SIMPLE INTEREST formula (see {@link #calculateInterest()}).
 */
public abstract class Loan {

    /** The four states a loan can be in. */
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** Loan ids start at 501 so they never look like account numbers. */
    private static int nextLoanId = 501;

    public static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final int loanId;
    private final int accountNumber;
    private final double principalAmount;
    private final int tenureMonths;
    private double amountPaid;
    private String status;
    private final String loanDate;

    /** Creates a brand new loan, stamped with today's date. */
    protected Loan(int accountNumber, double principalAmount, int tenureMonths) {
        this(nextLoanId++, accountNumber, principalAmount, tenureMonths, 0.0, STATUS_APPROVED,
                LocalDate.now().format(DATE_FORMAT));
    }

    /** Rebuilds a loan that was read back from loans.txt. */
    protected Loan(int loanId, int accountNumber, double principalAmount, int tenureMonths,
                   double amountPaid, String status, String loanDate) {
        this.loanId = loanId;
        this.accountNumber = accountNumber;
        this.principalAmount = principalAmount;
        this.tenureMonths = tenureMonths;
        this.amountPaid = amountPaid;
        this.status = status;
        this.loanDate = loanDate;
        if (loanId >= nextLoanId) {
            nextLoanId = loanId + 1;
        }
    }

    /** Annual interest rate as a percentage. Overridden by every loan type. */
    public abstract double getAnnualInterestRate();

    /** Human readable loan type, also used as the on-disk type marker. */
    public abstract String getLoanType();

    // ------------------------------------------------------------------
    // Loan maths - SIMPLE INTEREST, exactly as taught in class
    // ------------------------------------------------------------------

    /**
     * Interest = Principal x Rate x Time
     *
     *   Rate is the annual rate as a decimal  (10% -> 10/100 = 0.10)
     *   Time is the tenure in YEARS           (24 months -> 24/12 = 2)
     *
     * Worked example: 2,00,000 x 10/100 x 2 = 40,000
     */
    public double calculateInterest() {
        double years = tenureMonths / 12.0;
        return round(principalAmount * (getAnnualInterestRate() / 100.0) * years);
    }

    /** Total repayment = principal + interest. 2,00,000 + 40,000 = 2,40,000 */
    public double getTotalRepayment() {
        return round(principalAmount + calculateInterest());
    }

    /** Monthly EMI = total repayment / tenure in months. 2,40,000 / 24 = 10,000 */
    public double getMonthlyEmi() {
        return round(getTotalRepayment() / tenureMonths);
    }

    /** How much of the total repayment is still outstanding. */
    public double getRemainingAmount() {
        return round(getTotalRepayment() - amountPaid);
    }

    /**
     * Books a repayment against this loan and moves it to COMPLETED once the
     * remaining amount reaches zero. Callers validate the amount first.
     */
    public void recordPayment(double amount) {
        this.amountPaid = round(this.amountPaid + amount);
        if (getRemainingAmount() <= 0.0) {
            this.status = STATUS_COMPLETED;
        } else if (STATUS_APPROVED.equals(this.status)) {
            this.status = STATUS_ACTIVE;
        }
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    // ------------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------------

    public int getLoanId() {
        return loanId;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public double getPrincipalAmount() {
        return principalAmount;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public double getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(double amountPaid) {
        this.amountPaid = amountPaid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLoanDate() {
        return loanDate;
    }

    public static int getNextLoanId() {
        return nextLoanId;
    }

    /** Resets the id sequence; used when the loan list is reloaded from disk. */
    public static void resetIdSequence() {
        nextLoanId = 501;
    }

    /** Rounds a money value to two decimal places. */
    protected static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * One pipe separated line, the exact format stored in loans.txt. The
     * derived columns (interest, total, remaining, emi) are written out too so
     * the file matches the loans table layout and can be read by eye.
     */
    public String toFileLine() {
        StringBuilder builder = new StringBuilder();
        builder.append(loanId).append('|')
               .append(accountNumber).append('|')
               .append(getLoanType()).append('|')
               .append(principalAmount).append('|')
               .append(getAnnualInterestRate()).append('|')
               .append(tenureMonths).append('|')
               .append(calculateInterest()).append('|')
               .append(getTotalRepayment()).append('|')
               .append(amountPaid).append('|')
               .append(getRemainingAmount()).append('|')
               .append(getMonthlyEmi()).append('|')
               .append(status).append('|')
               .append(loanDate);
        return builder.toString();
    }

    /** The full LOAN DETAILS block shown by the console. */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Loan ID          : ").append(loanId).append('\n');
        builder.append("Account Number   : ").append(accountNumber).append('\n');
        builder.append("Loan Type        : ").append(getLoanType()).append('\n');
        builder.append(String.format("Principal Amount : Rs.%.2f%n", principalAmount));
        builder.append(String.format("Interest Rate    : %.0f%% per annum%n", getAnnualInterestRate()));
        builder.append("Tenure           : ").append(tenureMonths).append(" months").append('\n');
        builder.append(String.format("Total Interest   : Rs.%.2f%n", calculateInterest()));
        builder.append(String.format("Total Repayment  : Rs.%.2f%n", getTotalRepayment()));
        builder.append(String.format("Amount Paid      : Rs.%.2f%n", amountPaid));
        builder.append(String.format("Remaining Amount : Rs.%.2f%n", getRemainingAmount()));
        builder.append(String.format("Monthly EMI      : Rs.%.2f%n", getMonthlyEmi()));
        builder.append("Loan Date        : ").append(loanDate).append('\n');
        builder.append("Status           : ").append(status);
        return builder.toString();
    }
}
