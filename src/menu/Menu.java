package menu;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import exception.AccountNotEmptyException;
import exception.DuplicateAccountException;
import exception.InsufficientBalanceException;
import exception.InvalidAccountException;
import exception.InvalidTransferException;
import model.Account;
import model.Transaction;
import service.BankService;
import util.Validation;

/**
 * The console layer: prints the menu, reads and validates input, calls the
 * matching {@link BankService} operation and turns any checked exception into
 * a friendly one line message.
 */
public class Menu {

    private static final String LINE =
            "==================================================================";

    private final Scanner scanner;
    private final BankService bankService;
    private boolean running;

    public Menu() {
        this(new BankService(), new Scanner(System.in));
    }

    public Menu(BankService bankService, Scanner scanner) {
        this.bankService = bankService;
        this.scanner = scanner;
        this.running = true;
    }

    /** Main console loop. Runs until the user picks Exit or input runs out. */
    public void start() {
        printWelcome();
        while (running) {
            printMenu();
            String choice = readLine("Enter your choice (1-8): ");
            if (choice == null) {
                System.out.println("\nInput stream closed. Shutting down.");
                break;
            }
            if (!Validation.isValidMenuChoice(choice)) {
                System.out.println("Invalid choice. Please enter a number between 1 and 8.");
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
                    closeAccount();
                    break;
                case "8":
                    exit();
                    break;
                default:
                    System.out.println("Invalid choice. Please enter a number between 1 and 8.");
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
        builder.append("   2. Deposit").append('\n');
        builder.append("   3. Withdraw").append('\n');
        builder.append("   4. Transfer").append('\n');
        builder.append("   5. Check Balance").append('\n');
        builder.append("   6. Transaction History").append('\n');
        builder.append("   7. Close Account").append('\n');
        builder.append("   8. Exit").append('\n');
        builder.append(LINE);
        System.out.println(builder.toString());
    }

    private void printHeading(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
