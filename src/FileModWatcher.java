import java.io.IOException;
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

    public FileModWatcher(final Path interestingFile, final Lambdas.Nullary<?> callback) {
        assert interestingFile != null;
        assert callback        != null;

        this.interestingFile = interestingFile;
        this.callback        = callback;
    }

    public synchronized void start() {
        try {
            final WatchService watcher = FileSystems.getDefault().newWatchService();
            Path dir = interestingFile.getParent();
            if (dir == null) { // @NOTE no parent
                dir = Path.of(System.getProperty("user.dir"));
            }
            dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            for (;;) {
                final WatchKey key = watcher.take();

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
    }
}
