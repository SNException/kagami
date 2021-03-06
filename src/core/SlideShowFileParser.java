import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
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

    public final record SlideShowMetaDataRec(int hz, float aspectRatio) {}

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


    public SlideShowMetaDataRec parseMetaData() throws ParseException {
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
                return new SlideShowMetaDataRec(hz, -1);
            }

            final String[] xy = aspectRatio.split(":");
            if (xy.length != 2) {
                throw new ParseException("Error on line %s: Invalid aspect ration format! Must be x:y or 'FILL'!", 1);
            }
            final float x = parseInteger(xy[0], 1);
            final float y = parseInteger(xy[1], 1);
            Main.logger.log(Level.INFO, String.format("Parsed aspect ratio: %s", x / y));

            return new SlideShowMetaDataRec(hz, x / y);
        }

        throw new ParseException("Error on line %s: Your first line must be the metadata!", 1);
    }

    public Slide[] parseSlides() throws ParseException {
        final StringBuilder fileContent = new StringBuilder();
        final boolean success = readFileIntoMemory(fileContent);
        if (!success) {
            throw new ParseException("Failed to read '%s'\n", file.getAbsolutePath());
        }
        final ArrayList<Slide> slideshow   = new ArrayList<>();
        final ArrayList<String> slideNames = new ArrayList<>();

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
            slideNames.add(slideName);
            final Slide slide = parseSlideDecl(slideName, lines, cursor);
            slideshow.add(slide);
        }

        errorOnDuplicateSlideNames(slideNames);

        return slideshow.toArray(Slide[]::new);
    }

    private void errorOnDuplicateSlideNames(final ArrayList<String> slideNames) throws ParseException {
        final HashSet<String> set = new HashSet<>(slideNames);
        if (set.size() < slideNames.size()) {
            throw new ParseException("You have duplicate slide names in your slideshow which is illegal!");
        }
    }

    private Slide parseSlideDecl(final String slideName, final String[] lines, final Cursor cursor) throws ParseException {
        assert slideName != null;
        assert lines     != null;
        assert cursor    != null;

        final Slide.Argb argb = new Slide.Argb();
        Slide.AudioRec audio = null; // @NOTE null means play NO audio (which is fine)
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
                return new Slide(slideName, argb, audio, elements.toArray(Slide.Element[]::new));  // @NOTE break to main loop
            }

            if (isConfig(line)) {
                final String key = line.split("=")[0];
                final String val = line.split("=")[1];
                switch (key.toUpperCase()) {
                    case "COLOR": {
                        parsePossibleGradient(argb, val, cursor);
                    } break;

                    case "AUDIO": {
                        final String[] args = val.split(";");
                        if (args.length != 4) {
                            throw new ParseException("Error on line %s: Too few/many arguments for audio configuration!", cursor.val + 1);
                        }

                        final File file = new File(args[0]);
                        if (file.exists() && !file.isDirectory()) {
                            // @NOTE do nothing, we are good
                        } else {
                            throw new ParseException("Error on line %s: Audio file does not exist!", cursor.val + 1);
                        }

                        final String sfile   = args[0];
                        final float decibel = parseInteger(args[1], cursor);
                        final boolean loop  = parseBoolean(args[2], cursor);
                        final boolean carry = parseBoolean(args[3], cursor);

                        audio = new Slide.AudioRec(sfile, decibel, loop, carry);
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
        return new Slide(slideName, argb, audio, elements.toArray(Slide.Element[]::new));  // @NOTE break to main loop
    }

    private void parsePossibleGradient(final Slide.Argb argb, final String val, final Cursor cursor) throws ParseException {
        assert argb   != null;
        assert val    != null;
        assert cursor != null;

        final String[] gradient = val.split(";"); // @NOTE: Gradient
        if (gradient.length == 1) {
            argb.color1 = parseArgb(val, cursor);
        } else if (gradient.length == 7) {
            argb.color1 = parseArgb(gradient[0], cursor);
            argb.color2 = parseArgb(gradient[1], cursor);

            argb.x1 = parseFloat(gradient[2], cursor);
            argb.y1 = parseFloat(gradient[3], cursor);
            argb.x2 = parseFloat(gradient[4], cursor);
            argb.y2 = parseFloat(gradient[5], cursor);
            argb.cyclic = parseBoolean(gradient[6], cursor);
        } else {
            throw new ParseException("Error on line %s: Invalid amount of arguments for gradient color specification!", cursor.val + 1);
        }
    }

    private Color parseArgb(final String str, final Cursor cursor) throws ParseException {
        assert str    != null;
        assert cursor != null;

        if (str.length() < 8) {
            throw new ParseException("Error on line %s: Invalid color value!", cursor.val + 1);
        }

        //
        // @TODO: What about if we pass a hex value which is longer than 8?
        // We have to check whether the last char is a semicolon.
        //

        final char[] chars = str.toCharArray();

        final String xr = chars[0] + "" + chars[1];
        final String xg = chars[2] + "" + chars[3];
        final String xb = chars[4] + "" + chars[5];
        final String xa = chars[6] + "" + chars[7];

        try {

            final int r = Integer.parseInt(xr, 16);
            final int g = Integer.parseInt(xg, 16);
            final int b = Integer.parseInt(xb, 16);
            final int a = Integer.parseInt(xa, 16);

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
        final Slide.Argb color = new Slide.Argb();
        float x    = 0.5f;
        float y    = 0.5f;
        float w    = 0.5f;
        float h    = 0.5f;
        int rot    = 0;
        float borderSize   = 0;
        final Slide.Argb borderColor = new Slide.Argb();

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
                        parsePossibleGradient(color, value, cursor);
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
                        parsePossibleGradient(borderColor, value, cursor);
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
        final Slide.Argb borderColor = new Slide.Argb();

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
                        parsePossibleGradient(borderColor, value, cursor);
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
        final Slide.Argb argb = new Slide.Argb();
        float x    = 0.5f;
        float y    = 0.5f;
        float size = 0.5f;
        int rot    = 0;
        String font = "Serfi"; // @NOTE bundles with the JDK so it is always available
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
                return new Slide.Text(strings.toArray(String[]::new), argb, font, style, underline, strikethrough, reversed, x, y, size, rot);
            }

            if (isConfig(line)) {
                final String key   = line.split("=")[0];
                final String value = line.split("=")[1];
                switch (key.toUpperCase()) {
                    case "LINE": {
                        strings.add(value);
                    } break;

                    case "COLOR": {
                        parsePossibleGradient(argb, value, cursor);
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

                    case "ROTATION": {
                        rot = parseInteger(value, cursor);
                    } break;

                    case "FONT": {
                        font = checkFontInstalled(value, cursor);
                    } break;

                    case "STYLE": {
                        // @NOTE :no_arrow_switch_case:
                        style = switch (value) {
                            case "PLAIN"      : yield Font.PLAIN;
                            case "BOLD"       : yield Font.BOLD;
                            case "ITALIC"     : yield Font.ITALIC;
                            case "BOLDITALIC" : yield Font.ITALIC;
                            default           : throw new ParseException("Error on line %s: Unknown font style! Can only be PLAIN, BOLD, ITALIC or BOLDITALIC!", cursor.val + 1);
                        };
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
        return new Slide.Text(strings.toArray(String[]::new), argb, font, style, underline, strikethrough, reversed, x, y, size, rot);
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
        final FResult<FileInputStream> handleResult = SFile.openFileForReading(file.getAbsolutePath());
        if (handleResult.failed) {
            Main.logger.log(Level.SEVERE, handleResult.error.getMessage(), handleResult.error);
            return false;
        }

        final FResult<byte[]> readResult = SFile.read(handleResult.data);
        if (readResult.failed) {
            Main.logger.log(Level.SEVERE, handleResult.error.getMessage(), handleResult.error);
            return false;
        }
        SFile.close(handleResult.data);
        buffer.append(new String(readResult.data));
        return true;
    }
}
