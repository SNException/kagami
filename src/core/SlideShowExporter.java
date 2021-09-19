import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;
import javax.imageio.ImageIO;

public final class SlideShowExporter {

    private final Canvas canvas;
    private final HashMap<RenderingHints.Key, Object> renderingHints;
    private final Slide[] slideshow;
    private final String dst;

    public SlideShowExporter(final Canvas canvas, final HashMap<RenderingHints.Key, Object> renderingHints, final Slide[] slideshow, final String dst) {
        assert canvas          != null;
        assert renderingHints  != null;
        assert slideshow       != null;
        assert dst             != null;

        this.canvas         = canvas;
        this.renderingHints = renderingHints;
        this.slideshow      = slideshow;
        this.dst            = dst;
    }

    public boolean export() {
        final File dstDir = new File(dst);
        dstDir.mkdirs();

        for (int i = 0; i < slideshow.length; ++i) {
            final BufferedImage slideImage = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = slideImage.createGraphics();
            g.setRenderingHints(renderingHints);
            slideshow[i].render(g);
            try {
                javax.imageio.ImageIO.write(slideImage, "png", new java.io.File("export/slide_" + (i + 1) + ".png"));
            } catch (final java.io.IOException ex) {
                Main.logger.log(Level.SEVERE, ex.getMessage(), ex);
                return false;
            }
        }
        final FResult<java.io.FileOutputStream> handleResult = SFile.openFileForWriting("export/slideshow.html");
        if (handleResult.success) {
            final java.io.FileOutputStream handle = handleResult.data;
            final StringBuilder htmlImageTags = new StringBuilder();
            for (int i = 0; i < slideshow.length; ++i) {
                final String slideName = "slide_" + (i + 1) + ".png";
                htmlImageTags.append("<div>\n");
                htmlImageTags.append(String.format("<img src=\"%s\"", slideName)).append("\n"); // @TODO: size
                htmlImageTags.append("</div>\n");
            }
            final String html =
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<body>\n" +
                "<h1>Slideshow</h1>\n" +
                htmlImageTags.toString() +
                "</body>\n" +
                "</html>\n";

            SFile.write(handle, html.getBytes());
            SFile.fsync(handle);
            SFile.close(handle);
        } else {
            Main.logger.log(Level.SEVERE, handleResult.error.getMessage(), handleResult.error);
            return false;
        }
        return true;
    }
}
