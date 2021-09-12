import java.awt.Toolkit;
import java.io.File;
import java.util.logging.Level;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;

public final class AudioUtils {

    private AudioUtils() {
        assert false;
    }

    public static void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    public static Clip createAudioClip(final String file, final float decibel) {
        assert file != null;

        try {
            final Clip clip = (Clip) AudioSystem.getLine(new Line.Info(Clip.class));
            clip.addLineListener(new LineListener() {
                @Override
                public void update(final LineEvent event) {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                }
            });

            clip.open(AudioSystem.getAudioInputStream(new File(file)));
            clip.setFramePosition(0);

            final FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            gainControl.setValue(decibel);
            return clip;
        } catch (final Exception ex) {
            Main.logger.log(Level.SEVERE, ex.getMessage(), ex.toString());
            return null;
        }
    }

    public static void playAudioClip(final Clip clip, final boolean loop) {
        assert clip != null;

        if (loop) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } else {
            clip.start();
        }
    }

    public static void stopAudioClip(final Clip clip) {
        assert clip != null;

        clip.close();
    }
}
