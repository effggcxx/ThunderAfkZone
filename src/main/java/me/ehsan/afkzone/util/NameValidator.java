package me.ehsan.afkzone.util;

import java.util.regex.Pattern;

/**
 * Shared validation for user-facing names (zones, rewards, etc.).
 *
 * <p>Both zone and reward names use the same character rules, so the regex is
 * defined once here instead of being duplicated at each call site. The pattern
 * is precompiled for efficiency since these checks run on command input.
 */
public final class NameValidator {

    /** Allowed characters: letters, digits, underscores, and hyphens. */
    public static final String NAME_PATTERN = "[a-zA-Z0-9_\\-]+";

    private static final Pattern COMPILED = Pattern.compile(NAME_PATTERN);

    private NameValidator() {}

    /**
     * Returns true if the given name is non-null and contains only letters,
     * digits, underscores, and hyphens (i.e. no spaces or special characters).
     */
    public static boolean isValidName(String name) {
        return name != null && COMPILED.matcher(name).matches();
    }
}