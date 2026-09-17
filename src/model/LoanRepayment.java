package model;

import java.time.LocalDate;

/**
 * Immutable record of one repayment made against a loan, built the same way as
 * {@link Transaction}: final fields, getters only.
 */
public class LoanRepayment {

    private static int nextRepaymentId = 1;

    private final int repaymentId;
    private final int loanId;
    private final double amount;
    private final String repaymentDate;

    /** Creates a brand new repayment, stamped with today's date. */
    public LoanRepayment(int loanId, double amount) {
        this(nextRepaymentId++, loanId, amount, LocalDate.now().format(Loan.DATE_FORMAT));
    }

    /** Rebuilds a repayment that was read back from loan_repayments.txt. */
    public LoanRepayment(int repaymentId, int loanId, double amount, String repaymentDate) {
        this.repaymentId = repaymentId;
        this.loanId = loanId;
        this.amount = amount;
        this.repaymentDate = repaymentDate;
        if (repaymentId >= nextRepaymentId) {
            nextRepaymentId = repaymentId + 1;
        }
    }

    public int getRepaymentId() {
        return repaymentId;
    }

    public int getLoanId() {
        return loanId;
    }

    public double getAmount() {
        return amount;
    }

    public String getRepaymentDate() {
        return repaymentDate;
    }

    public static int getNextRepaymentId() {
        return nextRepaymentId;
    }

    /** Resets the id sequence; used when the repayment log is reloaded. */
    public static void resetIdSequence() {
        nextRepaymentId = 1;
    }

    /** One pipe separated line, the exact format stored in loan_repayments.txt. */
    public String toFileLine() {
        StringBuilder builder = new StringBuilder();
        builder.append(repaymentId).append('|')
               .append(loanId).append('|')
               .append(amount).append('|')
               .append(repaymentDate);
        return builder.toString();
    }

    /** One row of the REPAYMENT HISTORY table. */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("%-12d", repaymentId))
               .append(String.format("Rs.%-15.2f", amount))
               .append(repaymentDate);
        return builder.toString();
    }
}
