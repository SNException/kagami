import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.imageio.ImageIO;

public final class SlideShowFileParser {

    private final File file;

    public SlideShowFileParser(final File file) {
        assert file != null;

        this.file = file;
    }

    public final class ParseException extends Exception {
        private static final long serialVersionUID = 1L; // @NOTE *sigh*

        public ParseException(final String str, final Object... args) {
            super(String.format(str, args));
        }
    }

    public SlideShowMetaData parseMetaData() throws ParseException {
        final StringBuilder fileContent = new StringBuilder();
        final boolean success = readFileIntoMemory(fileContent);
        if (!success) {
            throw new ParseException("Failed to read '%s'\n", file.getAbsolutePath());
        }

        final String[] lines = fileContent.toString().split("\n");
        final String metaLine = lines[0].strip();

        if (isHeader(metaLine)) {
            final String meta = metaLine.substring(metaLine.indexOf("(") + 1, metaLine.indexOf(")"));
            final String[] metaData = meta.split(";");
            if (metaData.length != 2) {
                throw new ParseException("Error on line %s: You either have to few or too many arguments for the metadata!", 1);
            }

            int hz = 0;
            try {
                hz = Integer.parseInt(metaData[0]);
                if (hz != 0 && hz != 20 && hz != 30 && hz != 60 && hz != 80 && hz != 120 && hz != 144) {
                    throw new ParseException("Error on line %s: The refresh rate of your slideshow can only be a value of either 0 (monitor refresh rate), 20, 30, 60, 80, 120 or 144!", 1);
                }
                if (hz == 0) {
                    final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    final GraphicsDevice dev      = env.getDefaultScreenDevice();
                    final int monitorHz           = dev.getDisplayMode().getRefreshRate();
                    if (monitorHz == DisplayMode.REFRESH_RATE_UNKNOWN) {
                        Main.logger.log(Level.WARNING, "Monitor refresh rate is not known");
                        hz = 30; // @NOTE I think this is a reasonable default
                    } else {
                        hz = monitorHz;
                    }
                }
            } catch (final NumberFormatException ex) {
                throw new ParseException("Error on line %s: First meta item must be the refresh rate of your slideshow!", 1);
            }

            final String aspectRatio = metaData[1];
            if (aspectRatio.equals("FILL")) {
                Main.logger.log(Level.INFO, String.format("Parsed aspect ratio: %s", "FILL"));
                return new SlideShowMetaData(hz, -1);
            }

            final String[] xy = aspectRatio.split(":");
            if (xy.length != 2) {
                throw new ParseException("Error on line %s: Invalid aspect ration format! Must be x:y or 'FILL'!", 1);
            }
            final float x = parseInteger(xy[0], 1);
            final float y = parseInteger(xy[1], 1);
            Main.logger.log(Level.INFO, String.format("Parsed aspect ratio: %s", x / y));

            return new SlideShowMetaData(hz, x / y);
        }

        throw new ParseException("Error on line %s: Your first line must be the metadata!", 1);
    }

    private final class Cursor {

        public volatile int val = -1;

        private final int limit;

        public Cursor(final int limit) {
            this.limit = limit;
        }

        public void unwind() {
            if (val > 0) {
                val -= 1;
            }
        }

        public boolean advance() {
            if (val >= limit) {
                return false;
            }
            val += 1;
            return true;
        }
    }

    public Slide[] parseSlides() throws ParseException {
        final StringBuilder fileContent = new StringBuilder();
        final boolean success = readFileIntoMemory(fileContent);
        if (!success) {
            throw new ParseException("Failed to read '%s'\n", file.getAbsolutePath());
        }
        final ArrayList<Slide> slideshow = new ArrayList<>();

        final String[] lines = fileContent.toString().split("\n");
        final Cursor cursor = new Cursor(lines.length - 1);
        while (cursor.advance()) {
            final String line = lines[cursor.val].strip();

            if (isHeader(line)) {
                continue;
            }
            if (isComment(line)) {
                continue;
            }
            if (isEmptyLine(line)) {
                continue;
            }

            final String slideName = requireSlideDecl(line, cursor);
            final Slide slide = parseSlideDecl(slideName, lines, cursor);
            slideshow.add(slide);
        }

        return slideshow.toArray(Slide[]::new);
    }

    private Slide parseSlideDecl(final String slideName, final String[] lines, final Cursor cursor) throws ParseException {
        assert slideName != null;
        assert lines     != null;
        assert cursor    != null;

        Color slideColor = Color.BLACK;
        Slide.Audio audio = null; // @NOTE null means play NO audio (which is fine)
        final ArrayList<Slide.Element> elements = new ArrayList<>();

        while (cursor.advance()) {
            final String line = lines[cursor.val].strip();
            if (isComment(line)) {
                continue;
            }
            if (isEmptyLine(line)) {
                continue;
            }

            if (line.startsWith("[")) { // @NOTE probably another slide decl
                cursor.unwind();
                return new Slide(slideName, slideColor, audio, elements.toArray(Slide.Element[]::new));  // @NOTE break to main loop
            }

            if (isConfig(line)) {
                final String key = line.split("=")[0];
                final String val = line.split("=")[1];
                switch (key.toUpperCase()) {
                    case "COLOR": {
                        slideColor = parseArgb(val, cursor);
                    } break;

                    case "AUDIO": {
                        audio = new Slide.Audio();

                        final String[] args = val.split(";");
                        if (args.length != 3) {
                            throw new ParseException("Error on line %s: Too few/many arguments for audio configuration!", cursor.val + 1);
                        }

                        final File file = new File(args[0]);
                        if (file.exists() && !file.isDirectory()) {
                            audio.file = args[0];
                        } else {
                            throw new ParseException("Error on line %s: Audio file does not exist!", cursor.val + 1);
                        }

                        audio.decibel = parseInteger(args[1], cursor);
                        audio.loop    = parseBoolean(args[2], cursor);
                    } break;

                    default: {
                        throw new ParseException("Error on line %s: Unknown slide configuration name!", cursor.val + 1);
                    }
                }
                continue;
            }

            final String type = requireSlideElement(line, cursor);
            final Slide.Element element = parseSlideElement(lines, type, cursor);
            elements.add(element);
        }

        // @NOTE EOF
        return new Slide(slideName, slideColor, audio, elements.toArray(Slide.Element[]::new));
    }

    private Color parseArgb(final String str, final Cursor cursor) throws ParseException {
        assert str    != null;
        assert cursor != null;

        final String[] args = str.split(";");
        if (args.length != 4) {
            throw new ParseException("Error on line %s: Too few/many arguments for a color definition!\nA valid color definition would be for example:\n100;120;150;255", cursor.val + 1);
        }

        try {
            final int r = Integer.parseInt(args[0]);
            final int g = Integer.parseInt(args[1]);
            final int b = Integer.parseInt(args[2]);
            final int a = Integer.parseInt(args[3]);

            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255 || a < 0 || a > 255) {
                throw new ParseException("Error on line %s: ARGB values out of range! Must be in between 0 - 255.", cursor.val + 1);
            }
            return new Color(r, g, b, a);
        } catch (final NumberFormatException ex) {
            throw new ParseException("Error on line %s: Color values must be numeric decimal values.", cursor.val + 1);
        }
    }

    private Slide.Element parseSlideElement(final String[] lines, final String type, final Cursor cursor) throws ParseException {
        assert lines  != null;
        assert type   != null;
        assert cursor != null;

        switch (type) {
            case "TEXT": {
                return parseTextElement(lines, cursor);
            }

            case "RECT": {
                return parseFormElement(lines, cursor, Slide.Form.Type.RECT);
            }

            case "OVAL": {
                return parseFormElement(lines, cursor, Slide.Form.Type.OVAL);
            }

            case "IMAGE": {
                return parseImageElement(lines, cursor);
            }

            default: {
                assert false : "Can not happen since requireSlideElement() already checked that!";
                return null;
            }
        }
    }

    private Slide.Form parseFormElement(final String[] lines, final Cursor cursor, final Slide.Form.Type type) throws ParseException { assert lines  != null;
        assert lines  != null;
        assert cursor != null;
        assert type   != null;

        // @NOTE default values
        Color color = Color.BLACK;
        float x    = 0.5f;
        float y    = 0.5f;
        float w    = 0.5f;
        float h    = 0.5f;
        int rot    = 0;
        float borderSize   = 0;
        Color borderColor  = Color.BLACK;

        while (cursor.advance()) {
            final String line = lines[cursor.val].strip();
            if (isComment(line)) {
                continue;
            }
            if (isEmptyLine(line)) {
                continue;
            }

            if (!isConfig(line)) { // @NOTE probably another slide decl or element
                cursor.unwind();
                return new Slide.Form(type, color, x, y, w, h, rot, borderSize, borderColor);
            }

            if (isConfig(line)) {
                final String key   = line.split("=")[0];
                final String value = line.split("=")[1];
                switch (key.toUpperCase()) {
                    case "COLOR": {
                        color = parseArgb(value, cursor);
                    } break;

                    case "X": {
                        x = parseFloat(value, cursor);
                    } break;

                    case "Y": {
                        y = parseFloat(value, cursor);
                    } break;

                    case "W": {
                        w = parseFloat(value, cursor);
                    } break;

                    case "H": {
                        h = parseFloat(value, cursor);
                    } break;

                    case "ROTATION": {
                        rot = parseInteger(value, cursor);
                    } break;

                    case "BORDERSIZE": {
                        borderSize = parseFloat(value, cursor);
                    } break;

                    case "BORDERCOLOR": {
                        borderColor = parseArgb(value, cursor);
                    } break;

                    default: {
                        throw new ParseException("Error on line %s: Unknown configuration name for an rect element!", cursor.val + 1);
                    }
                }
            }
        }

        // @NOTE EOF
        return new Slide.Form(type, color, x, y, w, h, rot, borderSize, borderColor);
    }

    private Slide.Image parseImageElement(final String[] lines, final Cursor cursor) throws ParseException {
        assert lines  != null;
        assert cursor != null;

        // @NOTE default values
        Image image = null;
        float x    = 0.5f;
        float y    = 0.5f;
        float w    = 0.5f;
        float h    = 0.5f;
        float alpha = 1;
        int rot    = 0;
        float borderSize   = 0;
        Color borderColor  = Color.BLACK;

        while (cursor.advance()) {
            final String line = lines[cursor.val].strip();
            if (isComment(line)) {
                continue;
            }
            if (isEmptyLine(line)) {
                continue;
            }

            if (!isConfig(line)) { // @NOTE probably another slide decl or element
                cursor.unwind();
                return new Slide.Image(image, x, y, w, h, alpha, rot, borderSize, borderColor);
            }

            if (isConfig(line)) {
                final String key   = line.split("=")[0];
                final String value = line.split("=")[1];
                switch (key.toUpperCase()) {
                    case "FILE": {
                        image = parseImageFile(value, cursor);
                    } break;

                    case "X": {
                        x = parseFloat(value, cursor);
                    } break;

                    case "Y": {
                        y = parseFloat(value, cursor);
                    } break;

                    case "W": {
                        w = parseFloat(value, cursor);
                    } break;

                    case "H": {
                        h = parseFloat(value, cursor);
                    } break;

                    case "ALPHA": {
                        alpha = parseFloat(value, cursor);
                    } break;

                    case "ROTATION": {
                        rot = parseInteger(value, cursor);
                    } break;

                    case "BORDERSIZE": {
                        borderSize = parseFloat(value, cursor);
                    } break;

                    case "BORDERCOLOR": {
                        borderColor = parseArgb(value, cursor);
                    } break;

                    default: {
                        throw new ParseException("Error on line %s: Unknown configuration name for an image element!", cursor.val + 1);
                    }
                }
            }
        }

        return new Slide.Image(image, x, y, w, h, alpha, rot, borderSize, borderColor);
    }

    private Slide.Text parseTextElement(final String[] lines, final Cursor cursor) throws ParseException {
        assert lines  != null;
        assert cursor != null;

        // @NOTE default values
        final ArrayList<String> strings = new ArrayList<>();
        Color color = Color.BLACK;
        float x    = 0.5f;
        float y    = 0.5f;
        float size = 0.5f;
        String font = "Serfi";
        int style = Font.PLAIN;
        boolean underline     = false;
        boolean strikethrough = false;
        boolean reversed      = false;

        while (cursor.advance()) {
            final String line = lines[cursor.val].strip();
            if (isComment(line)) {
                continue;
            }
            if (isEmptyLine(line)) {
                continue;
            }

            if (!isConfig(line)) { // @NOTE probably another slide decl or element
                cursor.unwind();
                if (strings.size() == 0) {
                    strings.add("LINE=?????");
                }
                return new Slide.Text(strings.toArray(String[]::new), color, font, style, underline, strikethrough, reversed, x, y, size);
            }

            if (isConfig(line)) {
                final String key   = line.split("=")[0];
                final String value = line.split("=")[1];
                switch (key.toUpperCase()) {
                    case "LINE": {
                        strings.add(value);
                    } break;

                    case "COLOR": {
                        color = parseArgb(value, cursor);
                    } break;

                    case "X": {
                        x = parseFloat(value, cursor);
                    } break;

                    case "Y": {
                        y = parseFloat(value, cursor);
                    } break;

                    case "SIZE": {
                        size = parseFloat(value, cursor);
                    } break;

                    case "FONT": {
                        font = checkFontInstalled(value, cursor);
                    } break;

                    case "STYLE": {
                        // @TODO: In Java 17 we can use yield!!
                        switch (value) {
                            case "PLAIN": {
                                style = Font.PLAIN;
                            } break;

                            case "BOLD": {
                                style = Font.BOLD;
                            } break;

                            case "ITALIC": {
                                style = Font.ITALIC;
                            } break;

                            case "BOLDITALIC": {
                                style = Font.BOLD | Font.ITALIC;
                            } break;

                            default: {
                                throw new ParseException("Error on line %s: Unknown font style! Can only be PLAIN, BOLD, ITALIC or BOLDITALIC!", cursor.val + 1);
                            }
                        }
                    } break;

                    case "UNDERLINE": {
                        underline = parseBoolean(value, cursor);
                    } break;

                    case "STRIKETHROUGH": {
                        strikethrough = parseBoolean(value, cursor);
                    } break;

                    case "REVERSED": {
                        reversed = parseBoolean(value, cursor);
                    } break;

                    default: {
                        throw new ParseException("Error on line %s: Unknown configuration name for a text element!", cursor.val + 1);
                    }
                }
            }
        }
        // @NOTE EOF
        if (strings.size() == 0) {
            strings.add("LINE=?????");
        }
        return new Slide.Text(strings.toArray(String[]::new), color, font, style, underline, strikethrough, reversed, x, y, size);
    }

    private String requireSlideElement(final String line, final Cursor cursor) throws ParseException {
        assert line   != null;
        assert cursor != null;

        final int startE = line.indexOf("{");
        final int endE   = line.indexOf("}");
        if (startE == -1 && endE == -1) {
            throw new ParseException("Error on line %s: Invalid slide element declaration!", cursor.val + 1);
        }
        if (countChar(line, '{') > 1) {
            throw new ParseException("Error on line %s: Slide element definition has to many open tokens!", cursor.val + 1);
        }
        if (countChar(line, '}') > 1) {
            throw new ParseException("Error on line %s: Slide element definition has to many close tokens!", cursor.val + 1);
        }
        if (startE == -1 && endE != -1) {
            throw new ParseException("Error on line %s: Slide element has been closed but not opened!", cursor.val + 1);
        }
        if (startE != -1 && endE == -1) {
            throw new ParseException("Error on line %s: Slide element has been opened but not closed!", cursor.val + 1);
        }

        final String eName = line.substring(startE + 1, endE);
        if (eName.isEmpty()) {
            throw new ParseException("Error on line %s: A slide element must have a correct name indicating the type!", cursor.val + 1);
        }

        switch (eName.toUpperCase()) {
            case "TEXT": {
                return "TEXT";
            }

            case "RECT": {
                return "RECT";
            }

            case "OVAL": {
                return "OVAL";
            }

            case "IMAGE": {
                return "IMAGE";
            }

            default: {
                throw new ParseException("Error on line %s: Unknown slide element!", cursor.val + 1);
            }
        }
    }

    private boolean parseBoolean(final String s, final Cursor cursor) throws ParseException {
        assert s      != null;
        assert cursor != null;

        final boolean result = s.equals("TRUE") || s.equals("FALSE");
        if (result) {
            return Boolean.parseBoolean(s);
        } else {
            throw new ParseException("Error on line %s: Invalid boolean!", cursor.val + 1);
        }
    }

    private Image parseImageFile(final String s, final Cursor cursor) throws ParseException {
        assert s      != null;
        assert cursor != null;

        // @NOTE
        // We use ImageIcon instead of ImageIO since it supports .gif files.
        // It is also waaaayyyy faster to load!
        // On the downside it does really give us a good way to check for errors...
        final File file = new File(s);
        if (!file.exists() || file.isDirectory()) {
            throw new ParseException("Error on line %s: The path '%s' does not point to a file which can be read as an image.", cursor.val + 1, s);
        }
        return new javax.swing.ImageIcon(s).getImage(); // @NOTE that this does not block since it creates a background thread!
    }

    private boolean isConfig(final String line) {
        assert line != null;

        return line.split("=").length == 2;
    }

    private boolean isEmptyLine(final String line) {
        assert line != null;

        return line.isEmpty();
    }

    private String requireSlideDecl(final String line, final Cursor cursor) throws ParseException {
        assert line   != null;
        assert cursor != null;

        final int startSec = line.indexOf("[");
        final int endSec   = line.indexOf("]");
        if (startSec == -1 && endSec == -1) {
            throw new ParseException("Error on line %s: You need to define a section first!", cursor.val + 1);
        }
        if (countChar(line, '[') > 1) {
            throw new ParseException("Error on line %s: Section definition has to many open tokens!", cursor.val + 1);
        }
        if (countChar(line, ']') > 1) {
            throw new ParseException("Error on line %s: Section definition has to many close tokens!", cursor.val + 1);
        }
        if (startSec == -1 && endSec != -1) {
            throw new ParseException("Error on line %s: Section has been closed but not opened!", cursor.val + 1);
        }
        if (startSec != -1 && endSec == -1) {
            throw new ParseException("Error on line %s: Section has been opened but not closed!", cursor.val + 1);
        }
        final String secName = line.substring(startSec + 1, endSec);
        if (secName.isEmpty()) {
            throw new ParseException("Error on line %s: Empty section name is not allowed!", cursor.val + 1);
        }
        return secName;
    }

    private int countChar(final String source, final char target) {
        assert source != null;

        final char[] chars = source.toCharArray();
        int sum = 0;
        for (char c : chars) {
            if (c == target) {
                sum += 1;
            }
        }
        return sum;
    }

    private boolean isHeader(final String line) {
        assert line != null;

        return line.startsWith("(") && line.endsWith(")");
    }

    private boolean isComment(final String line) {
        assert line != null;

        return line.startsWith("#");
    }

    private int parseInteger(final String s, final Cursor cursor) throws ParseException {
        assert s      != null;
        assert cursor != null;

        return parseInteger(s, cursor.val + 1);
    }

    private int parseInteger(final String s, final int line) throws ParseException {
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException ex) {
            throw new ParseException("Error on line %s: Invalid integer!", line);
        }
    }

    private float parseFloat(final String s, final Cursor cursor) throws ParseException {
        assert s      != null;
        assert cursor != null;

        int dots = 0;
        for (final char c : s.toCharArray()) {
            if (c == '.') {
                dots++;
            }
        }

        if (dots == 0 || dots > 1) {
            throw new ParseException("Error on line %s: Failed to parse float value!", cursor.val + 1);
        }

        try {
            final float result = Float.parseFloat(s);
            if (result > 1.0 || result < 0.0) {
                throw new ParseException("Error on line %s: Float value must be between 0-1!", cursor.val + 1);
            }
            return result;
        } catch (final NumberFormatException ex) {
            throw new ParseException("Error on line %s: Failed to parse float value!", cursor.val + 1);
        }
    }

    private String checkFontInstalled(final String fontName, final Cursor cursor) throws ParseException {
        assert fontName != null;
        assert cursor   != null;

        final GraphicsEnvironment gfxEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final String[] fonts = gfxEnv.getAvailableFontFamilyNames();
        for (final String font : fonts) {
            if (font.equals(fontName)) {
                return fontName;
            }
        }
        throw new ParseException("Error on line %s: You do not have the font '%s' installed on your system!", cursor.val + 1, fontName);
    }

    private boolean readFileIntoMemory(final StringBuilder buffer) {
        try (final FileInputStream in = new FileInputStream(file)) {
            while (true) {
                final byte[] buf = new byte[4096]; // @NOTE likely a page
                final int readBytes = in.read(buf);
                if (readBytes < 0) {
                    break;
                }
                buffer.append(new String(buf, 0, readBytes, StandardCharsets.UTF_8));
            }
            return true;
        } catch (final Exception ex) {
            return false;
        }
    }
}
