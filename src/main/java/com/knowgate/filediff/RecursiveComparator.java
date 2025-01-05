package com.knowgate.filediff;

import java.io.*;

import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.knowgate.filediff.Difference.*;

public class RecursiveComparator {

    public static void main(String[] args) throws IOException {
        final Map<CommandLineOption, String> options = new EnumMap<>(CommandLineOption.class);
        final String[] paths = new String[2];
        int d = 0;
        CommandLineOption opt = null;

        for (String arg : args) {
            if (arg.startsWith("-")) {
                opt = CommandLineOption.valueOf(arg.substring(1));
                if (!opt.isParametrized()) {
                    options.put(opt, "");
                    opt = null;
                }
            } else {
                if (opt!=null) {
                    if (opt.equals(CommandLineOption.p) || opt.equals(CommandLineOption.i)) {
                        if (options.containsKey(opt)) {
                            options.put(opt, options.get(opt) + "," + arg);
                        } else {
                            options.put(opt, arg);
                        }
                    } else {
                        options.put(opt, arg);
                    }
                    opt = null;
                } else {
                    paths[d++] = arg;
                }
            }
        }

        final List<Difference> differences = compare(paths[0], paths[1], options);

        if (options.containsKey(CommandLineOption.r)) {
            for (Difference diff : differences) {
                switch (diff.getOperation()) {
                    case Added:
                        System.out.println("Added " + diff.getFile2().getAbsolutePath());
                        break;
                    case Deleted:
                        System.out.println("Deleted " + diff.getFile1().getAbsolutePath());
                        break;
                    case Modified:
                        System.out.println("Modified " + diff.getFile1().getName()
                                + " " +
                                diff.getFile1().getAbsolutePath().substring(0, diff.getFile1().getAbsolutePath().length() - diff.getFile1().getName().length()) +
                                " " + diff.getFile1().length() + " bytes is " +
                                (diff.getFile1().lastModified()>diff.getFile2().lastModified() ? "newer": "older") + " than " +
                                diff.getFile2().getAbsolutePath().substring(0, diff.getFile2().getAbsolutePath().length() - diff.getFile2().getName().length()) +
                                " " + diff.getFile2().length() + " bytes");
                        break;
                }
            }
        }

        if (options.containsKey(CommandLineOption.d)) {
           differences.stream().filter(df -> FileOperation.Deleted.equals(df.getOperation())).
            map(Difference::getFile1).forEach(File::delete);
        }

        if (options.containsKey(CommandLineOption.a)) {
            final String baseTarget = paths[0].endsWith(File.separator) ? paths[0] : paths[0] + File.separator;
            for (Difference df : differences) {
                if (FileOperation.Added.equals(df.getOperation())) {
                    File targetDir = new File (baseTarget + df.getSubPath());
                    if (!targetDir.exists()) {
                        targetDir.mkdirs();
                    }
                    Path targetFile = Paths.get(baseTarget + df.getSubPath() + File.separator + df.getFile2().getName());
                    Files.copy(df.getFile2().toPath(), targetFile);
                }
            }
        }
    }

    public static List<Difference> compare(final String path1, final String path2, final Map<CommandLineOption, String> options) {
        final File dir1 = new File(path1);
        final File dir2 = new File(path2);
        final Map<String, File> files1 = listFiles(dir1, options.get(CommandLineOption.e), options.get(CommandLineOption.i), options.get(CommandLineOption.p));
        final Map<String, File> files2 = listFiles(dir2, options.get(CommandLineOption.e), options.get(CommandLineOption.i), options.get(CommandLineOption.p));
        final List<Difference> differences = new ArrayList<>(1000);
        final Function<String, String> subPath = p -> p.substring(0, p.lastIndexOf(File.separatorChar));
        return Stream.concat(
            files1.entrySet().stream().filter(e -> files2.containsKey(e.getKey()) &&
                    areDifferent(e.getValue(), files2.get(e.getKey()))).
                map(e -> mod(subPath.apply(e.getKey()), e.getValue(), files2.get(e.getKey()))),
            Stream.concat(
                files1.entrySet().stream().filter(e -> !files2.containsKey(e.getKey())).
                    map(e -> del(subPath.apply(e.getKey()), e.getValue())),
                files2.entrySet().stream().filter(e -> !files1.containsKey(e.getKey())).
                    map(e -> add(subPath.apply(e.getKey()), e.getValue())))).collect(Collectors.toList());
    }

    public static Map<String, File> listFiles(final File dir, final String fileNameRegExp,
                                              final String excludeDirNameRegExp, String protectedFileList) {
        final Map<String, File> listing = new TreeMap<>();
        final Pattern fileNamePattern = fileNameRegExp==null || fileNameRegExp.isEmpty() ? null : Pattern.compile(fileNameRegExp);
        final List<Pattern> ignorePathsPattern = excludeDirNameRegExp==null || excludeDirNameRegExp.isEmpty() ?  Collections.emptyList() :
                Arrays.stream(excludeDirNameRegExp.split(",")).map(Pattern::compile).collect(Collectors.toList());
        final List<Pattern> protectedFiles = protectedFileList==null || protectedFileList.isEmpty() ? Collections.emptyList() :
                Arrays.stream(protectedFileList.split(",")).map(Pattern::compile).collect(Collectors.toList());
        listFilesTail(dir, dir.getAbsolutePath(), fileNamePattern, ignorePathsPattern, protectedFiles, listing);
        return listing;
    }

    private static void listFilesTail(final File dir, final String base, final Pattern fileNamePattern, List<Pattern> ignorePathsPattern,
                                      final List<Pattern> protectedFiles, final Map<String, File> listing) {
        final File[] subdirs = dir.listFiles();
        if (null!=subdirs) {
            for (File f : subdirs) {
                if (f.isFile()) {
                    if (fileNamePattern!=null && fileNamePattern.matcher(f.getName()).matches() &&
                        protectedFiles.stream().noneMatch(pattern -> pattern.matcher(f.getName()).matches())) {
                        listing.put(f.getAbsolutePath().substring(base.length()), f);
                    }
                } else if (f.isDirectory() && ignorePathsPattern.stream().noneMatch(pattern -> pattern.matcher(f.getName()).matches())) {
                    listFilesTail(f, base, fileNamePattern, ignorePathsPattern, protectedFiles, listing);
                }
            }
        }
    }

    private static boolean areDifferent(File file1, File file2) {
        try {
            return file1.length() != file2.length() || !MD5HashFile(file1).equals(MD5HashFile(file2));
        } catch (IOException ignore) {
            return true;
        }
    }

    private static String MD5HashFile(File file) throws IOException {
        byte[] buf = ChecksumFile(file);
        StringBuilder res = new StringBuilder();
        for (byte b : buf) {
            res.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return res.toString();
    }

    private static byte[]  ChecksumFile(File file) throws IOException {
        InputStream fis = new FileInputStream(file);
        byte[] buf = new byte[1024];
        try {
            MessageDigest complete = MessageDigest.getInstance("MD5");
            int n;
            do {
                n = fis.read(buf);
                if (n > 0) {
                    complete.update(buf, 0, n);
                }
            } while (n != -1);
            fis.close();
            return complete.digest();
        } catch (NoSuchAlgorithmException neverThrown) {
            return null;
        }
    }
}
