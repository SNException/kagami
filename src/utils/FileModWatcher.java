import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.logging.Level;

public final class FileModWatcher {

    private final Path interestingFile;
    private final Lambdas.Nullary<?> callback;
    private WatchService watcher;
    private volatile boolean polling = false;

    public FileModWatcher(final Path interestingFile, final Lambdas.Nullary<?> callback) {
        assert interestingFile != null;
        assert callback        != null;

        this.interestingFile = interestingFile;
        this.callback        = callback;

        try {
            watcher = FileSystems.getDefault().newWatchService();
            Main.logger.log(Level.INFO, "Platform does support event based file watching.");
        } catch (final IOException ex) {
            Main.logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (final UnsupportedOperationException ex) {
            Main.logger.log(Level.WARNING, "Event based file watching is not supported on this platform.");
        }
    }

    public synchronized void start() {
        if (polling) return;

        polling = true;

        if (watcher != null) {
            smartPolling();
        } else { // @NOTE platform does not support event based file watching
            stupidPolling();
        }
    }

    public synchronized void stop() {
        polling = false;

        if (watcher != null) {
            try {
                watcher.close();
            } catch (final IOException ex) {
                Main.logger.log(Level.INFO, ex.getMessage(), ex);
            }
        }
    }

    private synchronized void smartPolling() {
        final Thread thread = new Thread(() -> {
            try {
                Path dir = interestingFile.getParent();
                if (dir == null) { // @NOTE no parent
                    dir = Path.of(System.getProperty("user.dir"));
                }
                dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                while (polling) {

                    WatchKey key = null;
                    try {
                        key = watcher.take();
                    } catch (final ClosedWatchServiceException ex) {
                        // @NOTE user has called stop
                        return;
                    }

                    // @NOTE Prevent receiving two separate ENTRY_MODIFY events: file modified
                    // and timestamp updated. Instead, receive one ENTRY_MODIFY event
                    // with two counts.
                    // @TODO That is pretty 'yikes' don't you think?
                    // Probably better to check the timestamp of the file.
                    Thread.sleep(50);

                    for (final WatchEvent<?> event: key.pollEvents()) {
                        final WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        final WatchEvent<Path> evt = (WatchEvent<Path>) event;

                        final Path fileName = evt.context();
                        if (fileName.getFileName().equals(interestingFile.getFileName())) {
                            Main.logger.log(Level.INFO, "Received mod event");
                            callback.call();
                        }

                        final boolean valid = key.reset();
                        if (!valid) {
                            break;
                        }
                    }
                }
            } catch (final IOException ex) {
                Main.logger.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (final InterruptedException ex) {
                assert false : "Not supposed to interrupt this.";
            }
        }, "file_mod_watcher_smart_thread");

        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private synchronized void stupidPolling() {
        final Thread thread = new Thread(() -> {
            long last = interestingFile.toFile().lastModified();
            while (polling) {
                long now = interestingFile.toFile().lastModified();
                if (now > last) {
                    try {
                        callback.call();
                    } finally {
                        last = now;
                    }
                } else {
                    Thread.yield();
                    try {
                        Thread.sleep(500);
                    } catch (final InterruptedException ex) {
                        assert false : "Not supposed to interrupt this.";
                    }
                }
            }
        }, "file_mod_watcher_smart_thread");

        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }
}
