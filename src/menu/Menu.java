package menu;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import exception.AccountNotEmptyException;
import exception.DuplicateAccountException;
import exception.InsufficientBalanceException;
import exception.InvalidAccountException;
import exception.InvalidRepaymentException;
import exception.InvalidTransferException;
import exception.LoanNotEligibleException;
import exception.LoanNotFoundException;
import model.Account;
import model.Loan;
import model.LoanRepayment;
import model.Transaction;
import service.BankService;
import service.LoanService;
import util.Validation;

/**
 * The console layer: prints the menu, reads and validates input, calls the
 * matching {@link BankService} operation and turns any checked exception into
 * a friendly one line message.
 */
public class Menu {

    private static final String LINE =
            "==================================================================";

    /** Number of options on the main menu and on the loan submenu. */
    private static final int MAIN_MENU_OPTIONS = 10;
    private static final int LOAN_MENU_OPTIONS = 7;

    private final Scanner scanner;
    private final BankService bankService;
    private final LoanService loanService;
    private boolean running;

    public Menu() {
        this(new BankService(), new Scanner(System.in));
    }

    public Menu(BankService bankService, Scanner scanner) {
        this(bankService, new LoanService(bankService), scanner);
    }

    public Menu(BankService bankService, LoanService loanService, Scanner scanner) {
        this.bankService = bankService;
        this.loanService = loanService;
        this.scanner = scanner;
        this.running = true;
    }

    /** Main console loop. Runs until the user picks Exit or input runs out. */
    public void start() {
        printWelcome();
        while (running) {
            printMenu();
            String choice = readLine("Enter your choice (1-" + MAIN_MENU_OPTIONS + "): ");
            if (choice == null) {
                System.out.println("\nInput stream closed. Shutting down.");
                break;
            }
            if (!Validation.isValidMenuChoice(choice, MAIN_MENU_OPTIONS)) {
                System.out.println("Invalid choice. Please enter a number between 1 and "
                        + MAIN_MENU_OPTIONS + ".");
                continue;
            }
            switch (choice.trim()) {
                case "1":
                    createAccount();
                    break;
                case "2":
                    deposit();
                    break;
                case "3":
                    withdraw();
                    break;
                case "4":
                    transfer();
                    break;
                case "5":
                    checkBalance();
                    break;
                case "6":
                    transactionHistory();
                    break;
                case "7":
                    loanManagement();
                    break;
                case "8":
                    viewAllAccounts();
                    break;
                case "9":
                    closeAccount();
                    break;
                case "10":
                    exit();
                    break;
                default:
                    System.out.println("Invalid choice. Please enter a number between 1 and "
                            + MAIN_MENU_OPTIONS + ".");
                    break;
            }
        }
    }

    // ------------------------------------------------------------------
    // Menu options
    // ------------------------------------------------------------------

    private void createAccount() {
        printHeading("CREATE ACCOUNT");
        Integer accountNumber = askAccountNumber("Enter a new account number: ");
        if (accountNumber == null) {
            return;
        }
        String name = askName("Enter account holder name: ");
        if (name == null) {
            return;
        }
        String type = askAccountType();
        if (type == null) {
            return;
        }
        Double opening = askAmount("Enter opening balance (Rs., 0 or more): ", true);
        if (opening == null) {
            return;
        }
        try {
            Account account = bankService.createAccount(accountNumber, name, type, opening);
            System.out.println("\nAccount created successfully.");
            System.out.println(account);
            System.out.println("Total open accounts: " + Account.getTotalAccounts());
        } catch (DuplicateAccountException e) {
            System.out.println("Could not create account: " + e.getMessage());
        }
    }

    private void deposit() {
        printHeading("DEPOSIT");
        Integer accountNumber = askAccountNumber("Enter account number: ");
        if (accountNumber == null) {
            return;
        }
        Double amount = askAmount("Enter amount to deposit (Rs.): ", false);
        if (amount == null) {
            return;
        }
        try {
            Transaction transaction = bankService.deposit(accountNumber, amount);
            System.out.printf("%nDeposited Rs.%.2f into account %d.%n", transaction.getAmount(),
                    accountNumber);
            System.out.printf("Updated balance: Rs.%.2f%n", bankService.checkBalance(accountNumber));
        } catch (InvalidAccountException e) {
            System.out.println("Deposit failed: " + e.getMessage());
        }
    }

    private void withdraw() {
        printHeading("WITHDRAW");
        Integer accountNumber = askAccountNumber("Enter account number: ");
        if (accountNumber == null) {
            return;
        }
        Double amount = askAmount("Enter amount to withdraw (Rs.): ", false);
        if (amount == null) {
            return;
        }
        try {
            double tax = bankService.previewTax(accountNumber, amount);
            System.out.printf("%nTax on this withdrawal: Rs.%.2f%n", tax);
            Transaction transaction = bankService.withdraw(accountNumber, amount);
            System.out.printf("Amount withdrawn : Rs.%.2f%n", transaction.getAmount());
            System.out.printf("Tax deducted     : Rs.%.2f%n", transaction.getTax());
            System.out.printf("Total deducted   : Rs.%.2f%n", transaction.getTotal());
            System.out.printf("Remaining balance: Rs.%.2f%n", bankService.checkBalance(accountNumber));
        } catch (InvalidAccountException e) {
            System.out.println("Withdrawal failed: " + e.getMessage());
        } catch (InsufficientBalanceException e) {
            System.out.println("Withdrawal failed: " + e.getMessage());
        }
    }

    private void transfer() {
        printHeading("TRANSFER");
        Integer fromAccount = askAccountNumber("Enter sender account number: ");
        if (fromAccount == null) {
            return;
        }
        Integer toAccount = askAccountNumber("Enter receiver account number: ");
        if (toAccount == null) {
            return;
        }
        Double amount = askAmount("Enter amount to transfer (Rs.): ", false);
        if (amount == null) {
            return;
        }
        try {
            Transaction transaction = bankService.transfer(fromAccount, toAccount, amount);
            System.out.printf("%nTransferred Rs.%.2f from account %d to account %d.%n",
                    transaction.getAmount(), fromAccount, toAccount);
            System.out.printf("Tax on transfer  : Rs.%.2f%n", transaction.getTax());
            System.out.printf("Total debited    : Rs.%.2f%n", transaction.getTotal());
            System.out.printf("Sender balance   : Rs.%.2f%n", bankService.checkBalance(fromAccount));
            System.out.printf("Receiver balance : Rs.%.2f%n", bankService.checkBalance(toAccount));
        } catch (InvalidAccountException e) {
            System.out.println("Transfer failed: " + e.getMessage() + " No balances were changed.");
        } catch (InvalidTransferException e) {
            System.out.println("Transfer failed: " + e.getMessage() + " No balances were changed.");
        } catch (InsufficientBalanceException e) {
            System.out.println("Transfer failed: " + e.getMessage() + " No balances were changed.");
        }
    }

    private void checkBalance() {
        printHeading("CHECK BALANCE");
        Integer accountNumber = askAccountNumber("Enter account number: ");
        if (accountNumber == null) {
            return;
        }
        try {
            Account account = bankService.findAccount(accountNumber);
            System.out.println();
            System.out.println(account);
        } catch (InvalidAccountException e) {
            System.out.println("Could not show balance: " + e.getMessage());
        }
    }

    private void transactionHistory() {
        printHeading("TRANSACTION HISTORY");
        Integer accountNumber = askAccountNumber("Enter account number: ");
        if (accountNumber == null) {
            return;
        }
        try {
            List<Transaction> history = bankService.getTransactionHistory(accountNumber);
            if (history.isEmpty()) {
                System.out.println("\nNo transactions recorded for account " + accountNumber + ".");
                return;
            }
            System.out.println("\nTransactions for account " + accountNumber + ":");
            System.out.println(LINE);
            for (Transaction transaction : history) {
                System.out.println(transaction);
            }
            System.out.println(LINE);
            System.out.println("Total transactions: " + history.size());
        } catch (InvalidAccountException e) {
            System.out.println("Could not show history: " + e.getMessage());
        }
    }

    private void closeAccount() {
        printHeading("CLOSE ACCOUNT");
        Integer accountNumber = askAccountNumber("Enter account number to close: ");
        if (accountNumber == null) {
            return;
        }
        try {
            Account closed = bankService.closeAccount(accountNumber);
            System.out.println("\nAccount " + closed.getAccountNumber() + " ("
                    + closed.getAccountHolderName() + ") has been closed.");
            System.out.println("Total open accounts: " + Account.getTotalAccounts());
        } catch (InvalidAccountException e) {
            System.out.println("Could not close account: " + e.getMessage());
        } catch (AccountNotEmptyException e) {
            System.out.println("\n" + e.getMessage());
            String answer = readLine(String.format(
                    "Pay out the remaining Rs.%.2f (tax free settlement) and close anyway? (y/n): ",
                    e.getRemainingBalance()));
            if (answer == null || !answer.trim().equalsIgnoreCase("y")) {
                System.out.println("Account " + accountNumber + " was left open.");
                return;
            }
            try {
                Transaction settlement = bankService.settleAndCloseAccount(accountNumber);
                if (settlement != null) {
                    System.out.printf("Settlement paid out: Rs.%.2f (no tax on closure payouts).%n",
                            settlement.getAmount());
                }
                System.out.println("Account " + accountNumber + " has been closed.");
                System.out.println("Total open accounts: " + Account.getTotalAccounts());
            } catch (InvalidAccountException | AccountNotEmptyException ex) {
                System.out.println("Could not close account: " + ex.getMessage());
            }
        }
    }

    private void viewAllAccounts() {
        printHeading("ALL ACCOUNTS");
        List<Account> accounts = bankService.getAllAccounts();
        if (accounts.isEmpty()) {
            System.out.println("\nNo accounts have been opened yet.");
            return;
        }
        System.out.println();
        System.out.println(LINE);
        System.out.printf("%-14s%-24s%-12s%s%n", "ACCOUNT NO", "HOLDER NAME", "TYPE", "BALANCE");
        System.out.println(LINE);
        for (Account account : accounts) {
            System.out.printf("%-14d%-24s%-12s Rs.%.2f%n", account.getAccountNumber(),
                    account.getAccountHolderName(), account.getAccountType(), account.getBalance());
        }
        System.out.println(LINE);
        System.out.println("Total open accounts: " + Account.getTotalAccounts());
    }

    // ------------------------------------------------------------------
    // Loan management - option 7
    // ------------------------------------------------------------------

    /** Submenu loop. Runs until the user picks Back or the input ends. */
    private void loanManagement() {
        boolean inLoanMenu = true;
        while (inLoanMenu) {
            printLoanMenu();
            String choice = readLine("Enter your choice (1-" + LOAN_MENU_OPTIONS + "): ");
            if (choice == null) {
                return;
            }
            if (!Validation.isValidMenuChoice(choice, LOAN_MENU_OPTIONS)) {
                System.out.println("Invalid choice. Please enter a number between 1 and "
                        + LOAN_MENU_OPTIONS + ".");
                continue;
            }
            switch (choice.trim()) {
                case "1":
                    checkLoanEligibility();
                    break;
                case "2":
                    applyForLoan();
                    break;
                case "3":
                    viewLoanDetails();
                    break;
                case "4":
                    viewLoanStatus();
                    break;
                case "5":
                    makeLoanRepayment();
                    break;
                case "6":
                    viewRepaymentHistory();
                    break;
                case "7":
                    inLoanMenu = false;
                    break;
                default:
                    System.out.println("Invalid choice. Please enter a number between 1 and "
                            + LOAN_MENU_OPTIONS + ".");
                    break;
            }
        }
    }

    private void checkLoanEligibility() {
        printHeading("LOAN ELIGIBILITY");
        Integer accountNumber = askAccountNumber("Enter Account Number: ");
        if (accountNumber == null) {
            return;
        }
        try {
            double balance = bankService.checkBalance(accountNumber);
            double maximum = loanService.getMaximumEligibleLoan(accountNumber);
            System.out.printf("Account Balance      : Rs.%.2f%n", balance);
            System.out.printf("Maximum Eligible Loan: Rs.%.2f%n", maximum);

            Double requested = askAmount("Enter Requested Loan Amount: Rs.", false);
            if (requested == null) {
                return;
            }
            System.out.printf("Requested Loan Amount: Rs.%.2f%n", requested);
            try {
                loanService.checkEligibility(accountNumber, requested);
                System.out.println("Loan Eligibility     : ELIGIBLE");
                System.out.println("You can proceed with the loan application.");
            } catch (LoanNotEligibleException e) {
                System.out.println("Loan Eligibility     : NOT ELIGIBLE");
                System.out.println("Reason:");
                System.out.println("  " + e.getReason());
            }
        } catch (InvalidAccountException e) {
            System.out.println("Could not check eligibility: " + e.getMessage());
        }
    }

    private void applyForLoan() {
        printHeading("APPLY FOR LOAN");
        Integer accountNumber = askAccountNumber("Enter Account Number: ");
        if (accountNumber == null) {
            return;
        }
        String loanType = askLoanType();
        if (loanType == null) {
            return;
        }
        Double amount = askAmount("Enter Loan Amount: Rs.", false);
        if (amount == null) {
            return;
        }
        Integer tenure = askTenure();
        if (tenure == null) {
            return;
        }
        try {
            System.out.println("\nChecking eligibility...");
            loanService.checkEligibility(accountNumber, amount);
            System.out.println("Loan eligible!");

            // Show the figures before asking the customer to commit to them.
            Loan preview = loanService.previewLoan(accountNumber, loanType, amount, tenure);
            System.out.printf("Interest Rate          : %.0f%%%n", preview.getAnnualInterestRate());
            System.out.printf("Estimated Interest     : Rs.%.2f%n", preview.calculateInterest());
            System.out.printf("Total Repayment Amount : Rs.%.2f%n", preview.getTotalRepayment());
            System.out.printf("Monthly EMI            : Rs.%.2f%n", preview.getMonthlyEmi());

            String confirm = readLine("Confirm Loan Application? (Y/N): ");
            if (confirm == null || !confirm.trim().equalsIgnoreCase("y")) {
                System.out.println("Loan application cancelled. Nothing was changed.");
                return;
            }
            Loan loan = loanService.applyForLoan(accountNumber, loanType, amount, tenure);
            System.out.println("\nLoan application submitted successfully!");
            System.out.println("Loan ID: " + loan.getLoanId());
            System.out.println("Status : " + loan.getStatus());
            System.out.printf("Rs.%.2f has been credited to account %d.%n",
                    loan.getPrincipalAmount(), accountNumber);
            System.out.printf("Updated account balance: Rs.%.2f%n",
                    bankService.checkBalance(accountNumber));
        } catch (InvalidAccountException e) {
            System.out.println("Loan application failed: " + e.getMessage());
        } catch (LoanNotEligibleException e) {
            System.out.println("Loan Eligibility: NOT ELIGIBLE");
            System.out.println("Reason:");
            System.out.println("  " + e.getReason());
        }
    }

    private void viewLoanDetails() {
        printHeading("LOAN DETAILS");
        Integer loanId = askLoanId();
        if (loanId == null) {
            return;
        }
        try {
            System.out.println();
            System.out.println(loanService.findLoan(loanId));
        } catch (LoanNotFoundException e) {
            System.out.println("Could not show loan details: " + e.getMessage());
        }
    }

    private void viewLoanStatus() {
        printHeading("LOAN STATUS");
        Integer loanId = askLoanId();
        if (loanId == null) {
            return;
        }
        try {
            Loan loan = loanService.findLoan(loanId);
            System.out.println();
            System.out.println("Loan ID          : " + loan.getLoanId());
            System.out.println("Account Number   : " + loan.getAccountNumber());
            System.out.printf("Total Repayment  : Rs.%.2f%n", loan.getTotalRepayment());
            System.out.printf("Amount Paid      : Rs.%.2f%n", loan.getAmountPaid());
            System.out.printf("Remaining Amount : Rs.%.2f%n", loan.getRemainingAmount());
            System.out.println("Loan Status      : " + loan.getStatus());
        } catch (LoanNotFoundException e) {
            System.out.println("Could not show loan status: " + e.getMessage());
        }
    }

    private void makeLoanRepayment() {
        printHeading("LOAN REPAYMENT");
        Integer loanId = askLoanId();
        if (loanId == null) {
            return;
        }
        try {
            Loan loan = loanService.findLoan(loanId);
            System.out.printf("Loan Amount      : Rs.%.2f%n", loan.getPrincipalAmount());
            System.out.printf("Total Repayment  : Rs.%.2f%n", loan.getTotalRepayment());
            System.out.printf("Amount Paid      : Rs.%.2f%n", loan.getAmountPaid());
            System.out.printf("Remaining Amount : Rs.%.2f%n", loan.getRemainingAmount());
            System.out.printf("Monthly EMI      : Rs.%.2f%n", loan.getMonthlyEmi());
            System.out.println("Loan Status      : " + loan.getStatus());

            if (loan.isCompleted()) {
                System.out.println("\nThis loan is already fully repaid.");
                return;
            }
            Double amount = askAmount("Enter Repayment Amount: Rs.", false);
            if (amount == null) {
                return;
            }
            loanService.makeRepayment(loanId, amount);
            System.out.println("\nRepayment successful!");
            System.out.printf("Amount Paid      : Rs.%.2f%n", loan.getAmountPaid());
            System.out.printf("Remaining Amount : Rs.%.2f%n", loan.getRemainingAmount());
            System.out.printf("Account Balance  : Rs.%.2f%n",
                    bankService.checkBalance(loan.getAccountNumber()));
            if (loan.isCompleted()) {
                System.out.println("Loan fully repaid!");
            }
            System.out.println("Loan Status      : " + loan.getStatus());
        } catch (LoanNotFoundException e) {
            System.out.println("Repayment failed: " + e.getMessage());
        } catch (InvalidRepaymentException e) {
            System.out.println("Repayment failed: " + e.getMessage());
        } catch (InvalidAccountException e) {
            System.out.println("Repayment failed: " + e.getMessage() + " Nothing was deducted.");
        } catch (InsufficientBalanceException e) {
            System.out.println("Repayment failed: " + e.getMessage() + " Nothing was deducted.");
        }
    }

    private void viewRepaymentHistory() {
        printHeading("REPAYMENT HISTORY");
        Integer loanId = askLoanId();
        if (loanId == null) {
            return;
        }
        try {
            Loan loan = loanService.findLoan(loanId);
            List<LoanRepayment> history = loanService.getRepaymentHistory(loanId);
            System.out.println("\nLoan ID: " + loanId);
            if (history.isEmpty()) {
                System.out.println("No repayments have been made against this loan yet.");
                System.out.printf("Remaining: Rs.%.2f%n", loan.getRemainingAmount());
                return;
            }
            System.out.println(LINE);
            System.out.printf("%-12s%-18s%s%n", "PAYMENT ID", "AMOUNT", "DATE");
            System.out.println(LINE);
            for (LoanRepayment repayment : history) {
                System.out.println(repayment);
            }
            System.out.println(LINE);
            System.out.printf("Total Paid: Rs.%.2f%n", loanService.getTotalPaid(loanId));
            System.out.printf("Remaining : Rs.%.2f%n", loan.getRemainingAmount());
            System.out.println("Status    : " + loan.getStatus());
        } catch (LoanNotFoundException e) {
            System.out.println("Could not show repayment history: " + e.getMessage());
        }
    }

    private void exit() {
        System.out.println("\nThank you for banking with us. Goodbye!");
        running = false;
    }

    // ------------------------------------------------------------------
    // Input helpers - every one of them re-prompts instead of crashing
    // ------------------------------------------------------------------

    private Integer askAccountNumber(String prompt) {
        while (true) {
            String input = readLine(prompt);
            if (input == null) {
                return null;
            }
            if (Validation.isValidAccountNumber(input)) {
                return Validation.parseAccountNumber(input);
            }
            System.out.println("Invalid account number. Use digits only, 1 to "
                    + Validation.MAX_ACCOUNT_NUMBER + ".");
        }
    }

    private String askName(String prompt) {
        while (true) {
            String input = readLine(prompt);
            if (input == null) {
                return null;
            }
            if (Validation.isValidName(input)) {
                return input.trim();
            }
            System.out.println("Invalid name. Use 2 to 40 letters (spaces, '.' and ''' allowed).");
        }
    }

    private String askAccountType() {
        while (true) {
            String input = readLine("Select account type - 1) Savings  2) Current : ");
            if (input == null) {
                return null;
            }
            String type = Validation.normaliseAccountType(input);
            if (type != null) {
                return type;
            }
            System.out.println("Invalid account type. Enter 1 for Savings or 2 for Current.");
        }
    }

    private Double askAmount(String prompt, boolean allowZero) {
        while (true) {
            String input = readLine(prompt);
            if (input == null) {
                return null;
            }
            boolean valid = allowZero ? Validation.isNonNegativeAmount(input)
                                      : Validation.isPositiveAmount(input);
            if (valid) {
                return Validation.parseAmount(input);
            }
            System.out.println(allowZero
                    ? "Invalid amount. Enter a number of 0 or more."
                    : "Invalid amount. Enter a number greater than 0.");
        }
    }

    private Integer askLoanId() {
        while (true) {
            String input = readLine("Enter Loan ID: ");
            if (input == null) {
                return null;
            }
            if (Validation.isValidLoanId(input)) {
                return Validation.parseLoanId(input);
            }
            System.out.println("Invalid loan ID. Use digits only, for example 501.");
        }
    }

    private String askLoanType() {
        while (true) {
            System.out.println("Select Loan Type:");
            System.out.println("   1. Personal Loan  (10% per annum)");
            System.out.println("   2. Education Loan (7% per annum)");
            String input = readLine("Enter Choice: ");
            if (input == null) {
                return null;
            }
            String type = Validation.normaliseLoanType(input);
            if (type != null) {
                return type;
            }
            System.out.println("Invalid loan type. Enter 1 for Personal or 2 for Education.");
        }
    }

    private Integer askTenure() {
        while (true) {
            String input = readLine("Enter Tenure (months): ");
            if (input == null) {
                return null;
            }
            if (Validation.isValidTenure(input)) {
                return Validation.parseTenure(input);
            }
            System.out.println("Invalid tenure. Enter a whole number of months between "
                    + Validation.MIN_TENURE_MONTHS + " and " + Validation.MAX_TENURE_MONTHS + ".");
        }
    }

    /** Reads one line, or null when the input stream has ended. */
    private String readLine(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        try {
            if (!scanner.hasNextLine()) {
                return null;
            }
            return scanner.nextLine();
        } catch (NoSuchElementException | IllegalStateException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Screen furniture
    // ------------------------------------------------------------------

    private void printWelcome() {
        System.out.println(LINE);
        System.out.println("      BANK MANAGEMENT SYSTEM WITH AUTOMATED TAX CALCULATOR");
        System.out.println(LINE);
        System.out.println(" Withdrawal / transfer tax slabs");
        System.out.println("   Up to Rs.50,000            : no tax (both account types)");
        System.out.println("   Rs.50,001 to Rs.1,00,000   : 1% (both account types)");
        System.out.println("   Above Rs.1,00,000          : Savings 1%  |  Current 2%");
        System.out.println(LINE);
    }

    private void printMenu() {
        StringBuilder builder = new StringBuilder();
        builder.append('\n').append(LINE).append('\n');
        builder.append(" MAIN MENU").append('\n');
        builder.append("   1. Create Account").append('\n');
        builder.append("   2. Deposit Money").append('\n');
        builder.append("   3. Withdraw Money").append('\n');
        builder.append("   4. Transfer Funds").append('\n');
        builder.append("   5. Check Balance").append('\n');
        builder.append("   6. Transaction History").append('\n');
        builder.append("   7. Loan Management").append('\n');
        builder.append("   8. View All Accounts").append('\n');
        builder.append("   9. Close Account").append('\n');
        builder.append("  10. Exit").append('\n');
        builder.append(LINE);
        System.out.println(builder.toString());
    }

    private void printLoanMenu() {
        StringBuilder builder = new StringBuilder();
        builder.append('\n').append(LINE).append('\n');
        builder.append(" LOAN MANAGEMENT").append('\n');
        builder.append("   1. Check Loan Eligibility").append('\n');
        builder.append("   2. Apply for Loan").append('\n');
        builder.append("   3. View Loan Details").append('\n');
        builder.append("   4. View Loan Status").append('\n');
        builder.append("   5. Make Loan Repayment").append('\n');
        builder.append("   6. View Repayment History").append('\n');
        builder.append("   7. Back to Main Menu").append('\n');
        builder.append(LINE);
        System.out.println(builder.toString());
    }

    private void printHeading(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
