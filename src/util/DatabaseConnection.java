package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC connection helper for the Bank Management System.
 *
 * IMPORTANT - this class is NOT used by the running application. The project
 * persists its data with java.io character streams through {@link FileManager},
 * as explained in the README and in the report. This class is the migration
 * path described under "Future Scope": it is the single place that knows how to
 * open a database connection, so moving the application from text files to a
 * relational database means writing DAO classes against this helper and leaving
 * every other layer untouched.
 *
 * It compiles with the plain JDK, because java.sql is part of the standard
 * library. To actually CONNECT, two things are needed at run time:
 *
 *   1. a running database server holding the schema (see data/schema.sql if you
 *      add one), and
 *   2. the vendor's JDBC driver JAR on the classpath, for example
 *
 *        javac -d out $(find src -name "*.java")
 *        java -cp out:lib/mysql-connector-j-8.4.0.jar Main
 *
 * Without the driver, getConnection() throws SQLException - it never crashes
 * the application, which is why every method here reports failure instead of
 * terminating.
 */
public final class DatabaseConnection {

    // ------------------------------------------------------------------
    // Connection settings - change these to match your own database
    // ------------------------------------------------------------------

    /** MySQL. For SQLite use: "jdbc:sqlite:data/bank.db" (no user/password). */
    private static final String URL =
            "jdbc:mysql://localhost:3306/bank_management?useSSL=false&serverTimezone=UTC";

    private static final String USER = "root";

    /**
     * Kept here only because this is an academic project. In anything real the
     * password belongs in a configuration file or an environment variable that
     * is never committed to version control.
     */
    private static final String PASSWORD = "";

    /**
     * Driver class name. Since JDBC 4.0 the driver registers itself when the
     * JAR is on the classpath, so loading it by name is optional - it is kept
     * here because older JDBC examples and many course materials still show it.
     */
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    /** One shared connection for the whole application. */
    private static Connection connection;

    private DatabaseConnection() {
        // utility class - never instantiated
    }

    // ------------------------------------------------------------------
    // Opening and closing
    // ------------------------------------------------------------------

    /**
     * Returns the shared connection, opening it on the first call.
     *
     * @return an open connection to the bank database
     * @throws SQLException if the driver is missing or the server refuses the
     *                      connection - the caller decides what to tell the user
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName(DRIVER);
            } catch (ClassNotFoundException e) {
                throw new SQLException("JDBC driver not found on the classpath: " + DRIVER
                        + ". Add the connector JAR with -cp and try again.", e);
            }
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
        }
        return connection;
    }

    /**
     * True when a connection can be opened. Useful for a start-up check that
     * decides whether to run against the database or fall back to the files.
     */
    public static boolean isAvailable() {
        try {
            return getConnection() != null;
        } catch (SQLException e) {
            System.out.println("[Database] Not available: " + e.getMessage());
            return false;
        }
    }

    /** Closes the shared connection. Safe to call even if it was never opened. */
    public static void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.out.println("[Database] Could not close the connection: " + e.getMessage());
        } finally {
            connection = null;
        }
    }
}
