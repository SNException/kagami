import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Main {

    public static final String VERSION = "v0.2.0";

    public static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private static void initLogging() {
        final boolean isDebug = isDebugMode();
        LogManager.getLogManager().reset();
        final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        logger.setLevel(isDebug ? Level.ALL : Level.SEVERE);
        final ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(isDebug ? Level.ALL : Level.SEVERE);
        logger.addHandler(consoleHandler);
    }

    // @NOTE this does not work when we are running inside a .jar file
    private static void loadAllClassesIntoMemory() {
        final String root = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath();
        final ArrayList<String> files = new ArrayList<>();
        try (final Stream<Path> stream = Files.walk(Paths.get(root), Integer.MAX_VALUE)) {
            files.addAll(stream.map(String::valueOf).sorted().collect(Collectors.toList()));
        } catch (final IOException ex) {
            logger.log(Level.INFO, "Failed to find all class files to load.", ex);
            return;
        }

        for (int i = 0, l = files.size(); i < l; ++i) {
            final String file = files.get(i);
            if (Files.isDirectory(Paths.get(file)) || !file.endsWith(".class")) {
                continue;
            }

            final String classDef = file.replace(root + File.separator, "").replaceAll("\\" + File.separator, "\\.").replace(".class", "");
            try {
                final Class<?> c = Class.forName(classDef);
                if (c != null) {
                    logger.log(Level.INFO, String.format("Loaded '%s' into memory!\n", c.getName()));
                } else {
                    logger.log(Level.INFO, "Failed to load all classes into memory beforehand!");
                    return;
                }
            } catch (final Exception ex) {
                logger.log(Level.INFO, "Failed to load all classes into memory beforehand!", ex);
                return;
            }
        }
    }

    private static void initUncaughtExceptionHandler() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Can not run on headless env!");
        }

        // @NOTE We only want to apply this exception handler in dev mode so we do not waste CPU time checking
        // whether the exception is an instance of AssertionError, which can not happen anyway.
        if (isDebugMode()) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                if (e instanceof AssertionError) {
                    handleAssert((AssertionError) e);
                }

                // @NOTE fallthrough to the default behaviour (found here: ThreadGroup#uncaughtException)
                if (!(e instanceof ThreadDeath)) {
                    System.err.print("Exception in thread \"" + t.getName() + "\" ");
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    public static void handleAssert(final AssertionError e) {
        System.err.println("--- ASSERTION FAILED ---");
        System.err.println();
        e.printStackTrace(System.err);

        show_dialog: {
            final StackTraceElement[] trace = e.getStackTrace();
            if (trace != null && trace.length >= 1) {
                final String file = trace[0].getClassName();
                final String proc = trace[0].getMethodName();
                final long   line = trace[0].getLineNumber();

                if (file != null && proc != null && line != -1) {
                    final javax.swing.JTextArea area = new javax.swing.JTextArea();
                    final String message = "Yikes! We have triggered an assertion!\n" +
                                           "Progam will terminate immediately.\n\n" +
                                           "The offending assert has the following location:\n" +
                                           String.format("FILE: %s\nPROC: %s\nLINE: %s\n", file, proc, line);
                    area.setText(message);
                    area.setEditable(false);
                    area.setFont(new java.awt.Font("Consolas", java.awt.Font.BOLD, 16));
                    area.setBackground(new java.awt.Color(240, 240, 240));
                    area.setForeground(new java.awt.Color(150, 50, 50));
                    final Object[] buttons = new Object[] {"Okay", "Open 'assert' in editor"};
                    int[] choice = {-1};
                    try {
                        if (java.awt.EventQueue.isDispatchThread()) {
                            choice[0] = javax.swing.JOptionPane.showOptionDialog(null, area, "FATAL", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.ERROR_MESSAGE, null, buttons, buttons[0]);
                        } else {
                            java.awt.EventQueue.invokeAndWait(() -> {
                                choice[0] = javax.swing.JOptionPane.showOptionDialog(null, area, "FATAL", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.ERROR_MESSAGE, null, buttons, buttons[0]);
                            });
                        }
                    } catch (final java.lang.reflect.InvocationTargetException | InterruptedException ex) {
                        // @NOTE can not happen I think
                    }
                    if (choice[0] == 1) {
                        try {
                            final String assertFilePath = file.replace("\\.", "\\" + File.separator) + ".java";
                            // @NOTE
                            // Example:
                            // gvim.exe --remote-silent +__LINE__ src/__FILE__
                            // @TODO: Does not work when the assertions is thrown inside an inner class
                            final String assertEditor = new String(Files.readAllBytes(Paths.get("assert_editor.txt"))).replaceAll("__LINE__", String.valueOf(line)).replace("__FILE__", assertFilePath);
                            Runtime.getRuntime().exec(assertEditor);
                        } catch (final IOException ex) {
                            System.err.println("failed to open editor: " + ex.getMessage());
                        }
                    }
                }
            }
        }

        // @NOTE Kill the JVM immediately.
        // I have to clue why this is not the default behaviour when triggering assert statements.
        // That just makes them almost completely pointless.
        Runtime.getRuntime().halt(-1);
    }

    public static boolean isDebugMode() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-ea");
    }

    public static void main(final String[] args) {
        initLogging();

        if (isDebugMode()) {
            logger.log(Level.INFO, "Running with assertions enabled!");
        }

        // @NOTE
        // As you know loading classes into memory happens automatically. However,
        // it is done when the class is referenced for the very first time (although that is JVM dependent). Since code is usally scattered across
        // different files it is hard to tell when exactly that happens. And not only is it hard to tell it can also happen at times
        // where it is bad. For example, imagine a user clicking a button which for the very first time loads a class which contains the 'button run code'.
        // This will cause lag for user. And that latency might be severe depending on how much stuff has to be loaded. For all we know there can be
        // huge amounts of asset loading code in initializer blocks.
        // Anyway, my point is that I happily sacrifice some startup time so that we can have a more predictable and performent runtime.
        loadAllClassesIntoMemory();

        // @NOTE
        // We do this after loading every class into memory. That is because I want to 'ensure' that
        // no other static initializer code (inside external dependencies for example) resets my UncaughtExceptionHandler.
        // This could still happen in function calls but I guess it is less likey. Anyway if a library actually does
        // set the UncaughtExceptionHandler we should strongly reconsider if using that library is a good idea to begin with.
        initUncaughtExceptionHandler();

        // @NOTE Let's try to collect some garbage we have made so far
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();

        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (final Exception ex) {
            logger.log(Level.INFO, "Failed to set system look and feel.", ex);
        }

        if (args.length == 0) {
            final FileDialog chooser = new FileDialog((java.awt.Frame) null, "Kagami - Choose slideshow", FileDialog.LOAD);
            chooser.setDirectory(System.getProperty("user.dir"));
            chooser.setMultipleMode(false);
            chooser.setFilenameFilter((dir, file) -> file.endsWith(".kagami"));
            chooser.setLocation(GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint());
            chooser.setVisible(true);
            final String file = chooser.getFile();
            if (file != null) {
                launch(new File(chooser.getDirectory() + file));
            } else {
                System.exit(0);
            }
        } else {
            launch(new File(args[0]));
        }
    }

    private static void launch(final File slideshowFile) {
        EventQueue.invokeLater(() -> {
            final Display display = new Display(slideshowFile.getName());
            final SlideShowFileParser parser = new SlideShowFileParser(slideshowFile);

            try {
                final SlideShowMetaData metaData = parser.parseMetaData();
                display.initAndShow(metaData.hz, metaData.aspectRatio);
            } catch (final SlideShowFileParser.ParseException ex) {
                javax.swing.JOptionPane.showMessageDialog(null, ex.getMessage(), "Error parsing metadata", javax.swing.JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            final Lambdas.Unary<Void, SlideShowFileParser.ParseException> handleParseErrorLambda = (ex) -> {
                if (isDebugMode()) ex.printStackTrace(System.err);
                display.destroyAllSlides();
                display.showMessage(ex.getMessage());
                AudioUtils.beep(); // @NOTE draw attention to in case it is behind the editor
                return (Void) null;
            };

            try {
                final long begin = System.nanoTime() / 1000000;
                final Slide[] initialSlideshow   = parser.parseSlides();
                display.clearMessage();
                display.newSlideShow(initialSlideshow);
                final long delta = (System.nanoTime() / 1000000) - begin;
                logger.log(Level.INFO, String.format("Slideshow loading took %s milliseconds\n", delta));
            } catch (final SlideShowFileParser.ParseException ex) {
                handleParseErrorLambda.call(ex);
            }

            final Thread thread = new Thread(() -> {
                // @NOTE I know that there is the PathWatcher Api in Java. However, that API is just nuts.
                // In fact it is so crazy that I rather do this stupid poll solution.
                long last = slideshowFile.lastModified();
                while (true) {
                    long now = slideshowFile.lastModified();
                    if (now > last) {
                        try {
                            final Slide[] slideshow = parser.parseSlides();
                            display.clearMessage();
                            display.newSlideShow(slideshow);
                        } catch (final SlideShowFileParser.ParseException ex) {
                            handleParseErrorLambda.call(ex);
                        } finally {
                            last = now;
                        }
                    }

                    try {
                        Thread.yield();
                        //
                        // @TODO Make poll rate configurable?
                        //

                        //
                        // @TODO There should be a program argument (?) to turn off hotloading.
                        // This would be useful when you are done making frequent changes to your presentation.
                        // Would be system resource waste to have this poll during presentation. Perhaps disable 
                        // hotloading when we are in 'presentation mode' (fullscreen).
                        //
                        Thread.sleep(250, 0);
                    } catch (final InterruptedException ex) {
                        assert false : "Not supposed to interrupt this thread!";
                    }
                }
            });
            thread.setName("hotloader_thread");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            thread.start();
        });
    }
}
