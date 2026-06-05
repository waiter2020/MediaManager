package com.mediamanager.common.util;

import java.util.regex.Pattern;

/**
 * Password strength validator.
 * Rules: minimum 8 characters, at least one uppercase, one lowercase, one digit.
 */
public final class PasswordValidator {

    private PasswordValidator() {
    }

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    /**
     * Validates password strength.
     *
     * @param password the raw password to validate
     * @throws IllegalArgumentException with a descriptive message if validation fails
     */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (!UPPERCASE.matcher(password).find()) {
            throw new IllegalArgumentException(
                    "Password must contain at least one uppercase letter");
        }
        if (!LOWERCASE.matcher(password).find()) {
            throw new IllegalArgumentException(
                    "Password must contain at least one lowercase letter");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new IllegalArgumentException(
                    "Password must contain at least one digit");
        }
    }

    /**
     * @return true if the password meets all strength requirements
     */
    public static boolean isValid(String password) {
        try {
            validate(password);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
