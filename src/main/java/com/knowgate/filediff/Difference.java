package com.knowgate.filediff;

import java.io.File;

public class Difference {

    private final FileOperation operation;

    private final String subpath;

    private final File file1;

    private final File file2;

    public Difference(FileOperation operation, String subpath, File file1, File file2) {
        this.operation = operation;
        this.subpath = subpath;
        this.file1 = file1;
        this.file2 = file2;
    }

    public FileOperation getOperation() {
        return operation;
    }

    public File getFile1() {
        return file1;
    }

    public File getFile2() {
        return file2;
    }

    public String getSubPath() {
        return subpath;
    }

    public static Difference add(String subpath, File file2) {
        return new Difference(FileOperation.Added, subpath, null, file2);
    }

    public static Difference del(String subpath, File file1) {
        return new Difference(FileOperation.Deleted, subpath, file1, null);
    }

    public static Difference mod(String subpath, File file1, File file2) {
        return new Difference(FileOperation.Modified, subpath, file1, file2);
    }
}
