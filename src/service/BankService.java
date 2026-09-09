package service;

import java.util.ArrayList;
import java.util.List;

import exception.AccountNotEmptyException;
import exception.DuplicateAccountException;
import exception.InsufficientBalanceException;
import exception.InvalidAccountException;
import exception.InvalidTransferException;
import model.Account;
import model.CurrentAccount;
import model.SavingsAccount;
import model.Transaction;
import util.FileManager;
import util.Validation;

/**
 * Business layer of the bank. Holds the accounts and the transaction log in
 * ArrayLists, applies the tax rules and pushes every change to disk through
 * {@link FileManager}.
 */
public class BankService {

    private final List<Account> accounts;
    private final List<Transaction> transactions;
    private final FileManager fileManager;

    public BankService() {
        this(new FileManager());
    }

    public BankService(FileManager fileManager) {
        this.fileManager = fileManager;
        Account.resetTotalAccounts();
        Transaction.resetIdSequence();
        this.accounts = new ArrayList<>(fileManager.loadAccounts());
        this.transactions = new ArrayList<>(fileManager.loadTransactions());
    }

    // ------------------------------------------------------------------
    // 1. Create account
    // ------------------------------------------------------------------

    /**
     * Opens a new savings or current account.
     *
     * @param accountType "SAVINGS" or "CURRENT" (see {@link Validation#normaliseAccountType})
     */
    public Account createAccount(int accountNumber, String holderName, String accountType,
                                 double openingBalance) throws DuplicateAccountException {
        if (findAccountOrNull(accountNumber) != null) {
            throw new DuplicateAccountException(accountNumber);
        }
        double balance = Validation.round(openingBalance);
        Account account;
        if (CurrentAccount.TYPE.equalsIgnoreCase(accountType)) {
            account = new CurrentAccount(accountNumber, holderName, balance);
        } else {
            account = new SavingsAccount(accountNumber, holderName, balance);
        }
        accounts.add(account);
        recordTransaction(new Transaction(accountNumber, Transaction.ACCOUNT_OPEN, balance, 0.0,
                balance, Transaction.NO_SECOND_ACCOUNT));
        fileManager.saveAccounts(accounts);
        return account;
    }

    // ------------------------------------------------------------------
    // 2. Deposit
    // ------------------------------------------------------------------

    /** Adds money to an account. Deposits are never taxed. */
    public Transaction deposit(int accountNumber, double amount) throws InvalidAccountException {
        Account account = findAccount(accountNumber);
        double value = Validation.round(amount);
        account.credit(value);
        Transaction transaction = new Transaction(accountNumber, Transaction.DEPOSIT, value, 0.0,
                value, Transaction.NO_SECOND_ACCOUNT);
        recordTransaction(transaction);
        fileManager.saveAccounts(accounts);
        return transaction;
    }

    // ------------------------------------------------------------------
    // 3. Withdraw
    // ------------------------------------------------------------------

    /**
     * Withdraws money after automatically applying the account's tax slab.
     * The balance drops by amount + tax.
     */
    public Transaction withdraw(int accountNumber, double amount)
            throws InvalidAccountException, InsufficientBalanceException {
        Account account = findAccount(accountNumber);
        double value = Validation.round(amount);
        double tax = Validation.round(account.calculateTax(value));
        double total = Validation.round(value + tax);
        if (total > account.getBalance()) {
            throw new InsufficientBalanceException(total, account.getBalance());
        }
        account.debit(total);
        account.setBalance(Validation.round(account.getBalance()));
        Transaction transaction = new Transaction(accountNumber, Transaction.WITHDRAW, value, tax,
                total, Transaction.NO_SECOND_ACCOUNT);
        recordTransaction(transaction);
        fileManager.saveAccounts(accounts);
        return transaction;
    }

    // ------------------------------------------------------------------
    // 4. Transfer
    // ------------------------------------------------------------------

    /**
     * Moves money between two accounts. The sender is taxed exactly as if it
     * were a withdrawal; the receiver is credited the full amount untaxed.
     *
     * Both account lookups and the balance check happen before any balance is
     * touched, so a failed transfer leaves both accounts unchanged.
     *
     * @return the TRANSFER_OUT transaction recorded against the sender
     */
    public Transaction transfer(int fromAccountNumber, int toAccountNumber, double amount)
            throws InvalidAccountException, InsufficientBalanceException, InvalidTransferException {
        if (fromAccountNumber == toAccountNumber) {
            throw new InvalidTransferException("Source and destination accounts must be different.");
        }
        Account from = findAccount(fromAccountNumber);
        Account to = findAccount(toAccountNumber);

        double value = Validation.round(amount);
        double tax = Validation.round(from.calculateTax(value));
        double total = Validation.round(value + tax);
        if (total > from.getBalance()) {
            throw new InsufficientBalanceException(total, from.getBalance());
        }

        // Every check passed - now, and only now, both balances change.
        from.debit(total);
        from.setBalance(Validation.round(from.getBalance()));
        to.credit(value);
        to.setBalance(Validation.round(to.getBalance()));

        Transaction outgoing = new Transaction(fromAccountNumber, Transaction.TRANSFER_OUT, value,
                tax, total, toAccountNumber);
        Transaction incoming = new Transaction(toAccountNumber, Transaction.TRANSFER_IN, value, 0.0,
                value, fromAccountNumber);
        recordTransaction(outgoing);
        recordTransaction(incoming);
        fileManager.saveAccounts(accounts);
        return outgoing;
    }

    // ------------------------------------------------------------------
    // 5. Check balance
    // ------------------------------------------------------------------

    public double checkBalance(int accountNumber) throws InvalidAccountException {
        return findAccount(accountNumber).getBalance();
    }

    // ------------------------------------------------------------------
    // 6. Transaction history
    // ------------------------------------------------------------------

    /**
     * Every transaction for one account, read back from the persisted log so
     * the history survives a restart.
     */
    public List<Transaction> getTransactionHistory(int accountNumber) throws InvalidAccountException {
        findAccount(accountNumber);
        return readHistoryFromFile(accountNumber);
    }

    /** History lookup that also works for accounts that have been closed. */
    public List<Transaction> readHistoryFromFile(int accountNumber) {
        List<Transaction> history = new ArrayList<>();
        for (Transaction transaction : fileManager.loadTransactions()) {
            if (transaction.getAccountNumber() == accountNumber) {
                history.add(transaction);
            }
        }
        return history;
    }

    // ------------------------------------------------------------------
    // 7. Close account
    // ------------------------------------------------------------------

    /**
     * Closes an account. The balance must already be zero - a non-empty
     * account is refused with {@link AccountNotEmptyException} so the caller
     * can decide whether to settle it first.
     */
    public Account closeAccount(int accountNumber)
            throws InvalidAccountException, AccountNotEmptyException {
        Account account = findAccount(accountNumber);
        if (Validation.round(account.getBalance()) != 0.0) {
            throw new AccountNotEmptyException(accountNumber, account.getBalance());
        }
        accounts.remove(account);
        Account.decrementTotalAccounts();
        recordTransaction(new Transaction(accountNumber, Transaction.ACCOUNT_CLOSE, 0.0, 0.0, 0.0,
                Transaction.NO_SECOND_ACCOUNT));
        fileManager.saveAccounts(accounts);
        return account;
    }

    /**
     * Pays the whole remaining balance back to the holder and then closes the
     * account. This settlement payout is deliberately tax free: it is a
     * closure refund of the customer's own money, not a withdrawal, and a
     * taxed payout could never bring the balance to exactly zero.
     *
     * @return the settlement transaction, or null when the account was already empty
     */
    public Transaction settleAndCloseAccount(int accountNumber)
            throws InvalidAccountException, AccountNotEmptyException {
        Account account = findAccount(accountNumber);
        double remaining = Validation.round(account.getBalance());
        Transaction settlement = null;
        if (remaining > 0.0) {
            account.setBalance(0.0);
            settlement = new Transaction(accountNumber, Transaction.CLOSE_SETTLEMENT, remaining, 0.0,
                    remaining, Transaction.NO_SECOND_ACCOUNT);
            recordTransaction(settlement);
        }
        closeAccount(accountNumber);
        return settlement;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** Looks an account up or fails with a checked exception. */
    public Account findAccount(int accountNumber) throws InvalidAccountException {
        Account account = findAccountOrNull(accountNumber);
        if (account == null) {
            throw new InvalidAccountException(accountNumber);
        }
        return account;
    }

    private Account findAccountOrNull(int accountNumber) {
        for (Account account : accounts) {
            if (account.getAccountNumber() == accountNumber) {
                return account;
            }
        }
        return null;
    }

    public boolean accountExists(int accountNumber) {
        return findAccountOrNull(accountNumber) != null;
    }

    public List<Account> getAllAccounts() {
        return new ArrayList<>(accounts);
    }

    public List<Transaction> getAllTransactions() {
        return new ArrayList<>(transactions);
    }

    /** Preview of the tax for an amount, used by the menu confirmation screen. */
    public double previewTax(int accountNumber, double amount) throws InvalidAccountException {
        return Validation.round(findAccount(accountNumber).calculateTax(Validation.round(amount)));
    }

    private void recordTransaction(Transaction transaction) {
        transactions.add(transaction);
        fileManager.appendTransaction(transaction);
    }
}
