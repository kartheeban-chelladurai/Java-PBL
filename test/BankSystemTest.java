import java.io.File;
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
import service.BankService;
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
        assertTrue("validation: menu choice 8 accepted", Validation.isValidMenuChoice("8"));
        assertTrue("validation: menu choice 9 rejected", !Validation.isValidMenuChoice("9"));
    }

    // ------------------------------------------------------------------
    // Harness plumbing
    // ------------------------------------------------------------------

    private static BankService newBank() {
        return new BankService(new FileManager(TEST_DATA_DIR));
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
