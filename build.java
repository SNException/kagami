import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public final class build {

    private build() { assert false; }

    private static boolean runShellCommand(final String cwd, final Consumer<String> callback, final String...cmdLine) {
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

        try {
            process.waitFor();
        } catch (final InterruptedException ex) {
            assert false : String.format("Not supposed to interrupt thread '%s'.");
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

    private static boolean clean(final String dir) {
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

    public static final BuildOptions buildOptions = new BuildOptions();

    private static final class BuildOptions {
        public String srcDir         = "src";
        public String outDir         = "bin";
        public String srcFiles       = "sources.txt";
        public String compiler       = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + "javac").getAbsolutePath();
        public String[] compilerLine = new String[] {compiler, "-J-Xms2048m", "-J-Xmx2048m", "-J-XX:+UseG1GC", "-Xdiags:verbose", "-Xlint:all", "-Xmaxerrs", "1", "-encoding", "UTF8", "--release", "17", "-g", "-d", outDir, "-sourcepath", srcDir, "@" + srcFiles};

        public String jvmExe     = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java").getAbsolutePath();
        public String entryClass = "Main";
        public String[] jvmLine  = new String[] {jvmExe, "-ea", "-Xms2048m", "-Xmx2048m", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC", "-cp", outDir, entryClass};
    }

    @Invokeable
    public static void clean() {
        final boolean success = clean(buildOptions.outDir);
        if (!success) {
            System.out.printf("Failed to clean '%s'!", buildOptions.outDir);
            System.exit(1);
        }
    }

    @Invokeable
    public static void run() {
        runShellCommand(".", (line) -> { System.out.print(line); }, buildOptions.jvmLine);
    }

    @Invokeable
    public static void build() {
        final String[] sources = getAllFiles(buildOptions.srcDir, ".java");
        if (sources == null) {
            System.out.println("Failed to find all source files for the compiling step!");
            System.exit(1);
        }

        new File(buildOptions.srcFiles).deleteOnExit();
        try (final FileOutputStream out = new FileOutputStream(buildOptions.srcFiles, /* append */ true)) {
            for (final String source : sources) {
                out.write((source + "\n").getBytes());
            }
            out.flush();
        } catch (final IOException ex) {
            System.out.println("Failed to write sources file!");
            System.exit(1);
        }


        // the directory will be recreated for us when we invoke the compiler with '-d'.
        if (Files.exists(Path.of(buildOptions.outDir))) {
            final boolean success = clean(buildOptions.outDir);
            if (!success) {
                System.out.println("Failed to cleanup previous output files!");
                System.exit(1);
            }
        }

        final StringBuilder javacOutputBuffer = new StringBuilder();
        final boolean compilationSuccess = runShellCommand(".", (line) -> { javacOutputBuffer.append(line); }, buildOptions.compilerLine);
        if (compilationSuccess) {
            if (!javacOutputBuffer.toString().isEmpty()) System.out.println(javacOutputBuffer.toString());
            System.out.println("Build success");
        } else {
            if (!javacOutputBuffer.toString().isEmpty()) System.out.println(javacOutputBuffer.toString());
            System.out.println("Build failed");
        }
    }

    @Invokeable
    public static void buildRelease() {
        buildOptions.compilerLine = new String[] {buildOptions.compiler, "-J-Xms2048m", "-J-Xmx2048m", "-J-XX:+UseG1GC", "-Xdiags:verbose", "-Xlint:all", "-Xmaxerrs", "1", "-encoding", "UTF8", "--release", "17", "-g:none", "-d", buildOptions.outDir, "-sourcepath", buildOptions.srcDir, "@" + buildOptions.srcFiles};

        final String releaseDir = "release";
        if (Files.exists(Path.of(releaseDir))) {
            final boolean success = clean(releaseDir);
            if (!success) {
                System.out.printf("Failed to clean '%s'!", releaseDir);
                System.exit(1);
            }
        }

        try {
            Files.createDirectory(Path.of(releaseDir));

            final String runLine = "@echo off\n \"" + buildOptions.jvmExe + "\" -Xms2048m -Xmx2048m -XX:+AlwaysPreTouch -XX:+UseG1GC -cp " + buildOptions.outDir + " " + buildOptions.entryClass;
            Files.writeString(Path.of(releaseDir, "Kagami.bat"), runLine, StandardOpenOption.CREATE, StandardOpenOption.SYNC);

            final String[] resources = getAllFiles("res", null);
            if (resources == null) {
                System.out.println("Failed to gather all resource files!");
                System.exit(1);
            }

            final String resDir = "res";
            Files.createDirectory(Path.of(releaseDir, resDir));
            for (final String res : resources) {
                Files.copy(Path.of(res), Path.of(releaseDir, res));
            }

            build();

            final String[] binaries = getAllFiles(buildOptions.outDir, null);
            if (binaries == null) {
                System.out.println("Failed to gather all binary files!");
                System.exit(1);
            }

            Files.createDirectory(Path.of(releaseDir, buildOptions.outDir));
            for (final String bin : binaries) {
                Files.copy(Path.of(bin), Path.of(releaseDir, bin));
            }
        } catch (final Exception ex) {
            System.out.println("Failed to build the release: " + ex.toString());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static void main(final String[] args) {
        if (!System.getProperty("java.version").equals("17.0.2")) {
            System.out.println("build.java must be executed with java version 17.0.2");
            System.exit(1);
        }

        if (args.length == 0) {
            System.out.println("Please specify the function you wish to run!");
            System.out.println("Example: java ./build.java --foobar");
            System.exit(1);
        }

        if (args.length == 1) {
            if (!args[0].startsWith("--")) {
                System.out.println("The method name must be prefixed with two dashes!");
                System.exit(1);
            }
            final String targetMethod = args[0].replace("--", "");

            for (final Method method : build.class.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) && method.getName().equals(targetMethod) && method.getAnnotation(Invokeable.class) != null && method.getParameterCount() == 0) {
                    try {
                        method.invoke(null);
                        System.exit(0);
                    } catch (final Exception ex) {
                        System.out.printf("ERROR while executing the specified method: %s\n", ex.getMessage());
                        System.exit(1);
                    }
                }
            }
            System.out.printf("Failed to find the specified function '%s'.\n", targetMethod);
            System.out.println("Make sure the function you wish to execute meets the following requirements:");
            System.out.println("\t- The visibility is public");
            System.out.println("\t- Function is static");
            System.out.println("\t- It is annotated with @Invokeable");
            System.out.println("\t- Does not take any arguments");
            System.exit(1);
        } else {
            System.out.println("Too many arguments!");
            System.out.println("Example: java ./build.java --build");
            System.exit(1);
        }
    }
}
