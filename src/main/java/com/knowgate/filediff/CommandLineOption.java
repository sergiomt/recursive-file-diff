package com.knowgate.filediff;

public enum CommandLineOption {
    r(false, "Report differences"),
    d(false, "Remove deleted files from first directory"),
    a(false, "Add new files from second directory"),
    e(true, "Regular expression that file names must match"),
    i(true, "Ignore directories which name matches the given regular expression"),
    p(true, "Protected file");

    private final boolean parametrized;

    private final String description;

    CommandLineOption(boolean parametrized, String description) {
        this.parametrized = parametrized;
        this.description = description;
    }

    public boolean isParametrized() {
        return parametrized;
    }
}
