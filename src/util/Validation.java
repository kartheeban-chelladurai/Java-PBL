package util;

/**
 * Static helpers used by the console layer to validate every entry before it
 * reaches the service layer. Nothing here has state, so the class is never
 * instantiated.
 */
public final class Validation {

    /** Longest account number accepted (keeps the value inside an int). */
    public static final int MAX_ACCOUNT_NUMBER = 999999999;

    /** Loan tenure limits, in months. */
    public static final int MIN_TENURE_MONTHS = 1;
    public static final int MAX_TENURE_MONTHS = 360;

    private Validation() {
        // utility class
    }

    /** True when the text is a whole number in the 1 .. 999999999 range. */
    public static boolean isValidAccountNumber(String input) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.length() > 9) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return false;
            }
        }
        int value = Integer.parseInt(trimmed);
        return value > 0 && value <= MAX_ACCOUNT_NUMBER;
    }

    /** Parses an already validated account number. */
    public static int parseAccountNumber(String input) {
        return Integer.parseInt(input.trim());
    }

    /** Names must be 2-40 characters of letters, spaces or a single quote/dot. */
    public static boolean isValidName(String input) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.length() < 2 || trimmed.length() > 40) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!Character.isLetter(c) && c != ' ' && c != '.' && c != '\'') {
                return false;
            }
        }
        return true;
    }

    /** True when the text is a finite number greater than zero. */
    public static boolean isPositiveAmount(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        try {
            double value = Double.parseDouble(input.trim());
            return isPositiveAmount(value);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** True when the number is finite, greater than zero and within limits. */
    public static boolean isPositiveAmount(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0
                && value <= 1_000_000_000_000.0;
    }

    /** Opening balances may be zero, unlike deposits and withdrawals. */
    public static boolean isNonNegativeAmount(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        try {
            double value = Double.parseDouble(input.trim());
            return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0
                    && value <= 1_000_000_000_000.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Parses an already validated amount. */
    public static double parseAmount(String input) {
        return round(Double.parseDouble(input.trim()));
    }

    /** Accepts 1/S/SAVINGS or 2/C/CURRENT in any case. */
    public static boolean isValidAccountType(String input) {
        return normaliseAccountType(input) != null;
    }

    /** Returns SAVINGS, CURRENT or null when the text is not a known type. */
    public static String normaliseAccountType(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim().toUpperCase();
        if (value.equals("1") || value.equals("S") || value.equals("SAVINGS")) {
            return "SAVINGS";
        }
        if (value.equals("2") || value.equals("C") || value.equals("CURRENT")) {
            return "CURRENT";
        }
        return null;
    }

    /** True when the text is a whole number between 1 and maxOption. */
    public static boolean isValidMenuChoice(String input, int maxOption) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return false;
            }
        }
        int choice = Integer.parseInt(trimmed);
        return choice >= 1 && choice <= maxOption;
    }

    // ------------------------------------------------------------------
    // Loan specific validation
    // ------------------------------------------------------------------

    /** Loan tenure must be a whole number of months, 1 to 360 (30 years). */
    public static boolean isValidTenure(String input) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.length() > 3) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return false;
            }
        }
        int months = Integer.parseInt(trimmed);
        return months >= MIN_TENURE_MONTHS && months <= MAX_TENURE_MONTHS;
    }

    /** Parses an already validated tenure. */
    public static int parseTenure(String input) {
        return Integer.parseInt(input.trim());
    }

    /** Loan ids follow the same rule as account numbers: digits, greater than zero. */
    public static boolean isValidLoanId(String input) {
        return isValidAccountNumber(input);
    }

    /** Parses an already validated loan id. */
    public static int parseLoanId(String input) {
        return Integer.parseInt(input.trim());
    }

    /** Accepts 1/P/PERSONAL or 2/E/EDUCATION in any case. */
    public static boolean isValidLoanType(String input) {
        return normaliseLoanType(input) != null;
    }

    /** Returns PERSONAL, EDUCATION or null when the text is not a known type. */
    public static String normaliseLoanType(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim().toUpperCase();
        if (value.equals("1") || value.equals("P") || value.equals("PERSONAL")) {
            return "PERSONAL";
        }
        if (value.equals("2") || value.equals("E") || value.equals("EDUCATION")) {
            return "EDUCATION";
        }
        return null;
    }

    /** Rounds a money value to two decimal places. */
    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
