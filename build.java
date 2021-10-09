import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public final class build {

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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface Invokeable {}

    @Invokeable
    public static void run() {
        final String OUTPUT_DIR  = "bin";
        final String JVM_EXE     = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe").getAbsolutePath();
        final String ENTRY_CLASS = "Main";
        final String[] JVM_LINE  = new String[] {JVM_EXE, "-ea", "-Xms2048m", "-Xmx2048m", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC", "-cp", OUTPUT_DIR, ENTRY_CLASS};

        final ArrayList<String> cmdLine = new ArrayList<>();
        for (final String jvmArg : JVM_LINE) cmdLine.add(jvmArg);

        runShellCommandAsync(".", (line) -> { System.out.print(line); }, cmdLine.toArray(String[]::new));
    }

    @Invokeable
    public static void build() {
        final String SOURCE_DIR      = "src";
        final String OUTPUT_DIR      = "bin";
        final String SOURCES_FILE    = "sources.txt";
        final String COMPILER        = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + "javac.exe").getAbsolutePath();
        final String RELEASE         = "17";
        final String[] COMPILER_LINE = new String[] {COMPILER, "-J-Xms2048m", "-J-Xmx2048m", "-J-XX:+UseG1GC", "-Xdiags:verbose", "-Xlint:all", "-Xmaxerrs", "5", "-encoding", "UTF8", "--release", RELEASE, "-g", "-d", OUTPUT_DIR, "-sourcepath", SOURCE_DIR, "@" + SOURCES_FILE};

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
            if (!javacOutputBuffer.toString().isEmpty()) System.out.println(javacOutputBuffer.toString());
            System.out.println("Build success");
        } else {
            if (!javacOutputBuffer.toString().isEmpty()) System.out.println(javacOutputBuffer.toString());
            System.out.println("Build failed");
        }
    }

    public static void main(final String[] args) {
        if (args.length == 0) {
            System.out.println("Please specify the function you wish to run!");
            System.out.println("Example: java.exe ./build.java --build");
            System.exit(1);
        }

        if (args.length == 1) {
            if (!args[0].startsWith("--")) {
                System.out.println("The method name must be prefixed with two dashes!");
                System.exit(1);
            }
            final String targetMethod = args[0].replace("--", "");

            final Class clazz = build.class;
            for (final Method method : clazz.getDeclaredMethods()) {
                final int modifiers = method.getModifiers();
                if (Modifier.isPublic(modifiers)) {
                    if (method.getName().equals(targetMethod) && method.getAnnotation(Invokeable.class) != null) {
                        try {
                            method.invoke(null);
                            System.exit(0);
                        } catch (final Exception ex) {
                            System.out.println("ERROR while executing the specified method: " + ex.getMessage());
                            System.exit(1);
                        }
                    }
                }
            }
            System.out.println("Failed to find the specified method! Make sure the method you wish to execute is 'public' and is annotated with @Invokeable.");
            System.exit(1);
        } else {
            System.out.println("Too many arguments!");
            System.exit(1);
        }
    }
}
