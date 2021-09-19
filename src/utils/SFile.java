//
// Simple file utilties
//

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class SFile {

    private SFile() {
        assert false;
    }

    public static FResult<FileInputStream> openFileForReading(final String file) {
        assert file != null;

        try {
            return new FResult<FileInputStream>(new FileInputStream(file), null);
        } catch (final IOException ex) {
            return new FResult<FileInputStream>(null, ex);
        }
    }

    public static FResult<FileOutputStream> openFileForWriting(final String file) {
        assert file != null;

        try {
            return new FResult<FileOutputStream>(new FileOutputStream(file, false), null);
        } catch (final IOException ex) {
            return new FResult<FileOutputStream>(null, ex);
        }
    }

    public static FResult<FileOutputStream> openFileForAppending(final String file) {
        assert file != null;

        try {
            return new FResult<FileOutputStream>(new FileOutputStream(file, true), null);
        } catch (final IOException ex) {
            return new FResult<FileOutputStream>(null, ex);
        }
    }

    public static FResult<byte[]> read(final FileInputStream handle) {
        assert handle != null;

        final ByteArrayOutputStream memory = new ByteArrayOutputStream();
        try {
            while (true) {
                final byte[] buffer = new byte[4096]; // likely a page
                final int readBytes = handle.read(buffer);
                if (readBytes < 0) {
                    break;
                }
                memory.write(buffer, 0, readBytes);
            }
        } catch (final IOException ex) {
            return new FResult<byte[]>(null, ex);
        }
        return new FResult<byte[]>(memory.toByteArray(), null);
    }

    public static FResult<Void> write(final FileOutputStream handle, final byte[] data) {
        assert handle != null;
        assert data   != null;

        try {
            handle.write(data);
            handle.flush();
            return new FResult<Void>(null, null);
        } catch (final IOException ex) {
            return new FResult<Void>(null, ex);
        }
    }

    public static FResult<Void> close(final Closeable handle) {
        assert handle != null;

        try {
            handle.close();
            return new FResult<Void>(null, null);
        } catch (final IOException ex) {
            return new FResult<Void>(null, ex);
        }
    }

    public static FResult<Void> fsync(final FileOutputStream handle) {
        assert handle != null;

        try {
            handle.getFD().sync();
            return new FResult<>(null, null);
        } catch (final IOException ex) {
            return new FResult<>(null, ex);
        }
    }
}
