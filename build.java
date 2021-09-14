import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// /usr/bin/time -f "Total time elapsed: %e seconds" java.exe build.java
public final class build {

    private static final String SOURCE_DIR      = "src";
    private static final String OUTPUT_DIR      = "bin";
    private static final String SOURCES_FILE    = "sources.txt";
    private static final String COMPILER        = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + "javac.exe").getAbsolutePath();
    private static final String RELEASE         = "17";
    private static final String[] COMPILER_LINE = new String[] {COMPILER, "-J-Xms2048m", "-J-Xmx2048m", "-J-XX:+UseG1GC", "-Xdiags:verbose", "-Xlint:all", "-Xmaxerrs", "5", "-encoding", "UTF8", "--release", RELEASE, "-g", "-d", OUTPUT_DIR, "-sourcepath", SOURCE_DIR, "@" + SOURCES_FILE};

    private static final String JVM_EXE         = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe").getAbsolutePath();
    private static final String ENTRY_CLASS     = "Main";
    private static final String[] JVM_LINE      = new String[] {JVM_EXE, "-ea", "-Xms2048m", "-Xmx2048m", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC", "-cp", OUTPUT_DIR, ENTRY_CLASS};

    private static boolean runShellCommand(final String cwd, final StringBuilder buffer, final String...cmdLine) {
        Process process = null;
        try {
            final ProcessBuilder pb = new ProcessBuilder(cmdLine);
            pb.directory(new File(cwd));
            pb.redirectErrorStream(true);

            process = pb.start();

            final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            for (;;) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                buffer.append(line).append("\n");
            }
        } catch (final IOException ex) {
            return false;
        }
        return process.exitValue() == 0;
    }

    private static boolean runShellCommandAsync(final String cwd, final Consumer<String> callback, final String...cmdLine) {
        Process process = null;
        try {
            final ProcessBuilder pb = new ProcessBuilder(cmdLine);
            pb.directory(new File(cwd));
            pb.redirectErrorStream(true);

            process = pb.start();

            final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            for (;;) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                callback.accept(line + "\n");
            }
        } catch (final IOException ex) {
            return false;
        }
        return process.exitValue() == 0;
    }

    private static String[] getAllFiles(final String dir, final String prefix) {
        try (final Stream<Path> stream = Files.walk(Path.of(dir), Integer.MAX_VALUE)) {
            final ArrayList<String> files = new ArrayList<>(stream.map(String::valueOf).sorted().collect(Collectors.toList()));
            final ArrayList<String> result = new ArrayList<>(files.size());

            for (int i = 0, l = files.size(); i < l; ++i) {
                final String file = files.get(i);

                if (Files.isDirectory(Path.of(file))) {
                    continue;
                }

                if (prefix != null) {
                    if (!file.endsWith(prefix)) {
                        continue;
                    }
                }

                result.add(file);
            }
            return result.toArray(String[]::new);
        } catch (final IOException ex) {
            return null;
        }
    }

    private static boolean removeDir(final String dir) {
        try {
            final Path path = Path.of(dir);

            Files.walk(path)
                    .map(Path::toFile)
                    .sorted((o1, o2) -> -o1.compareTo(o2))
                    .forEach(File::delete);

            return true;
        } catch (final IOException ex) {
            return false;
        }
    }

    private static void run() {
        runShellCommandAsync(".", (line) -> { System.out.print(line); }, JVM_LINE);
    }

    private static void build() {
        final String[] sources = getAllFiles(SOURCE_DIR, ".java");
        if (sources == null) {
            System.out.println("Failed to find all source files for the compiling step!");
            System.exit(1);
        }

        try (final FileOutputStream out = new FileOutputStream(SOURCES_FILE, /* append */ true)) {
            for (final String source : sources) {
                out.write((source + "\n").getBytes());
            }
            out.flush();
            new File(SOURCES_FILE).deleteOnExit();
        } catch (final IOException ex) {
            System.out.println("Failed to write sources file!");
            System.exit(1);
        }


        // the directory will be recreated for us when we invoke the compiler with '-d'.
        if (Files.exists(Path.of(OUTPUT_DIR))) {
            final boolean success = removeDir(OUTPUT_DIR);
            if (!success) {
                System.out.println("Failed to cleanup previous output files!");
                System.exit(1);
            }
        }

        final StringBuilder javacOutputBuffer = new StringBuilder();
        final boolean compilationSuccess = runShellCommand(".", javacOutputBuffer, COMPILER_LINE);
        if (compilationSuccess) {
            if (!javacOutputBuffer.toString().strip().isEmpty()) {
                // can only be warnings
                if (System.console() != null && System.getenv().get("TERM") != null) {
                    final String ansiCodeReset = "\u001B[0m";
                    final String ansiCodeYellow = "\u001B[33m";
                    System.out.println(ansiCodeYellow + javacOutputBuffer.toString() + ansiCodeReset);
                } else {
                    System.out.println(javacOutputBuffer.toString());
                }
            }
            System.out.println("Build successful");
        } else {
            // can only be errors (and warnings)
            if (System.console() != null && System.getenv().get("TERM") != null) {
                final String ansiCodeReset  = "\u001B[0m";
                final String ansiCodeRed   = "\u001B[31m";
                System.out.println(ansiCodeRed + javacOutputBuffer.toString() + ansiCodeReset);
            } else {
                System.out.println(javacOutputBuffer.toString());
            }
            System.out.println("Build failed");
        }
    }

    public static void main(final String[] args) {
        boolean wantToRun = false;
        if (args.length == 1) {
            if (args[0].equals("run")) {
                wantToRun = true;
            } else {
                System.out.println("First argument must either be not specified or 'run'");
                System.exit(1);
            }
        }

        if (wantToRun) {
            run();
        } else {
            build();
        }
    }
}
