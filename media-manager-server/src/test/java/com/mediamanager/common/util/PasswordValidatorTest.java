package com.mediamanager.common.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {

    @Test
    void testValidPasswords() {
        assertTrue(PasswordValidator.isValid("Abcdefgh1"));
        assertTrue(PasswordValidator.isValid("VeryStrongP4ssword"));
        assertTrue(PasswordValidator.isValid("A1b2C3d4"));
    }

    @Test
    void testInvalidPasswordsShort() {
        assertFalse(PasswordValidator.isValid("Short1A"));
        assertThrows(IllegalArgumentException.class, () -> PasswordValidator.validate("Short1A"));
    }

    @Test
    void testInvalidPasswordsNoUppercase() {
        assertFalse(PasswordValidator.isValid("lowercase123"));
        assertThrows(IllegalArgumentException.class, () -> PasswordValidator.validate("lowercase123"));
    }

    @Test
    void testInvalidPasswordsNoLowercase() {
        assertFalse(PasswordValidator.isValid("UPPERCASE123"));
        assertThrows(IllegalArgumentException.class, () -> PasswordValidator.validate("UPPERCASE123"));
    }

    @Test
    void testInvalidPasswordsNoDigit() {
        assertFalse(PasswordValidator.isValid("NoDigitsHere"));
        assertThrows(IllegalArgumentException.class, () -> PasswordValidator.validate("NoDigitsHere"));
    }
}
