# Bank Management System with Automated Transaction Tax Calculator

A console based banking application written in **core Java (JDK 21)** for the
BE CSE *Java Project Review*. It manages savings and current accounts, and the
core feature is an **automated slab based tax** that is calculated and deducted
on every withdrawal and on the outgoing side of every transfer.

There are **no external dependencies** - no JDBC, no Maven/Gradle, no third
party libraries. Persistence is plain text file I/O using character streams.

---

## Features

| # | Menu option | What it does |
|---|-------------|--------------|
| 1 | Create Account | Opens a `SavingsAccount` or `CurrentAccount` with an account number, holder name and opening balance |
| 2 | Deposit | Adds funds (deposits are never taxed) and logs the transaction |
| 3 | Withdraw | Calculates the tax automatically, then deducts **amount + tax** |
| 4 | Transfer | Moves money between two accounts; the sender is taxed exactly like a withdrawal, the receiver is credited the full amount untaxed |
| 5 | Check Balance | Shows account number, holder, type and current balance |
| 6 | Transaction History | Lists every entry for an account, read back from `data/transactions.txt` |
| 7 | Close Account | Closes an account whose balance is zero (see below) |
| 8 | Exit | Saves and quits |

---

## The tax rule

Applied automatically on **every withdrawal** and on the **outgoing side of every
transfer**. Deposits, incoming transfers and closure settlements are not taxed.

| Withdrawal amount | Savings account | Current account |
|-------------------|-----------------|-----------------|
| Up to Rs. 50,000 | No tax | No tax |
| Rs. 50,001 - Rs. 1,00,000 | 1% | 1% |
| Above Rs. 1,00,000 | 1% | 2% |

**Worked example** (verified by `test/BankSystemTest.java` and by an end to end run):

```
Current account, balance Rs. 3,00,000
Withdraw Rs. 1,50,000
  Tax  (2% of 1,50,000) = Rs.   3,000
  Total deducted        = Rs. 1,53,000
  Remaining balance     = Rs. 1,47,000
```

The balance must cover **amount + tax**; otherwise the withdrawal is rejected
with `InsufficientBalanceException` and the balance is left untouched.

---

## How to compile and run

From the project root (`BankManagementSystem/`):

```bash
# Compile the application
javac -d out $(find src -name "*.java")

# Run it
java -cp out Main
```

On Windows (PowerShell / cmd) use:

```bat
javac -d out src\Main.java src\model\*.java src\exception\*.java src\service\*.java src\util\*.java src\menu\*.java
java -cp out Main
```

### Running the tests

The test suite is a hand rolled harness that uses core Java only (no JUnit), so
it runs anywhere a JDK does. It writes to its own `data-test/` folder and
cleans up after itself, so it never touches your real data files.

```bash
javac -d out $(find src -name "*.java")
javac -d out-test -cp out $(find test -name "*.java")
java -cp out:out-test BankSystemTest      # Windows: java -cp "out;out-test" BankSystemTest
```

Current status: **64 assertions, all passing** - tax slabs for both account
types, the worked example above, insufficient balance, invalid/duplicate
accounts, transfer atomicity, close/settlement rules, validation and
persistence across a simulated restart.

---

## Project structure

```
BankManagementSystem/
├── src/
│   ├── model/
│   │   ├── Account.java              abstract base class, static account counter
│   │   ├── SavingsAccount.java       Savings tax slab
│   │   ├── CurrentAccount.java       Current tax slab
│   │   └── Transaction.java          immutable ledger entry
│   ├── exception/
│   │   ├── InsufficientBalanceException.java
│   │   ├── InvalidAccountException.java
│   │   ├── DuplicateAccountException.java
│   │   ├── InvalidTransferException.java
│   │   └── AccountNotEmptyException.java
│   ├── service/
│   │   └── BankService.java          business layer, ArrayLists of accounts + transactions
│   ├── util/
│   │   ├── FileManager.java          BufferedReader/BufferedWriter persistence
│   │   └── Validation.java           static input validation helpers
│   ├── menu/
│   │   └── Menu.java                 console loop, catches every checked exception
│   └── Main.java                     entry point
├── test/
│   └── BankSystemTest.java           core Java test harness
├── data/
│   ├── accounts.txt                  created on first run
│   └── transactions.txt              created on first run
├── README.md
└── .gitignore
```

### Data file formats

`data/accounts.txt` - one account per line:

```
accountNumber|TYPE|holderName|balance
1001|CURRENT|Ravi Kumar|147000.0
```

`data/transactions.txt` - one transaction per line, appended, never rewritten
during normal use:

```
id|accountNumber|type|amount|tax|total|secondAccountNumber|timestamp
4|1001|WITHDRAW|150000.0|3000.0|153000.0|-1|2026-09-09 09:25:29
5|1001|TRANSFER_OUT|60000.0|600.0|60600.0|1002|2026-09-09 09:25:29
```

`secondAccountNumber` is `-1` for everything except transfers, where it points
at the other side of the transfer.

---

## Documented design decisions

**Closing an account.** `BankService.closeAccount()` refuses to close an
account that still holds money and throws `AccountNotEmptyException`, which
carries the remaining balance. The menu catches it and offers to pay the
balance out first. That settlement payout is deliberately **tax free**: it is
a refund of the customer's own money at closure rather than a withdrawal, and a
taxed payout could never bring the balance to exactly zero (paying out `B`
would cost `B + tax`, which is more than the account holds). The settlement is
logged as `CLOSE_SETTLEMENT`, followed by an `ACCOUNT_CLOSE` entry. Answering
anything other than `y` leaves the account open and untouched.

**Transfers are atomic.** Both account lookups, the self transfer check and the
balance check all happen *before* any balance is modified, so a failed transfer
leaves both accounts exactly as they were. The transfer writes two ledger
entries - `TRANSFER_OUT` on the sender (with the tax) and `TRANSFER_IN` on the
receiver (untaxed) - each pointing at the other account.

**Committed data files.** `.gitignore` excludes `data/*.txt` so the repository
ships with a clean starting state instead of one developer's test balances; the
`data/` folder itself is kept in git via `data/.gitkeep`. `FileManager` creates
the directory and both files automatically on first run, so a fresh clone works
immediately.

**Account numbers are chosen by the user** (digits only, 1 to 999999999) and
must be unique - a reused number is rejected with `DuplicateAccountException`.

**Money is rounded** to two decimal places after every calculation
(`Validation.round`) so repeated percentage maths cannot accumulate floating
point drift.

---

## OOP and Java concepts used, and where

| Concept | Where it appears |
|---------|------------------|
| **Abstraction** | `Account` is `abstract` and declares `calculateTax(double)` and `getAccountType()` |
| **Inheritance** | `SavingsAccount extends Account`, `CurrentAccount extends Account` |
| **Polymorphism** | `BankService` calls `account.calculateTax(amount)` without knowing the concrete type - the slab used is decided at runtime |
| **Method overriding** | `calculateTax()` / `getAccountType()` in both subclasses; `toString()` in `Account` and `Transaction` |
| **Encapsulation** | All model fields are `private` with getters/setters; `Transaction` fields are `final` with getters only (immutability) |
| **Constructors / overloading** | `Transaction` has two constructors (new transaction vs. one rebuilt from file); `InsufficientBalanceException` has two |
| **Static members** | `Account.totalAccounts` (running count of open accounts), `Transaction.nextId` (id sequence), every method in `Validation` |
| **Packages** | `model`, `exception`, `service`, `util`, `menu` |
| **ArrayList** | `BankService.accounts` and `BankService.transactions` |
| **Control statements** | `if/else` in the tax slabs and validation, `switch` on the menu choice |
| **Loops** | `while` for the menu loop and the input re-prompt loops, `for-each` over accounts and transactions, `for` over the characters of a name |
| **String / StringBuilder** | `StringBuilder` builds the menu screen, `Transaction.toString()` and `toFileLine()`, and every file line |
| **Exception handling** | Five user defined **checked** exceptions, thrown by `BankService` and caught in `Menu`; `NumberFormatException`, `IOException` and `NoSuchElementException` handled internally |
| **File I/O (character streams)** | `FileManager` uses `FileReader`/`BufferedReader` and `FileWriter`/`BufferedWriter`, with try-with-resources |
| **Data types and operators** | `int` account numbers and ids, `double` money, `boolean` flags, arithmetic and relational operators throughout the tax logic |

---

## Input validation and error handling

Every entry is validated before it reaches the service layer, and an invalid
entry **re-prompts instead of crashing**:

- account numbers - digits only, 1 to 999999999
- holder names - 2 to 40 letters, spaces, `.` and `'` allowed; digits rejected
- amounts - finite numbers, greater than zero (opening balance may be zero)
- account type - `1`/`S`/`SAVINGS` or `2`/`C`/`CURRENT`, any case
- menu choice - `1` to `8`

Predictable failures are checked exceptions caught at the menu layer and printed
as a single friendly line: unknown account, insufficient balance, duplicate
account number, transfer to the same account, closing a non-empty account.
File errors are caught inside `FileManager` and reported as warnings, so a
missing or corrupt line never brings down the application.

---

## Known limitations / next steps

- **No authentication layer.** Anyone at the console can operate any account;
  there is no PIN, password or login. Adding one would mean a `Customer` model
  and hashed credentials.
- **No interest calculation.** Savings accounts do not accrue interest, and
  there is no minimum balance rule or overdraft facility for current accounts.
- **File based persistence instead of JDBC.** The syllabus this project targets
  (Java fundamentals -> inheritance/interfaces -> exception handling and I/O)
  does not cover JDBC or databases, so persistence uses `java.io` character
  streams over plain text files. That means no transactions in the database
  sense, no concurrent access safety and no indexed lookups; account search is
  a linear scan of an `ArrayList`. Moving to JDBC + SQL would be the natural
  next step.
- **Single user, single session.** Two copies of the app running at once would
  overwrite each other's `accounts.txt`.
- **Tax slabs are hard coded** in the two `calculateTax()` overrides. A future
  version could read the slab table from a configuration file.
- **Closed accounts are removed** from `accounts.txt`, though their history
  stays in `transactions.txt`; there is no archive of closed account details.
