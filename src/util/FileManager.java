package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import model.Account;
import model.CurrentAccount;
import model.EducationLoan;
import model.Loan;
import model.LoanRepayment;
import model.PersonalLoan;
import model.SavingsAccount;
import model.Transaction;

/**
 * All persistence for the bank, built on character streams only
 * (FileReader/FileWriter wrapped in BufferedReader/BufferedWriter).
 *
 * Every IOException is caught here and reported as a message, so a missing or
 * unreadable data file degrades the app instead of crashing it.
 */
public class FileManager {

    public static final String DEFAULT_DATA_DIRECTORY = "data";
    public static final String ACCOUNTS_FILE = "accounts.txt";
    public static final String TRANSACTIONS_FILE = "transactions.txt";
    public static final String LOANS_FILE = "loans.txt";
    public static final String LOAN_REPAYMENTS_FILE = "loan_repayments.txt";
    private static final String SEPARATOR = "\\|";

    private final String accountsPath;
    private final String transactionsPath;
    private final String loansPath;
    private final String loanRepaymentsPath;

    public FileManager() {
        this(DEFAULT_DATA_DIRECTORY);
    }

    public FileManager(String dataDirectory) {
        File directory = new File(dataDirectory);
        if (!directory.exists() && !directory.mkdirs()) {
            System.out.println("[File warning] Could not create the data directory: " + dataDirectory);
        }
        this.accountsPath = new File(directory, ACCOUNTS_FILE).getPath();
        this.transactionsPath = new File(directory, TRANSACTIONS_FILE).getPath();
        this.loansPath = new File(directory, LOANS_FILE).getPath();
        this.loanRepaymentsPath = new File(directory, LOAN_REPAYMENTS_FILE).getPath();
    }

    public String getAccountsPath() {
        return accountsPath;
    }

    public String getTransactionsPath() {
        return transactionsPath;
    }

    public String getLoansPath() {
        return loansPath;
    }

    public String getLoanRepaymentsPath() {
        return loanRepaymentsPath;
    }

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    /**
     * Reads every account back from accounts.txt.
     * Format: accountNumber|type|holderName|balance
     */
    public List<Account> loadAccounts() {
        List<Account> accounts = new ArrayList<>();
        File file = new File(accountsPath);
        if (!file.exists()) {
            return accounts;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(SEPARATOR);
                if (parts.length != 4) {
                    System.out.println("[File warning] Skipping malformed account line: " + line);
                    continue;
                }
                try {
                    int number = Integer.parseInt(parts[0].trim());
                    String type = parts[1].trim();
                    String name = parts[2].trim();
                    double balance = Double.parseDouble(parts[3].trim());
                    if (CurrentAccount.TYPE.equalsIgnoreCase(type)) {
                        accounts.add(new CurrentAccount(number, name, balance));
                    } else {
                        accounts.add(new SavingsAccount(number, name, balance));
                    }
                } catch (NumberFormatException e) {
                    System.out.println("[File warning] Skipping unreadable account line: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("[File error] Could not read " + accountsPath + ": " + e.getMessage());
        }
        return accounts;
    }

    /** Rewrites accounts.txt from the in-memory list. */
    public boolean saveAccounts(List<Account> accounts) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(accountsPath))) {
            for (Account account : accounts) {
                StringBuilder line = new StringBuilder();
                line.append(account.getAccountNumber()).append('|')
                    .append(account.getAccountType()).append('|')
                    .append(account.getAccountHolderName()).append('|')
                    .append(Validation.round(account.getBalance()));
                writer.write(line.toString());
                writer.newLine();
            }
            return true;
        } catch (IOException e) {
            System.out.println("[File error] Could not write " + accountsPath + ": " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------

    /**
     * Reads the transaction log back from transactions.txt.
     * Format: id|accountNumber|type|amount|tax|total|secondAccountNumber|timestamp
     */
    public List<Transaction> loadTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        File file = new File(transactionsPath);
        if (!file.exists()) {
            return transactions;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(SEPARATOR);
                if (parts.length != 8) {
                    System.out.println("[File warning] Skipping malformed transaction line: " + line);
                    continue;
                }
                try {
                    transactions.add(new Transaction(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            parts[2].trim(),
                            Double.parseDouble(parts[3].trim()),
                            Double.parseDouble(parts[4].trim()),
                            Double.parseDouble(parts[5].trim()),
                            Integer.parseInt(parts[6].trim()),
                            parts[7].trim()));
                } catch (NumberFormatException e) {
                    System.out.println("[File warning] Skipping unreadable transaction line: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("[File error] Could not read " + transactionsPath + ": " + e.getMessage());
        }
        return transactions;
    }

    /** Appends a single transaction to the log without rewriting the file. */
    public boolean appendTransaction(Transaction transaction) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(transactionsPath, true))) {
            writer.write(transaction.toFileLine());
            writer.newLine();
            return true;
        } catch (IOException e) {
            System.out.println("[File error] Could not write " + transactionsPath + ": " + e.getMessage());
            return false;
        }
    }

    /** Rewrites the whole transaction log; used by the tests and for repairs. */
    public boolean saveTransactions(List<Transaction> transactions) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(transactionsPath))) {
            for (Transaction transaction : transactions) {
                writer.write(transaction.toFileLine());
                writer.newLine();
            }
            return true;
        } catch (IOException e) {
            System.out.println("[File error] Could not write " + transactionsPath + ": " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Loans
    // ------------------------------------------------------------------

    /**
     * Reads every loan back from loans.txt.
     * Format: loanId|accountNumber|loanType|principal|interestRate|tenureMonths|
     *         totalInterest|totalRepayment|amountPaid|remainingAmount|emi|status|loanDate
     *
     * The interest, total, remaining and emi columns are derived values - they
     * are written for readability and recalculated from the principal, rate and
     * tenure when the loan is loaded, so the file can never drift out of step.
     */
    public List<Loan> loadLoans() {
        List<Loan> loans = new ArrayList<>();
        File file = new File(loansPath);
        if (!file.exists()) {
            return loans;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(SEPARATOR);
                if (parts.length != 13) {
                    System.out.println("[File warning] Skipping malformed loan line: " + line);
                    continue;
                }
                try {
                    int loanId = Integer.parseInt(parts[0].trim());
                    int accountNumber = Integer.parseInt(parts[1].trim());
                    String type = parts[2].trim();
                    double principal = Double.parseDouble(parts[3].trim());
                    int tenure = Integer.parseInt(parts[5].trim());
                    double amountPaid = Double.parseDouble(parts[8].trim());
                    String status = parts[11].trim();
                    String loanDate = parts[12].trim();
                    if (EducationLoan.TYPE.equalsIgnoreCase(type)) {
                        loans.add(new EducationLoan(loanId, accountNumber, principal, tenure,
                                amountPaid, status, loanDate));
                    } else {
                        loans.add(new PersonalLoan(loanId, accountNumber, principal, tenure,
                                amountPaid, status, loanDate));
                    }
                } catch (NumberFormatException e) {
                    System.out.println("[File warning] Skipping unreadable loan line: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("[File error] Could not read " + loansPath + ": " + e.getMessage());
        }
        return loans;
    }

    /** Rewrites loans.txt from the in-memory list. */
    public boolean saveLoans(List<Loan> loans) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(loansPath))) {
            for (Loan loan : loans) {
                writer.write(loan.toFileLine());
                writer.newLine();
            }
            return true;
        } catch (IOException e) {
            System.out.println("[File error] Could not write " + loansPath + ": " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Loan repayments
    // ------------------------------------------------------------------

    /**
     * Reads the repayment log back from loan_repayments.txt.
     * Format: repaymentId|loanId|amount|repaymentDate
     */
    public List<LoanRepayment> loadLoanRepayments() {
        List<LoanRepayment> repayments = new ArrayList<>();
        File file = new File(loanRepaymentsPath);
        if (!file.exists()) {
            return repayments;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(SEPARATOR);
                if (parts.length != 4) {
                    System.out.println("[File warning] Skipping malformed repayment line: " + line);
                    continue;
                }
                try {
                    repayments.add(new LoanRepayment(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Double.parseDouble(parts[2].trim()),
                            parts[3].trim()));
                } catch (NumberFormatException e) {
                    System.out.println("[File warning] Skipping unreadable repayment line: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("[File error] Could not read " + loanRepaymentsPath + ": "
                    + e.getMessage());
        }
        return repayments;
    }

    /** Appends a single repayment to the log without rewriting the file. */
    public boolean appendLoanRepayment(LoanRepayment repayment) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(loanRepaymentsPath, true))) {
            writer.write(repayment.toFileLine());
            writer.newLine();
            return true;
        } catch (IOException e) {
            System.out.println("[File error] Could not write " + loanRepaymentsPath + ": "
                    + e.getMessage());
            return false;
        }
    }
}
