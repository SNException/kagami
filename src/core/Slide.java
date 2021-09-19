import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.logging.Level;
import javax.sound.sampled.Clip;

public final class Slide {

    public static final class Audio {
        public String file   = null;
        public float decibel = 0;
        public boolean loop  = false;
    }

    public static final class Argb {
        public Color color1 = Color.BLACK;
        public Color color2 = null;
        public float x1     = 0;
        public float y1     = 0;
        public float x2     = 0;
        public float y2     = 0;
        public boolean cyclic = false;
    }

    private final String name;
    private final Argb argb;
    private final Audio audio;;
    private final Element[] elements;

    private int screenWidth  = 0;
    private int screenHeight = 0;

    private GradientPaint gradient;
    private float gradientTargetX1;
    private float gradientTargetY1;
    private float gradientTargetX2;
    private float gradientTargetY2;

    private Clip clip = null;

    public Slide(final String name, final Argb argb, final Audio audio, final Element... elements) {
        assert name != null;
        assert argb != null;

        this.name      = name;
        this.audio     = audio;
        this.argb      = argb;
        this.elements  = elements;
    }

    public void onEnter() {
        Main.logger.log(Level.INFO, "Entering: " + name);

        if (audio != null) {
            clip = AudioUtils.createAudioClip(audio.file, audio.decibel);
            if (clip != null) {
                AudioUtils.playAudioClip(clip, audio.loop);
            }
        }
    }

    public void onExit() {
        Main.logger.log(Level.INFO, "Leaving: " + name);

        if (clip != null) {
            AudioUtils.stopAudioClip(clip);
        }
    }

    public void destroy() {
        Main.logger.log(Level.INFO, "Destroy: " + name);

        if (clip != null) {
            AudioUtils.stopAudioClip(clip);
        }
    }

    public void update() {
        for (final Element e : elements) {
            e.update();
        }
    }

    public void render(final Graphics2D g) {
        if (argb.color2 != null) { // @NOTE if the second color is set we want to treat it as a gradient
            g.setPaint(gradient);
        } else {
            g.setColor(argb.color1);
        }

        // @NOTE slide background
        g.fillRect(0, 0, screenWidth, screenHeight);

        // @NOTE render all the elements on top of the slide
        for (final Element e : elements) {
            e.render(g);
        }
    }

    public void onResize(final Graphics2D g, final int screenWidth, final int screenHeight) {
        this.screenWidth  = screenWidth;
        this.screenHeight = screenHeight;

        for (final Element e : elements) {
            e.onResize(g, screenWidth, screenHeight);
        }

        if (argb.color2 != null) { // @NOTE we do not need to calculate these if we do not have a second color (gradient)
            gradientTargetX1 = screenWidth  * (argb.x1 * 100.0f) / 100.0f;
            gradientTargetY1 = screenHeight * (argb.y1 * 100.0f) / 100.0f;
            gradientTargetX2 = screenWidth  * (argb.x2 * 100.0f) / 100.0f;
            gradientTargetY2 = screenHeight * (argb.y2 * 100.0f) / 100.0f;
            gradient         = new GradientPaint(gradientTargetX1, gradientTargetY1, argb.color1, gradientTargetX2, gradientTargetY2, argb.color2, argb.cyclic);
        }
    }

    public interface Element {
        void update();
        void render(final Graphics2D g);
        default void onResize(final Graphics2D g, final int screenWidth, final int screenHeight) {}
    }

    public static final class Form implements Element {

        public enum Type {
            RECT,
            OVAL,
        }

        private final Type type;

        private final Argb color;

        private final float xPosPercentage;
        private final float yPosPercentage;

        private final float widthPercentage;
        private final float heightPercentage;
        private final float borderSizePercentage;
        private final float rotation;
        private final Argb borderColor;

        private float targetXPosPx      = 0;
        private float targetYPosPx      = 0;
        private float targetWidthPx     = 0;
        private float targetHeightPx    = 0;
        private float targetBorderPx    = 0;

        private GradientPaint gradient;
        private float gradientTargetX1;
        private float gradientTargetY1;
        private float gradientTargetX2;
        private float gradientTargetY2;

        private GradientPaint borderGradient;
        private float borderGradientTargetX1;
        private float borderGradientTargetY1;
        private float borderGradientTargetX2;
        private float borderGradientTargetY2;

        public Form(final Type type, final Argb color, final float xPosPercentage, final float yPosPercentage, final float widthPercentage, final float heightPercentage, final float rotation, final float borderSizePercentage, final Argb borderColor) {
            assert type  != null;
            assert color != null;

            this.type = type;

            this.color = color;

            this.xPosPercentage = xPosPercentage;
            this.yPosPercentage = yPosPercentage;

            this.widthPercentage  = widthPercentage;
            this.heightPercentage = heightPercentage;

            this.rotation = rotation;

            this.borderSizePercentage = borderSizePercentage;
            this.borderColor          = borderColor;
        }

        @Override
        public void update() {
        }

        @Override
        public void render(final Graphics2D g) {
            switch (type) {
                case RECT: {
                    final Graphics2D g2 = (Graphics2D) g.create();

                    if (color.color2 != null) { // @NOTE if the second color is set we want to treat it as a gradient
                        g2.setPaint(gradient);
                    } else {
                        g2.setColor(color.color1);
                    }

                    g2.rotate(Math.toRadians(rotation), targetXPosPx + (targetWidthPx / 2), targetYPosPx + (targetHeightPx / 2));
                    g2.fillRect((int) targetXPosPx, (int) targetYPosPx, (int) targetWidthPx, (int) targetHeightPx);

                    if (borderColor.color2 != null) { // @NOTE if the second color is set we want to treat it as a gradient
                        g2.setPaint(borderGradient);
                    } else {
                        g2.setColor(borderColor.color1);
                    }
                    g2.setStroke(new BasicStroke(targetBorderPx));
                    g2.drawRect((int) targetXPosPx, (int) targetYPosPx, (int) targetWidthPx, (int) targetHeightPx);
                    g2.dispose();
                } break;

                case OVAL: {
                    final Graphics2D g2 = (Graphics2D) g.create();

                    if (color.color2 != null) { // @NOTE if the second color is set we want to treat it as a gradient
                        g2.setPaint(gradient);
                    } else {
                        g2.setColor(color.color1);
                    }

                    g2.rotate(Math.toRadians(rotation), targetXPosPx + (targetWidthPx / 2), targetYPosPx + (targetHeightPx / 2));
                    g2.fillOval((int) targetXPosPx, (int) targetYPosPx, (int) targetWidthPx, (int) targetHeightPx);

                    if (borderColor.color2 != null) { // @NOTE if the second color is set we want to treat it as a gradient
                        g2.setPaint(borderGradient);
                    } else {
                        g2.setColor(borderColor.color1);
                    }
                    g2.setStroke(new BasicStroke(targetBorderPx));
                    g2.drawOval((int) targetXPosPx, (int) targetYPosPx, (int) targetWidthPx, (int) targetHeightPx);

                    g2.dispose();
                } break;

                default: {
                    assert false;
                } break;
            }
        }

        @Override
        public void onResize(final Graphics2D g, final int screenWidth, final int screenHeight) {
            targetWidthPx    = screenWidth   * (widthPercentage      * 100.0f) / 100.0f;
            targetHeightPx   = screenHeight  * (heightPercentage     * 100.0f) / 100.0f;
            targetXPosPx     = (screenWidth  * (xPosPercentage       * 100.0f) / 100.0f) - (targetWidthPx / 2);
            targetYPosPx     = (screenHeight * (yPosPercentage       * 100.0f) / 100.0f) - (targetHeightPx / 2);
            targetBorderPx   = targetWidthPx * (borderSizePercentage * 100.0f) / 100.0f;

            if (color.color2 != null) { // @NOTE we do not need to calculate these if we do not have a second color (gradient)
                gradientTargetX1 = screenWidth  * (color.x1 * 100.0f) / 100.0f;
                gradientTargetY1 = screenHeight * (color.y1 * 100.0f) / 100.0f;
                gradientTargetX2 = screenWidth  * (color.x2 * 100.0f) / 100.0f;
                gradientTargetY2 = screenHeight * (color.y2 * 100.0f) / 100.0f;
                gradient         = new GradientPaint(gradientTargetX1, gradientTargetY1, color.color1, gradientTargetX2, gradientTargetY2, color.color2, color.cyclic);
            }

            if (borderColor.color2 != null) { // @NOTE we do not need to calculate these if we do not have a second color (gradient)
                borderGradientTargetX1 = screenWidth  * (borderColor.x1 * 100.0f) / 100.0f;
                borderGradientTargetY1 = screenHeight * (borderColor.y1 * 100.0f) / 100.0f;
                borderGradientTargetX2 = screenWidth  * (borderColor.x2 * 100.0f) / 100.0f;
                borderGradientTargetY2 = screenHeight * (borderColor.y2 * 100.0f) / 100.0f;
                borderGradient         = new GradientPaint(borderGradientTargetX1, borderGradientTargetY1, borderColor.color1, borderGradientTargetX2, borderGradientTargetY2, borderColor.color2, borderColor.cyclic);
            }
        }
    }

    public static final class Image implements Element {

        private final java.awt.Image img;

        private final float xPosPercentage;
        private final float yPosPercentage;

        private final float widthPercentage;
        private final float heightPercentage;

        private final float alpha;

        private final float borderSizePercentage;
        private final Argb borderColor;

        private float targetXPosPx      = 0;
        private float targetYPosPx      = 0;
        private float targetWidthPx     = 0;
        private float targetHeightPx    = 0;
        private float rotation          = 0;
        private float targetBorderPx    = 0;

        private GradientPaint borderGradient;
        private float borderGradientTargetX1;
        private float borderGradientTargetY1;
        private float borderGradientTargetX2;
        private float borderGradientTargetY2;

        public Image(final java.awt.Image img, final float xPosPercentage, final float yPosPercentage, final float widthPercentage, final float heightPercentage, final float alpha, final float rotation, final float borderSizePercentage, final Argb borderColor) {
            this.img = img;

            this.xPosPercentage = xPosPercentage;
            this.yPosPercentage = yPosPercentage;

            this.widthPercentage  = widthPercentage;
            this.heightPercentage = heightPercentage;

            this.rotation = rotation;

            this.alpha = alpha;

            this.borderSizePercentage = borderSizePercentage;
            this.borderColor          = borderColor;
        }

        @Override
        public void update() {
        }

        @Override
        public void render(final Graphics2D g) {
            // @NOTE create new graphics since we are modify transform and stuff
            // This might be slow, since we also have to call dispose each frame.
            // What we could do is get the AffineTransform and apply it back perhaps?
            final Graphics2D g2 = (Graphics2D) g.create();

            g2.setColor(Color.BLACK); // @TODO: Make this configurable?
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
            g2.rotate(Math.toRadians(rotation), targetXPosPx + (targetWidthPx / 2), targetYPosPx + (targetHeightPx / 2));
            g2.drawImage(img, (int) targetXPosPx, (int) targetYPosPx, (int) targetWidthPx, (int) targetHeightPx, null);
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1.0f));

            if (borderColor.color2 != null) { // @NOTE if the second color is set we want to treat it as a gradient
                g2.setPaint(borderGradient);
            } else {
                g2.setColor(borderColor.color1);
            }
            g2.setStroke(new BasicStroke(targetBorderPx));
            g2.drawRect((int) targetXPosPx, (int) targetYPosPx, (int) targetWidthPx, (int) targetHeightPx);

            g2.dispose();
        }

        @Override
        public void onResize(final Graphics2D g, final int screenWidth, final int screenHeight) {
            targetWidthPx    = screenWidth   * (widthPercentage      * 100.0f) / 100.0f;
            targetHeightPx   = screenHeight  * (heightPercentage     * 100.0f) / 100.0f;
            targetXPosPx     = (screenWidth  * (xPosPercentage       * 100.0f) / 100.0f) - (targetWidthPx / 2);
            targetYPosPx     = (screenHeight * (yPosPercentage       * 100.0f) / 100.0f) - (targetHeightPx / 2);
            targetBorderPx   = targetWidthPx * (borderSizePercentage * 100.0f) / 100.0f;

            if (borderColor.color2 != null) { // @NOTE we do not need to calculate these if we do not have a second color (gradient)
                borderGradientTargetX1 = screenWidth  * (borderColor.x1 * 100.0f) / 100.0f;
                borderGradientTargetY1 = screenHeight * (borderColor.y1 * 100.0f) / 100.0f;
                borderGradientTargetX2 = screenWidth  * (borderColor.x2 * 100.0f) / 100.0f;
                borderGradientTargetY2 = screenHeight * (borderColor.y2 * 100.0f) / 100.0f;
                borderGradient         = new GradientPaint(borderGradientTargetX1, borderGradientTargetY1, borderColor.color1, borderGradientTargetX2, borderGradientTargetY2, borderColor.color2, borderColor.cyclic);
            }
        }
    }

    public static final class Text implements Element {

        private final String[] lines;
        private final Argb argb;
        private final String fontName;

        private final float xPosPercentage;
        private final float yPosPercentage;
        private final float sizePercentage;

        private final float rotation;

        private float targetXPosPx   = 0;
        private float targetYPosPx   = 0;
        private float fontSize        = 0;
        private int style             = Font.PLAIN;
        private boolean underline     = false;
        private boolean strikeThrough = false;
        private Font font             = null;

        private GradientPaint gradient;
        private float gradientTargetX1;
        private float gradientTargetY1;
        private float gradientTargetX2;
        private float gradientTargetY2;

        public Text(final String[] lines, final Argb argb, final String fontName, final int style, final boolean underline, final boolean strikeThrough, final boolean reversed, final float xPosPercentage, final float yPosPercentage, final float sizePercentage, final float rotation) {
            assert lines    != null;
            assert argb     != null;
            assert fontName != null;

            if (reversed) {
                this.lines = new String[lines.length];
                for (int i = 0; i < lines.length; ++i) {
                    String line = lines[i];
                    line = new StringBuilder(line).reverse().toString();
                    this.lines[i] = line;
                }
            } else {
                this.lines = lines;
            }

            this.argb          = argb;
            this.fontName      = fontName;
            this.style         = style;
            this.underline     = underline;
            this.strikeThrough = strikeThrough;

            this.xPosPercentage = xPosPercentage;
            this.yPosPercentage = yPosPercentage;
            this.sizePercentage = sizePercentage;
            this.rotation       = rotation;
        }

        @Override
        public void update() {
        }

        @Override
        public void render(final Graphics2D g)  {
            g.setFont(font.deriveFont(fontSize));

            final var oldState = g.getTransform();

            final int strHeight = g.getFontMetrics().getHeight();
            float y = targetYPosPx;
            for (int i = 0, l = lines.length; i < l; ++i) {
                final String line = lines[i];

                g.rotate(Math.toRadians(rotation), targetXPosPx + (g.getFontMetrics().stringWidth(line) / 2), targetYPosPx + (strHeight / 2));

                if (i != 0) {
                    if (argb.color2 != null) { // @NOTE if the second color is set we want to treat it as a gradient
                        g.setPaint(gradient);
                    } else {
                        g.setColor(argb.color1);
                    }
                    g.drawString(line, (int) targetXPosPx, y += strHeight);

                    if (underline) {
                        final float lineWidth = g.getFontMetrics().stringWidth(line);
                        // @TODO: Instead of using this constant offset (+4) we should use a delta offset between height and the y position of the next line
                        g.fillRect((int) targetXPosPx, (int) y + 4, (int) lineWidth, strHeight / 8);
                    }

                    if (strikeThrough) {
                        final float lineWidth  = g.getFontMetrics().stringWidth(line);
                        final float lineHeight = strHeight / 8;
                        // @TODO: Instead of using this constant offset (+4) we should use a delta offset between height and the y position of the next line
                        g.fillRect((int) targetXPosPx, (int) y - (strHeight / 2) + ((int) lineHeight), (int) lineWidth, strHeight / 8);
                    }

                } else {
                    if (argb.color2 != null) { // @NOTE if the second color is set we want to treat it as a gradient
                        g.setPaint(gradient);
                    } else {
                        g.setColor(argb.color1);
                    }
                    g.drawString(line, (int) targetXPosPx, y);

                    if (underline) {
                        final float lineWidth = g.getFontMetrics().stringWidth(line);
                        // @TODO: Instead of using this constant offset (+4) we should use a delta offset between height and the y position of the next line
                        g.fillRect((int) targetXPosPx, (int) y + 4, (int) lineWidth, strHeight / 8);
                    }

                    if (strikeThrough) {
                        final float lineWidth  = g.getFontMetrics().stringWidth(line);
                        final float lineHeight = strHeight / 8;
                        // @TODO: Instead of using this constant offset (+4) we should use a delta offset between height and the y position of the next line
                        g.fillRect((int) targetXPosPx, (int) y - (strHeight / 2) + ((int) lineHeight), (int) lineWidth, strHeight / 8);
                    }
                }
                g.setTransform(oldState);
            }
        }

        @Override
        public void onResize(final Graphics2D g, final int screenWidth, final int screenHeight) {
            final float targetWidthPx = screenWidth * (sizePercentage * 100.0f) / 100.0f;
            fontSize = 0.0f;
            font = new Font(fontName, style, (int) fontSize);
            while (true) {
                final FontMetrics metrics = g.getFontMetrics(font.deriveFont(fontSize));
                final float currentWidthPx = metrics.stringWidth(lines[0]); // @NOTE only use the first line

                // @TODO: what about height???
                if (currentWidthPx >= targetWidthPx) {
                    break;
                }
                fontSize += 0.20f; // @NOTE this value has been chosen for a good reason; it seems to achieve the best accuracy while still being as high as possible to make this loop as fast as possible.
            }

            final FontMetrics metrics = g.getFontMetrics(font);
            targetXPosPx = (screenWidth  * (xPosPercentage * 100.0f) / 100.0f) - (targetWidthPx / 2);
            targetYPosPx = (screenHeight * (yPosPercentage * 100.0f) / 100.0f);// - (metrics.getHeight() / 2);

            if (argb.color2 != null) { // @NOTE we do not need to calculate these if we do not have a second color (gradient)
                gradientTargetX1 = screenWidth  * (argb.x1 * 100.0f) / 100.0f;
                gradientTargetY1 = screenHeight * (argb.y1 * 100.0f) / 100.0f;
                gradientTargetX2 = screenWidth  * (argb.x2 * 100.0f) / 100.0f;
                gradientTargetY2 = screenHeight * (argb.y2 * 100.0f) / 100.0f;
                gradient = new GradientPaint(gradientTargetX1, gradientTargetY1, argb.color1, gradientTargetX2, gradientTargetY2, argb.color2, argb.cyclic);
            }
        }
    }
}
