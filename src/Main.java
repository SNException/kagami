import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Main {

    public static final String VERSION = "v0.3.0";

    public static final Logger logger = allocateLogger();

    private static Logger allocateLogger() {
        LogManager.getLogManager().reset();
        final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        final boolean isDebug = isDebugMode();
        logger.setLevel(isDebug ? Level.ALL : Level.SEVERE);
        final ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(final LogRecord log) {
                return String.format("[%s][%s][%s#%s]: %s\n",
                                    new Date(log.getMillis()),
                                    log.getLevel().getLocalizedName(),
                                    log.getSourceClassName(),
                                    log.getSourceMethodName(),
                                    log.getMessage()
                );
            }
        });
        consoleHandler.setLevel(isDebug ? Level.ALL : Level.SEVERE);
        logger.addHandler(consoleHandler);

        return logger;
    }

    private static void initUncaughtExceptionHandler() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Can not run on headless env!");
        }

        // @NOTE We only want to apply this exception handler in dev mode so we do not waste CPU time checking
        // whether the exception is an instance of AssertionError, which can not happen anyway.
        if (isDebugMode()) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                if (e instanceof AssertionError aerror) {
                    handleAssert(aerror);
                }

                // @NOTE fallthrough to the default behaviour (found here: ThreadGroup#uncaughtException)
                if (!(e instanceof ThreadDeath)) {
                    System.err.print("Exception in thread \"" + t.getName() + "\" ");
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    public static void handleAssert(final AssertionError aerror) {
        System.err.println("--- ASSERTION FAILED ---");
        System.err.println();
        aerror.printStackTrace(System.err);

        // @NOTE Panic; Kill the JVM immediately.
        // I have to clue why this is not the default behaviour when triggering assert statements.
        // That just makes them almost completely pointless.
        Runtime.getRuntime().halt(-1);
    }

    public static boolean isDebugMode() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-ea");
    }

    private static void launch(final File slideshowFile) {
        EventQueue.invokeLater(() -> {
            final Display display = new Display(slideshowFile.getName());
            final SlideShowFileParser parser = new SlideShowFileParser(slideshowFile);

            try {
                final SlideShowMetaDataRec metaData = parser.parseMetaData();
                display.initAndShow(metaData.hz(), metaData.aspectRatio());
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
                logger.log(Level.INFO, String.format("Slideshow loading took %s milliseconds", delta));
            } catch (final SlideShowFileParser.ParseException ex) {
                handleParseErrorLambda.call(ex);
            }

            final FileModWatcher watcher = new FileModWatcher(Path.of(slideshowFile.getAbsolutePath()), () -> {
                try {
                    final Slide[] slideshow = parser.parseSlides();
                    display.clearMessage();
                    display.newSlideShow(slideshow);
                } catch (final SlideShowFileParser.ParseException ex) {
                    handleParseErrorLambda.call(ex);
                }
                return (Void) null;
            });
            watcher.start();
        });
    }

    public static void main(final String[] args) {
        if (isDebugMode()) {
            logger.log(Level.INFO, "Running with assertions enabled!");
        }

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
            final FileDialog chooser = new FileDialog((java.awt.Frame) null, "Kagami " + VERSION + " - Choose slideshow", FileDialog.LOAD);
            chooser.setDirectory(System.getProperty("user.dir"));
            chooser.setMultipleMode(false);
            chooser.setFilenameFilter((dir, file) -> file.endsWith(".kagami"));
            chooser.setFile("*.kagami");
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
}
