import java.io.File;
import java.util.List;

import exception.AccountNotEmptyException;
import exception.DuplicateAccountException;
import exception.InsufficientBalanceException;
import exception.InvalidAccountException;
import exception.InvalidRepaymentException;
import exception.InvalidTransferException;
import exception.LoanNotEligibleException;
import exception.LoanNotFoundException;
import model.Account;
import model.CurrentAccount;
import model.EducationLoan;
import model.Loan;
import model.LoanRepayment;
import model.PersonalLoan;
import model.SavingsAccount;
import model.Transaction;
import service.BankService;
import service.LoanService;
import util.FileManager;
import util.Validation;

/**
 * Hand rolled test harness - core Java only, no external test library.
 *
 * Compile: javac -d out-test -cp out $(find test -name "*.java")
 * Run    : java -cp out:out-test BankSystemTest
 */
public class BankSystemTest {

    private static int passed = 0;
    private static int failed = 0;
    private static final String TEST_DATA_DIR = "data-test";

    public static void main(String[] args) {
        cleanTestData();

        testSavingsTaxSlabs();
        testCurrentTaxSlabs();
        testWorkedExample();
        testDepositIsUntaxed();
        testInsufficientBalanceLeavesBalanceUnchanged();
        testInvalidAccountIsRejected();
        testDuplicateAccountIsRejected();
        testTransferMovesMoneyAndTaxesSenderOnly();
        testTransferToUnknownAccountIsAtomic();
        testTransferToSelfIsRejected();
        testCloseAccountRequiresZeroBalance();
        testSettleAndCloseAccount();
        testHistoryIsReadBackFromFile();
        testPersistenceAcrossRestart();
        testValidationRules();

        // ---- Loan Management feature ----
        testLoanInterestAndEmiMaths();
        testLoanTypePolymorphism();
        testLoanEligibilityRules();
        testApplyForLoanCreditsAccount();
        testLoanRepaymentDebitsAccount();
        testInvalidRepaymentsAreRejected();
        testRepaymentNeedsAccountBalance();
        testLoanCompletion();
        testRepaymentHistoryIsReadBackFromFile();
        testLoansSurviveRestart();
        testTaxCalculatorStillWorksAfterLoanFeature();

        cleanTestData();

        System.out.println();
        System.out.println("==================================================");
        System.out.println("Tests passed: " + passed + "   failed: " + failed);
        System.out.println("==================================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Tax rules
    // ------------------------------------------------------------------

    private static void testSavingsTaxSlabs() {
        Account savings = new SavingsAccount(1, "Slab Tester", 0);
        assertEquals("savings: 10,000 is untaxed", 0.0, savings.calculateTax(10000));
        assertEquals("savings: 50,000 is untaxed (slab edge)", 0.0, savings.calculateTax(50000));
        assertEquals("savings: 50,001 taxed at 1%", 500.01, savings.calculateTax(50001));
        assertEquals("savings: 1,00,000 taxed at 1%", 1000.0, savings.calculateTax(100000));
        assertEquals("savings: 1,50,000 still taxed at 1%", 1500.0, savings.calculateTax(150000));
    }

    private static void testCurrentTaxSlabs() {
        Account current = new CurrentAccount(2, "Slab Tester", 0);
        assertEquals("current: 10,000 is untaxed", 0.0, current.calculateTax(10000));
        assertEquals("current: 50,000 is untaxed (slab edge)", 0.0, current.calculateTax(50000));
        assertEquals("current: 50,001 taxed at 1%", 500.01, current.calculateTax(50001));
        assertEquals("current: 1,00,000 taxed at 1%", 1000.0, current.calculateTax(100000));
        assertEquals("current: 1,00,001 taxed at 2%", 2000.02, current.calculateTax(100001));
        assertEquals("current: 1,50,000 taxed at 2%", 3000.0, current.calculateTax(150000));
    }

    /** The worked example from the project brief. */
    private static void testWorkedExample() {
        BankService bank = newBank();
        try {
            bank.createAccount(1001, "Worked Example", "CURRENT", 300000);
            Transaction t = bank.withdraw(1001, 150000);
            assertEquals("worked example: tax", 3000.0, t.getTax());
            assertEquals("worked example: total deducted", 153000.0, t.getTotal());
            assertEquals("worked example: remaining balance", 147000.0, bank.checkBalance(1001));
        } catch (Exception e) {
            fail("worked example threw " + e);
        }
    }

    private static void testDepositIsUntaxed() {
        BankService bank = newBank();
        try {
            bank.createAccount(2001, "Deposit Tester", "SAVINGS", 1000);
            Transaction t = bank.deposit(2001, 200000);
            assertEquals("deposit: no tax", 0.0, t.getTax());
            assertEquals("deposit: balance", 201000.0, bank.checkBalance(2001));
        } catch (Exception e) {
            fail("deposit threw " + e);
        }
    }

    private static void testInsufficientBalanceLeavesBalanceUnchanged() {
        BankService bank = newBank();
        try {
            bank.createAccount(3001, "Broke Tester", "SAVINGS", 60000);
            // 60,000 + 1% tax = 60,600 > 60,000 balance
            bank.withdraw(3001, 60000);
            fail("insufficient balance: expected InsufficientBalanceException");
        } catch (InsufficientBalanceException e) {
            pass("insufficient balance: rejected withdrawal of amount + tax");
        } catch (Exception e) {
            fail("insufficient balance: wrong exception " + e);
        }
        try {
            assertEquals("insufficient balance: balance untouched", 60000.0, bank.checkBalance(3001));
        } catch (InvalidAccountException e) {
            fail("insufficient balance: account vanished");
        }
    }

    private static void testInvalidAccountIsRejected() {
        BankService bank = newBank();
        try {
            bank.deposit(999999, 100);
            fail("invalid account: expected InvalidAccountException");
        } catch (InvalidAccountException e) {
            pass("invalid account: deposit rejected");
        } catch (Exception e) {
            fail("invalid account: wrong exception " + e);
        }
    }

    private static void testDuplicateAccountIsRejected() {
        BankService bank = newBank();
        try {
            bank.createAccount(4001, "First Holder", "SAVINGS", 500);
            bank.createAccount(4001, "Second Holder", "CURRENT", 500);
            fail("duplicate account: expected DuplicateAccountException");
        } catch (DuplicateAccountException e) {
            pass("duplicate account: rejected");
        } catch (Exception e) {
            fail("duplicate account: wrong exception " + e);
        }
    }

    // ------------------------------------------------------------------
    // Transfer
    // ------------------------------------------------------------------

    private static void testTransferMovesMoneyAndTaxesSenderOnly() {
        BankService bank = newBank();
        try {
            bank.createAccount(5001, "Sender", "CURRENT", 300000);
            bank.createAccount(5002, "Receiver", "SAVINGS", 1000);
            Transaction out = bank.transfer(5001, 5002, 150000);
            assertEquals("transfer: sender taxed at current rate", 3000.0, out.getTax());
            assertEquals("transfer: total debited", 153000.0, out.getTotal());
            assertEquals("transfer: sender balance", 147000.0, bank.checkBalance(5001));
            assertEquals("transfer: receiver gets full amount untaxed", 151000.0,
                    bank.checkBalance(5002));
            List<Transaction> receiverHistory = bank.getTransactionHistory(5002);
            Transaction last = receiverHistory.get(receiverHistory.size() - 1);
            assertTrue("transfer: receiver logged TRANSFER_IN",
                    Transaction.TRANSFER_IN.equals(last.getType()));
            assertTrue("transfer: TRANSFER_IN links back to sender",
                    last.getSecondAccountNumber() == 5001);
        } catch (Exception e) {
            fail("transfer threw " + e);
        }
    }

    private static void testTransferToUnknownAccountIsAtomic() {
        BankService bank = newBank();
        try {
            bank.createAccount(6001, "Atomic Sender", "SAVINGS", 100000);
            bank.transfer(6001, 6099, 1000);
            fail("atomic transfer: expected InvalidAccountException");
        } catch (InvalidAccountException e) {
            pass("atomic transfer: unknown destination rejected");
        } catch (Exception e) {
            fail("atomic transfer: wrong exception " + e);
        }
        try {
            assertEquals("atomic transfer: sender balance untouched", 100000.0,
                    bank.checkBalance(6001));
        } catch (InvalidAccountException e) {
            fail("atomic transfer: sender vanished");
        }

        // And the same for a transfer the sender cannot afford.
        try {
            bank.createAccount(6002, "Atomic Receiver", "SAVINGS", 500);
            bank.transfer(6001, 6002, 200000);
            fail("atomic transfer: expected InsufficientBalanceException");
        } catch (InsufficientBalanceException e) {
            pass("atomic transfer: unaffordable transfer rejected");
        } catch (Exception e) {
            fail("atomic transfer: wrong exception " + e);
        }
        try {
            assertEquals("atomic transfer: sender still untouched", 100000.0,
                    bank.checkBalance(6001));
            assertEquals("atomic transfer: receiver still untouched", 500.0,
                    bank.checkBalance(6002));
        } catch (InvalidAccountException e) {
            fail("atomic transfer: account vanished");
        }
    }

    private static void testTransferToSelfIsRejected() {
        BankService bank = newBank();
        try {
            bank.createAccount(7001, "Self Transfer", "SAVINGS", 10000);
            bank.transfer(7001, 7001, 100);
            fail("self transfer: expected InvalidTransferException");
        } catch (InvalidTransferException e) {
            pass("self transfer: rejected");
        } catch (Exception e) {
            fail("self transfer: wrong exception " + e);
        }
    }

    // ------------------------------------------------------------------
    // Close account
    // ------------------------------------------------------------------

    private static void testCloseAccountRequiresZeroBalance() {
        BankService bank = newBank();
        try {
            bank.createAccount(8001, "Not Empty", "SAVINGS", 2500);
            bank.closeAccount(8001);
            fail("close account: expected AccountNotEmptyException");
        } catch (AccountNotEmptyException e) {
            assertEquals("close account: reports remaining balance", 2500.0, e.getRemainingBalance());
        } catch (Exception e) {
            fail("close account: wrong exception " + e);
        }
        assertTrue("close account: account still open", bank.accountExists(8001));
    }

    private static void testSettleAndCloseAccount() {
        BankService bank = newBank();
        try {
            bank.createAccount(9001, "Closing Holder", "CURRENT", 120000);
            Transaction settlement = bank.settleAndCloseAccount(9001);
            assertEquals("settle and close: full balance paid out", 120000.0, settlement.getAmount());
            assertEquals("settle and close: settlement is tax free", 0.0, settlement.getTax());
            assertTrue("settle and close: account removed", !bank.accountExists(9001));
            List<Transaction> history = bank.readHistoryFromFile(9001);
            Transaction last = history.get(history.size() - 1);
            assertTrue("settle and close: closure logged",
                    Transaction.ACCOUNT_CLOSE.equals(last.getType()));
        } catch (Exception e) {
            fail("settle and close threw " + e);
        }

        // A zero balance account closes straight away.
        try {
            bank.createAccount(9002, "Empty Holder", "SAVINGS", 0);
            bank.closeAccount(9002);
            assertTrue("close account: empty account closed", !bank.accountExists(9002));
        } catch (Exception e) {
            fail("close of empty account threw " + e);
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private static void testHistoryIsReadBackFromFile() {
        BankService bank = newBank();
        try {
            bank.createAccount(10001, "History Holder", "SAVINGS", 100000);
            bank.deposit(10001, 5000);
            bank.withdraw(10001, 60000);
            List<Transaction> history = bank.getTransactionHistory(10001);
            assertTrue("history: three entries recorded", history.size() == 3);
            assertTrue("history: opening entry first",
                    Transaction.ACCOUNT_OPEN.equals(history.get(0).getType()));
            assertTrue("history: withdrawal last",
                    Transaction.WITHDRAW.equals(history.get(2).getType()));
            assertEquals("history: withdrawal tax stored", 600.0, history.get(2).getTax());
            assertEquals("history: balance after taxed withdrawal", 44400.0, bank.checkBalance(10001));
        } catch (Exception e) {
            fail("history threw " + e);
        }
    }

    private static void testPersistenceAcrossRestart() {
        cleanTestData();
        BankService first = newBank();
        try {
            first.createAccount(11001, "Persistent Holder", "CURRENT", 250000);
            first.withdraw(11001, 150000);
        } catch (Exception e) {
            fail("persistence: setup threw " + e);
            return;
        }

        // A brand new service reads the same files back - simulating a restart.
        BankService second = newBank();
        try {
            assertEquals("persistence: balance survives restart", 97000.0, second.checkBalance(11001));
            Account reloaded = second.findAccount(11001);
            assertTrue("persistence: account type survives restart",
                    CurrentAccount.TYPE.equals(reloaded.getAccountType()));
            assertTrue("persistence: holder name survives restart",
                    "Persistent Holder".equals(reloaded.getAccountHolderName()));
            assertTrue("persistence: history survives restart",
                    second.getTransactionHistory(11001).size() == 2);
        } catch (Exception e) {
            fail("persistence: reload threw " + e);
        }
        cleanTestData();
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private static void testValidationRules() {
        assertTrue("validation: 1001 is a valid account number",
                Validation.isValidAccountNumber("1001"));
        assertTrue("validation: letters rejected as account number",
                !Validation.isValidAccountNumber("10a1"));
        assertTrue("validation: zero rejected as account number",
                !Validation.isValidAccountNumber("0"));
        assertTrue("validation: blank rejected as account number",
                !Validation.isValidAccountNumber("   "));
        assertTrue("validation: name accepted", Validation.isValidName("Kartheeban C."));
        assertTrue("validation: digits rejected in name", !Validation.isValidName("User123"));
        assertTrue("validation: positive amount accepted", Validation.isPositiveAmount("1500.50"));
        assertTrue("validation: negative amount rejected", !Validation.isPositiveAmount("-5"));
        assertTrue("validation: zero rejected as amount", !Validation.isPositiveAmount("0"));
        assertTrue("validation: text rejected as amount", !Validation.isPositiveAmount("abc"));
        assertTrue("validation: zero allowed as opening balance",
                Validation.isNonNegativeAmount("0"));
        assertTrue("validation: '1' maps to SAVINGS",
                "SAVINGS".equals(Validation.normaliseAccountType("1")));
        assertTrue("validation: 'c' maps to CURRENT",
                "CURRENT".equals(Validation.normaliseAccountType("c")));
        assertTrue("validation: unknown type rejected",
                Validation.normaliseAccountType("gold") == null);
        assertTrue("validation: menu choice 10 accepted", Validation.isValidMenuChoice("10", 10));
        assertTrue("validation: menu choice 11 rejected", !Validation.isValidMenuChoice("11", 10));
        assertTrue("validation: loan submenu choice 7 accepted",
                Validation.isValidMenuChoice("7", 7));
        assertTrue("validation: loan submenu choice 8 rejected",
                !Validation.isValidMenuChoice("8", 7));
        assertTrue("validation: tenure 24 accepted", Validation.isValidTenure("24"));
        assertTrue("validation: tenure 0 rejected", !Validation.isValidTenure("0"));
        assertTrue("validation: tenure 400 rejected", !Validation.isValidTenure("400"));
        assertTrue("validation: fractional tenure rejected", !Validation.isValidTenure("12.5"));
        assertTrue("validation: '1' maps to PERSONAL",
                "PERSONAL".equals(Validation.normaliseLoanType("1")));
        assertTrue("validation: 'e' maps to EDUCATION",
                "EDUCATION".equals(Validation.normaliseLoanType("e")));
        assertTrue("validation: unknown loan type rejected",
                Validation.normaliseLoanType("gold") == null);
    }


    // ------------------------------------------------------------------
    // Loan Management
    // ------------------------------------------------------------------

    /** The worked example from the loan brief: 2,00,000 at 10% for 24 months. */
    private static void testLoanInterestAndEmiMaths() {
        Loan personal = new PersonalLoan(9001, 200000, 24);
        assertEquals("loan maths: personal rate is 10%", 10.0, personal.getAnnualInterestRate());
        assertEquals("loan maths: interest = P x R x T", 40000.0, personal.calculateInterest());
        assertEquals("loan maths: total repayment", 240000.0, personal.getTotalRepayment());
        assertEquals("loan maths: monthly EMI", 10000.0, personal.getMonthlyEmi());
        assertEquals("loan maths: nothing paid yet", 240000.0, personal.getRemainingAmount());

        // Education loan on the same figures, only the rate differs.
        Loan education = new EducationLoan(9001, 200000, 24);
        assertEquals("loan maths: education rate is 7%", 7.0, education.getAnnualInterestRate());
        assertEquals("loan maths: education interest", 28000.0, education.calculateInterest());
        assertEquals("loan maths: education total", 228000.0, education.getTotalRepayment());
        assertEquals("loan maths: education EMI", 9500.0, education.getMonthlyEmi());

        // A 6 month tenure is half a year, so the interest halves too.
        Loan halfYear = new PersonalLoan(9001, 100000, 6);
        assertEquals("loan maths: 6 month tenure is 0.5 years", 5000.0, halfYear.calculateInterest());
    }

    /** The same Loan reference gives different rates - method overriding at work. */
    private static void testLoanTypePolymorphism() {
        Loan[] loans = { new PersonalLoan(9002, 100000, 12), new EducationLoan(9002, 100000, 12) };
        assertEquals("polymorphism: personal via Loan reference", 10000.0,
                loans[0].calculateInterest());
        assertEquals("polymorphism: education via Loan reference", 7000.0,
                loans[1].calculateInterest());
        assertTrue("polymorphism: personal reports its type",
                PersonalLoan.TYPE.equals(loans[0].getLoanType()));
        assertTrue("polymorphism: education reports its type",
                EducationLoan.TYPE.equals(loans[1].getLoanType()));
    }

    private static void testLoanEligibilityRules() {
        cleanTestData();
        BankService bank = newBank();
        LoanService loanService = newLoanService(bank);
        try {
            bank.createAccount(20001, "Eligible Holder", "SAVINGS", 50000);
            bank.createAccount(20002, "Poor Holder", "SAVINGS", 5000);
        } catch (Exception e) {
            fail("eligibility: setup threw " + e);
            return;
        }

        try {
            assertEquals("eligibility: max loan is 5x balance", 250000.0,
                    loanService.getMaximumEligibleLoan(20001));
            loanService.checkEligibility(20001, 200000);
            pass("eligibility: 2,00,000 on a 50,000 balance is ELIGIBLE");
        } catch (Exception e) {
            fail("eligibility: 2,00,000 should have been eligible but threw " + e);
        }

        try {
            loanService.checkEligibility(20001, 300000);
            fail("eligibility: 3,00,000 should have been refused");
        } catch (LoanNotEligibleException e) {
            assertTrue("eligibility: reason names the maximum",
                    e.getReason().contains("exceeds the maximum eligible loan amount"));
        } catch (Exception e) {
            fail("eligibility: wrong exception " + e);
        }

        try {
            loanService.checkEligibility(20002, 1000);
            fail("eligibility: a 5,000 balance should have been refused");
        } catch (LoanNotEligibleException e) {
            assertTrue("eligibility: reason names the minimum balance",
                    e.getReason().contains("minimum balance"));
        } catch (Exception e) {
            fail("eligibility: wrong exception " + e);
        }

        try {
            loanService.checkEligibility(20001, -5000);
            fail("eligibility: a negative request should have been refused");
        } catch (LoanNotEligibleException e) {
            pass("eligibility: negative loan amount refused");
        } catch (Exception e) {
            fail("eligibility: wrong exception " + e);
        }

        try {
            loanService.checkEligibility(29999, 1000);
            fail("eligibility: unknown account should have been refused");
        } catch (InvalidAccountException e) {
            pass("eligibility: unknown account refused");
        } catch (Exception e) {
            fail("eligibility: wrong exception " + e);
        }
    }

    private static void testApplyForLoanCreditsAccount() {
        cleanTestData();
        BankService bank = newBank();
        LoanService loanService = newLoanService(bank);
        try {
            bank.createAccount(21001, "Loan Applicant", "SAVINGS", 50000);
            Loan loan = loanService.applyForLoan(21001, PersonalLoan.TYPE, 200000, 24);

            assertTrue("apply: first loan id is 501", loan.getLoanId() == 501);
            assertTrue("apply: loan becomes ACTIVE", Loan.STATUS_ACTIVE.equals(loan.getStatus()));
            assertEquals("apply: total repayment", 240000.0, loan.getTotalRepayment());
            assertEquals("apply: principal credited to the account", 250000.0,
                    bank.checkBalance(21001));

            List<Transaction> history = bank.getTransactionHistory(21001);
            Transaction last = history.get(history.size() - 1);
            assertTrue("apply: disbursement logged as a transaction",
                    Transaction.LOAN_DISBURSED.equals(last.getType()));
            assertEquals("apply: disbursement carries no tax", 0.0, last.getTax());

            // Tenure outside the allowed range must be refused.
            try {
                loanService.applyForLoan(21001, PersonalLoan.TYPE, 10000, 0);
                fail("apply: zero tenure should have been refused");
            } catch (LoanNotEligibleException e) {
                pass("apply: zero tenure refused");
            }
        } catch (Exception e) {
            fail("apply threw " + e);
        }
    }

    private static void testLoanRepaymentDebitsAccount() {
        cleanTestData();
        BankService bank = newBank();
        LoanService loanService = newLoanService(bank);
        try {
            bank.createAccount(22001, "Repaying Holder", "SAVINGS", 50000);
            Loan loan = loanService.applyForLoan(22001, PersonalLoan.TYPE, 200000, 24);

            loanService.makeRepayment(loan.getLoanId(), 10000);
            assertEquals("repayment: amount paid", 10000.0, loan.getAmountPaid());
            assertEquals("repayment: remaining amount", 230000.0, loan.getRemainingAmount());
            assertEquals("repayment: account debited", 240000.0, bank.checkBalance(22001));
            assertTrue("repayment: loan stays ACTIVE",
                    Loan.STATUS_ACTIVE.equals(loan.getStatus()));

            List<Transaction> history = bank.getTransactionHistory(22001);
            Transaction last = history.get(history.size() - 1);
            assertTrue("repayment: logged in the transaction history",
                    Transaction.LOAN_REPAYMENT.equals(last.getType()));
            assertEquals("repayment: carries no withdrawal tax", 0.0, last.getTax());
        } catch (Exception e) {
            fail("repayment threw " + e);
        }
    }

    private static void testInvalidRepaymentsAreRejected() {
        cleanTestData();
        BankService bank = newBank();
        LoanService loanService = newLoanService(bank);
        Loan loan = null;
        try {
            bank.createAccount(23001, "Invalid Repayer", "SAVINGS", 50000);
            loan = loanService.applyForLoan(23001, PersonalLoan.TYPE, 100000, 12);
        } catch (Exception e) {
            fail("invalid repayment: setup threw " + e);
            return;
        }

        try {
            loanService.makeRepayment(99999, 1000);
            fail("invalid repayment: unknown loan id should have been refused");
        } catch (LoanNotFoundException e) {
            pass("invalid repayment: unknown loan id refused");
        } catch (Exception e) {
            fail("invalid repayment: wrong exception " + e);
        }

        try {
            loanService.makeRepayment(loan.getLoanId(), 0);
            fail("invalid repayment: zero amount should have been refused");
        } catch (InvalidRepaymentException e) {
            pass("invalid repayment: zero amount refused");
        } catch (Exception e) {
            fail("invalid repayment: wrong exception " + e);
        }

        try {
            // Total repayment is 1,10,000 - paying more must be refused.
            loanService.makeRepayment(loan.getLoanId(), 120000);
            fail("invalid repayment: overpayment should have been refused");
        } catch (InvalidRepaymentException e) {
            pass("invalid repayment: more than the remaining amount refused");
        } catch (Exception e) {
            fail("invalid repayment: wrong exception " + e);
        }

        try {
            assertEquals("invalid repayment: nothing was paid", 0.0, loan.getAmountPaid());
            assertEquals("invalid repayment: account untouched", 150000.0,
                    bank.checkBalance(23001));
        } catch (InvalidAccountException e) {
            fail("invalid repayment: account vanished");
        }
    }

    private static void testRepaymentNeedsAccountBalance() {
        cleanTestData();
        BankService bank = newBank();
        LoanService loanService = newLoanService(bank);
        try {
            bank.createAccount(24001, "Empty Repayer", "SAVINGS", 10000);
            Loan loan = loanService.applyForLoan(24001, PersonalLoan.TYPE, 50000, 12);
            // Balance is now 60,000. Take most of it back out again.
            bank.withdraw(24001, 55000);

            loanService.makeRepayment(loan.getLoanId(), 5000);
            fail("repayment balance: should have failed on insufficient balance");
        } catch (InsufficientBalanceException e) {
            pass("repayment balance: refused when the account cannot afford it");
        } catch (Exception e) {
            fail("repayment balance: wrong exception " + e);
        }
        try {
            Loan loan = loanService.findLoan(501);
            assertEquals("repayment balance: loan untouched after a failed repayment", 0.0,
                    loan.getAmountPaid());
        } catch (LoanNotFoundException e) {
            fail("repayment balance: loan vanished");
        }
    }

    private static void testLoanCompletion() {
        cleanTestData();
        BankService bank = newBank();
        LoanService loanService = newLoanService(bank);
        try {
            bank.createAccount(25001, "Completing Holder", "SAVINGS", 50000);
            Loan loan = loanService.applyForLoan(25001, PersonalLoan.TYPE, 10000, 12);
            assertEquals("completion: total repayment", 11000.0, loan.getTotalRepayment());

            loanService.makeRepayment(loan.getLoanId(), 11000);
            assertEquals("completion: nothing remaining", 0.0, loan.getRemainingAmount());
            assertTrue("completion: status becomes COMPLETED", loan.isCompleted());
            assertTrue("completion: status word is COMPLETED",
                    Loan.STATUS_COMPLETED.equals(loanService.getLoanStatus(loan.getLoanId())));
            assertEquals("completion: account debited in full", 49000.0, bank.checkBalance(25001));

            try {
                loanService.makeRepayment(loan.getLoanId(), 100);
                fail("completion: repayment on a completed loan should have been refused");
            } catch (InvalidRepaymentException e) {
                pass("completion: repayment on a completed loan refused");
            }
        } catch (Exception e) {
            fail("completion threw " + e);
        }
    }

    private static void testRepaymentHistoryIsReadBackFromFile() {
        cleanTestData();
        BankService bank = newBank();
        LoanService loanService = newLoanService(bank);
        try {
            bank.createAccount(26001, "History Borrower", "SAVINGS", 50000);
            Loan loan = loanService.applyForLoan(26001, EducationLoan.TYPE, 100000, 12);
            loanService.makeRepayment(loan.getLoanId(), 10000);
            loanService.makeRepayment(loan.getLoanId(), 10000);
            loanService.makeRepayment(loan.getLoanId(), 5000);

            List<LoanRepayment> history = loanService.getRepaymentHistory(loan.getLoanId());
            assertTrue("loan history: three repayments recorded", history.size() == 3);
            assertTrue("loan history: ids run 1, 2, 3",
                    history.get(0).getRepaymentId() == 1 && history.get(2).getRepaymentId() == 3);
            assertEquals("loan history: total paid", 25000.0,
                    loanService.getTotalPaid(loan.getLoanId()));
            assertEquals("loan history: remaining on a 7% education loan", 82000.0,
                    loan.getRemainingAmount());
        } catch (Exception e) {
            fail("loan history threw " + e);
        }
    }

    private static void testLoansSurviveRestart() {
        cleanTestData();
        BankService first = newBank();
        LoanService firstLoans = newLoanService(first);
        try {
            first.createAccount(27001, "Persistent Borrower", "CURRENT", 50000);
            Loan loan = firstLoans.applyForLoan(27001, PersonalLoan.TYPE, 200000, 24);
            firstLoans.makeRepayment(loan.getLoanId(), 40000);
        } catch (Exception e) {
            fail("loan persistence: setup threw " + e);
            return;
        }

        // Brand new services read the same files back - simulating a restart.
        BankService second = newBank();
        LoanService secondLoans = newLoanService(second);
        try {
            Loan reloaded = secondLoans.findLoan(501);
            assertTrue("loan persistence: type survives restart",
                    PersonalLoan.TYPE.equals(reloaded.getLoanType()));
            assertEquals("loan persistence: principal survives restart", 200000.0,
                    reloaded.getPrincipalAmount());
            assertEquals("loan persistence: amount paid survives restart", 40000.0,
                    reloaded.getAmountPaid());
            assertEquals("loan persistence: remaining survives restart", 200000.0,
                    reloaded.getRemainingAmount());
            assertTrue("loan persistence: status survives restart",
                    Loan.STATUS_ACTIVE.equals(reloaded.getStatus()));
            assertEquals("loan persistence: account balance survives restart", 210000.0,
                    second.checkBalance(27001));
            assertTrue("loan persistence: repayment log survives restart",
                    secondLoans.getRepaymentHistory(501).size() == 1);
        } catch (Exception e) {
            fail("loan persistence: reload threw " + e);
        }
    }

    /** Regression guard: the loan feature must not disturb the withdrawal tax. */
    private static void testTaxCalculatorStillWorksAfterLoanFeature() {
        cleanTestData();
        BankService bank = newBank();
        LoanService loanService = newLoanService(bank);
        try {
            bank.createAccount(28001, "Tax Guard", "CURRENT", 100000);
            loanService.applyForLoan(28001, PersonalLoan.TYPE, 200000, 24);
            // Balance is now 3,00,000 - the brief's worked example, unchanged.
            Transaction withdrawal = bank.withdraw(28001, 150000);
            assertEquals("tax guard: 2% tax on a current account still applies", 3000.0,
                    withdrawal.getTax());
            assertEquals("tax guard: total deducted", 153000.0, withdrawal.getTotal());
            assertEquals("tax guard: remaining balance", 147000.0, bank.checkBalance(28001));
        } catch (Exception e) {
            fail("tax guard threw " + e);
        }
    }

    // ------------------------------------------------------------------
    // Harness plumbing
    // ------------------------------------------------------------------

    private static BankService newBank() {
        return new BankService(new FileManager(TEST_DATA_DIR));
    }

    /** Fresh, empty bank plus its loan service - used by the loan tests. */
    private static LoanService newLoanService(BankService bank) {
        return new LoanService(bank, new FileManager(TEST_DATA_DIR));
    }

    private static void cleanTestData() {
        File directory = new File(TEST_DATA_DIR);
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    System.out.println("[test warning] could not delete " + file.getPath());
                }
            }
        }
        if (directory.exists() && !directory.delete()) {
            System.out.println("[test warning] could not delete " + directory.getPath());
        }
    }

    private static void assertEquals(String label, double expected, double actual) {
        if (Math.abs(expected - actual) < 0.005) {
            pass(label);
        } else {
            fail(label + " (expected " + expected + " but got " + actual + ")");
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (condition) {
            pass(label);
        } else {
            fail(label);
        }
    }

    private static void pass(String label) {
        passed++;
        System.out.println("  PASS  " + label);
    }

    private static void fail(String label) {
        failed++;
        System.out.println("  FAIL  " + label);
    }
}
