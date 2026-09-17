package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Immutable record of a single operation on an account.
 *
 * Every field is final and there are no setters, so once a transaction is
 * created it can only be read - which is exactly what a ledger entry needs.
 */
public class Transaction {

    /** Transaction types written into the log file. */
    public static final String DEPOSIT = "DEPOSIT";
    public static final String WITHDRAW = "WITHDRAW";
    public static final String TRANSFER_OUT = "TRANSFER_OUT";
    public static final String TRANSFER_IN = "TRANSFER_IN";
    public static final String ACCOUNT_OPEN = "ACCOUNT_OPEN";
    public static final String CLOSE_SETTLEMENT = "CLOSE_SETTLEMENT";
    public static final String ACCOUNT_CLOSE = "ACCOUNT_CLOSE";
    public static final String LOAN_DISBURSED = "LOAN_DISBURSED";
    public static final String LOAN_REPAYMENT = "LOAN_REPAYMENT";

    /** Placeholder used when a transaction involves only one account. */
    public static final int NO_SECOND_ACCOUNT = -1;

    public static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static int nextId = 1;

    private final int transactionId;
    private final int accountNumber;
    private final String type;
    private final double amount;
    private final double tax;
    private final double total;
    private final int secondAccountNumber;
    private final String timestamp;

    /** Creates a brand new transaction, stamped with the current time. */
    public Transaction(int accountNumber, String type, double amount, double tax,
                       double total, int secondAccountNumber) {
        this(nextId++, accountNumber, type, amount, tax, total, secondAccountNumber,
                LocalDateTime.now().format(TIMESTAMP_FORMAT));
    }

    /** Rebuilds a transaction that was read back from the log file. */
    public Transaction(int transactionId, int accountNumber, String type, double amount,
                       double tax, double total, int secondAccountNumber, String timestamp) {
        this.transactionId = transactionId;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.tax = tax;
        this.total = total;
        this.secondAccountNumber = secondAccountNumber;
        this.timestamp = timestamp;
        if (transactionId >= nextId) {
            nextId = transactionId + 1;
        }
    }

    public int getTransactionId() {
        return transactionId;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public double getTax() {
        return tax;
    }

    public double getTotal() {
        return total;
    }

    public int getSecondAccountNumber() {
        return secondAccountNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public static int getNextId() {
        return nextId;
    }

    /** Resets the id sequence; used before reloading the log from disk. */
    public static void resetIdSequence() {
        nextId = 1;
    }

    /** One pipe separated line, the exact format stored in transactions.txt. */
    public String toFileLine() {
        StringBuilder builder = new StringBuilder();
        builder.append(transactionId).append('|')
               .append(accountNumber).append('|')
               .append(type).append('|')
               .append(amount).append('|')
               .append(tax).append('|')
               .append(total).append('|')
               .append(secondAccountNumber).append('|')
               .append(timestamp);
        return builder.toString();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("#%-5d", transactionId))
               .append(String.format("%-21s", timestamp))
               .append(String.format("%-17s", type))
               .append(String.format("Amount: Rs.%12.2f", amount))
               .append(String.format("   Tax: Rs.%10.2f", tax))
               .append(String.format("   Total: Rs.%12.2f", total));
        if (secondAccountNumber != NO_SECOND_ACCOUNT) {
            builder.append("   Other A/C: ").append(secondAccountNumber);
        }
        return builder.toString();
    }
}
