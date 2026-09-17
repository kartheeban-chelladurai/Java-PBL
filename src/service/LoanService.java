package service;

import java.util.ArrayList;
import java.util.List;

import exception.InsufficientBalanceException;
import exception.InvalidAccountException;
import exception.InvalidRepaymentException;
import exception.LoanNotEligibleException;
import exception.LoanNotFoundException;
import model.Account;
import model.EducationLoan;
import model.Loan;
import model.LoanRepayment;
import model.PersonalLoan;
import util.FileManager;
import util.Validation;

/**
 * Business layer of the loan module, built the same way as {@link BankService}:
 * the loans and repayments live in ArrayLists and every change is pushed to
 * disk through {@link FileManager}.
 *
 * It holds a reference to the existing {@link BankService} so a disbursement or
 * a repayment moves the customer's real account balance and is written into the
 * existing transaction history.
 */
public class LoanService {

    /** Eligibility rule 1: the account must hold at least this much. */
    public static final double MINIMUM_BALANCE_FOR_LOAN = 10000.0;

    /** Eligibility rule 2: a loan may not exceed this multiple of the balance. */
    public static final int MAXIMUM_LOAN_MULTIPLE = 5;

    private final List<Loan> loans;
    private final List<LoanRepayment> repayments;
    private final FileManager fileManager;
    private final BankService bankService;

    public LoanService(BankService bankService) {
        this(bankService, new FileManager());
    }

    public LoanService(BankService bankService, FileManager fileManager) {
        this.bankService = bankService;
        this.fileManager = fileManager;
        Loan.resetIdSequence();
        LoanRepayment.resetIdSequence();
        this.loans = new ArrayList<>(fileManager.loadLoans());
        this.repayments = new ArrayList<>(fileManager.loadLoanRepayments());
    }

    // ------------------------------------------------------------------
    // 1. Loan eligibility
    // ------------------------------------------------------------------

    /**
     * The largest loan this account may take: 5 x the current balance.
     *
     * Balance Rs.50,000 -> maximum eligible loan Rs.2,50,000
     */
    public double getMaximumEligibleLoan(int accountNumber) throws InvalidAccountException {
        Account account = bankService.findAccount(accountNumber);
        return Validation.round(account.getBalance() * MAXIMUM_LOAN_MULTIPLE);
    }

    /**
     * Applies the four eligibility rules in order:
     *
     *   1. the account exists                        (InvalidAccountException)
     *   2. the account is active - in this system an account only stays in
     *      accounts.txt while it is open, so a closed account simply is not
     *      found by rule 1
     *   3. the balance is at least Rs.10,000
     *   4. the requested amount is at most 5 x the balance
     *
     * A zero or negative request is refused before any of that.
     *
     * @throws LoanNotEligibleException carrying the reason to show the customer
     */
    public void checkEligibility(int accountNumber, double requestedAmount)
            throws InvalidAccountException, LoanNotEligibleException {
        Account account = bankService.findAccount(accountNumber);
        double amount = Validation.round(requestedAmount);

        if (amount <= 0.0) {
            throw new LoanNotEligibleException("The requested loan amount must be greater than zero.");
        }
        if (account.getBalance() < MINIMUM_BALANCE_FOR_LOAN) {
            throw new LoanNotEligibleException(String.format(
                    "A minimum balance of Rs.%.2f is required, but the account holds only Rs.%.2f.",
                    MINIMUM_BALANCE_FOR_LOAN, account.getBalance()));
        }
        double maximum = getMaximumEligibleLoan(accountNumber);
        if (amount > maximum) {
            throw new LoanNotEligibleException(
                    "Requested amount exceeds the maximum eligible loan amount.");
        }
    }

    // ------------------------------------------------------------------
    // 2. Apply for a loan
    // ------------------------------------------------------------------

    /**
     * Builds the loan object that WOULD be created for these inputs, so the
     * console can show the interest, total repayment and EMI before the
     * customer confirms. Nothing is saved and no balance is touched.
     */
    public Loan previewLoan(int accountNumber, String loanType, double amount, int tenureMonths) {
        if (EducationLoan.TYPE.equalsIgnoreCase(loanType)) {
            return new EducationLoan(0, accountNumber, Validation.round(amount), tenureMonths, 0.0,
                    Loan.STATUS_APPROVED, "");
        }
        return new PersonalLoan(0, accountNumber, Validation.round(amount), tenureMonths, 0.0,
                Loan.STATUS_APPROVED, "");
    }

    /**
     * Runs the eligibility rules once more, approves the loan, pays the
     * principal into the customer's account and saves everything.
     *
     * The loan is created APPROVED and becomes ACTIVE straight away, because in
     * this simple system the money is handed over the moment it is approved.
     */
    public Loan applyForLoan(int accountNumber, String loanType, double amount, int tenureMonths)
            throws InvalidAccountException, LoanNotEligibleException {
        if (tenureMonths < Validation.MIN_TENURE_MONTHS
                || tenureMonths > Validation.MAX_TENURE_MONTHS) {
            throw new LoanNotEligibleException("Tenure must be between "
                    + Validation.MIN_TENURE_MONTHS + " and " + Validation.MAX_TENURE_MONTHS
                    + " months.");
        }
        checkEligibility(accountNumber, amount);

        double principal = Validation.round(amount);
        Loan loan;
        if (EducationLoan.TYPE.equalsIgnoreCase(loanType)) {
            loan = new EducationLoan(accountNumber, principal, tenureMonths);
        } else {
            loan = new PersonalLoan(accountNumber, principal, tenureMonths);
        }
        loans.add(loan);

        // The approved amount is paid into the account and shows up in the
        // existing transaction history as LOAN_DISBURSED.
        bankService.creditLoanDisbursement(accountNumber, principal);
        loan.setStatus(Loan.STATUS_ACTIVE);
        fileManager.saveLoans(loans);
        return loan;
    }

    // ------------------------------------------------------------------
    // 3. Loan details and status
    // ------------------------------------------------------------------

    /** Looks a loan up or fails with a checked exception. */
    public Loan findLoan(int loanId) throws LoanNotFoundException {
        for (Loan loan : loans) {
            if (loan.getLoanId() == loanId) {
                return loan;
            }
        }
        throw new LoanNotFoundException(loanId);
    }

    /** Every loan belonging to one account, closed loans included. */
    public List<Loan> getLoansForAccount(int accountNumber) {
        List<Loan> result = new ArrayList<>();
        for (Loan loan : loans) {
            if (loan.getAccountNumber() == accountNumber) {
                result.add(loan);
            }
        }
        return result;
    }

    public List<Loan> getAllLoans() {
        return new ArrayList<>(loans);
    }

    /** Just the status word, for the "View Loan Status" screen. */
    public String getLoanStatus(int loanId) throws LoanNotFoundException {
        return findLoan(loanId).getStatus();
    }

    // ------------------------------------------------------------------
    // 4. Repayment
    // ------------------------------------------------------------------

    /**
     * Takes one repayment: the money leaves the customer's account, the loan's
     * paid/remaining figures move, and the loan is marked COMPLETED once
     * nothing is left to pay.
     *
     * Order of checks, so nothing is half done:
     *   1. the loan exists
     *   2. the loan is not already COMPLETED
     *   3. the amount is greater than zero
     *   4. the amount is not more than the remaining amount
     *   5. the account can afford it   (InsufficientBalanceException)
     */
    public LoanRepayment makeRepayment(int loanId, double amount)
            throws LoanNotFoundException, InvalidRepaymentException, InvalidAccountException,
                   InsufficientBalanceException {
        Loan loan = findLoan(loanId);
        if (loan.isCompleted()) {
            throw new InvalidRepaymentException(
                    "Loan " + loanId + " is already fully repaid. No further repayment is needed.");
        }
        double value = Validation.round(amount);
        if (value <= 0.0) {
            throw new InvalidRepaymentException("The repayment amount must be greater than zero.");
        }
        if (value > loan.getRemainingAmount()) {
            throw new InvalidRepaymentException(String.format(
                    "The repayment of Rs.%.2f is more than the remaining amount of Rs.%.2f.",
                    value, loan.getRemainingAmount()));
        }

        // Deducts from the account only after every loan side check has passed.
        bankService.debitLoanRepayment(loan.getAccountNumber(), value);

        loan.recordPayment(value);
        LoanRepayment repayment = new LoanRepayment(loanId, value);
        repayments.add(repayment);
        fileManager.appendLoanRepayment(repayment);
        fileManager.saveLoans(loans);
        return repayment;
    }

    // ------------------------------------------------------------------
    // 5. Repayment history
    // ------------------------------------------------------------------

    /** Every repayment made against one loan, read back from the saved log. */
    public List<LoanRepayment> getRepaymentHistory(int loanId) throws LoanNotFoundException {
        findLoan(loanId);
        List<LoanRepayment> history = new ArrayList<>();
        for (LoanRepayment repayment : fileManager.loadLoanRepayments()) {
            if (repayment.getLoanId() == loanId) {
                history.add(repayment);
            }
        }
        return history;
    }

    /** Sum of every repayment made against one loan. */
    public double getTotalPaid(int loanId) throws LoanNotFoundException {
        double total = 0.0;
        for (LoanRepayment repayment : getRepaymentHistory(loanId)) {
            total += repayment.getAmount();
        }
        return Validation.round(total);
    }
}
