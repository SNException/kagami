import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Panel;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import javax.swing.SwingUtilities;

public final class Display {

    private MainLoop mainLoop;
    private InputHandler inputHandler;
    private Frame frame;
    private Panel mainPanel;
    private Canvas canvas;
    private BufferStrategy backBuffers;
    private HashMap<RenderingHints.Key, Object> renderingHints;

    private final String title;

    private volatile Slide[] slideshow; // @NOTE can be changed from outside thread, hence it MUST be stored in main memory at all times
    private volatile int slideIndex = 0;

    private MousePointer mousePointer;
    private boolean isMousePointerActive = false;
    private float mousePointerSize = 16;

    private boolean doResize    = false;
    private boolean firstResize = true; // @NOTE used to indicate that we are resizing for the first time of current slideshow.

    private float currentAspectRatio = 0;
    private float targetAspectRatio = 0;
    private boolean fill = false;

    private enum DebugLevel {
        NONE,
        MINIMAL,
        EXTENDED
    }
    private DebugLevel debugLevel = Main.isDebugMode() ? DebugLevel.EXTENDED : DebugLevel.NONE;

    // @NOTE these can be set by the hotloader thread that is why the must be stored in main memory
    private volatile boolean msg      = false;
    private volatile String message   = "";

    public Display(final String title) {
        assert EventQueue.isDispatchThread();
        assert title != null;

        this.title = title;
        this.slideshow = new Slide[] { new Slide("DEFAULT", Color.BLACK, null) };
    }

    public void initAndShow(final int hz, final float targetAspectRatio) {
        this.targetAspectRatio = targetAspectRatio;
        if (targetAspectRatio == -1) {
            fill = true;
        }

        ui: {
            canvas = new Canvas();
            canvas.setFocusable(true);
            canvas.setIgnoreRepaint(true);
            inputHandler = new InputHandler();
            canvas.addKeyListener(inputHandler);
            final CustomMouseAdapter mouseHandler = new CustomMouseAdapter();
            canvas.addMouseWheelListener(mouseHandler);
            canvas.addMouseListener(mouseHandler);

            mainPanel = new Panel();
            mainPanel.setBackground(Color.BLACK);
            mainPanel.setLayout(null); // @NOTE we will do the layout handling ourselves!
            mainPanel.add(canvas);

            frame = new Frame(String.format("Kagami %s - %s", Main.VERSION, title));
            frame.setIconImage(new javax.swing.ImageIcon("res/icon.png").getImage());
            frame.setLayout(new BorderLayout());
            frame.add(mainPanel, BorderLayout.CENTER);
            frame.setBackground(Color.BLACK);
            mainPanel.addMouseWheelListener(mouseHandler);
            mainPanel.addMouseListener(mouseHandler);
            frame.addWindowListener(new CustomWindowAdapter());
            frame.addComponentListener(new CustomComponentAdapter());
            final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(screenSize.width / 2, screenSize.height / 2);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            mousePointer = new MousePointer();

            canvas.requestFocus();
            createGraphics();
        }

        rendering_hints: {
            renderingHints = new HashMap<>();
            renderingHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            renderingHints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            renderingHints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            renderingHints.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
            renderingHints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            renderingHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            renderingHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            renderingHints.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            renderingHints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        main_loop: {
            mainLoop = new MainLoop(hz);
            final Thread thread = new Thread(mainLoop);
            thread.setName("main_loop_thread");
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private final class CustomWindowAdapter extends WindowAdapter {

        @Override
        public void windowClosing(final WindowEvent evt) {
            System.exit(0);
        }
    }

    private final class CustomComponentAdapter extends ComponentAdapter {

        @Override
        public void componentResized(final ComponentEvent evt) {
            doResize = true;
        }
    }

    private final class CustomMouseAdapter extends MouseAdapter {

        @Override
        public void mousePressed(final MouseEvent evt) {
            if (SwingUtilities.isLeftMouseButton(evt)) {
                nextSlide();
                return;
            }

            if (SwingUtilities.isRightMouseButton(evt)) {
                prevSlide();
                return;
            }
        }

        @Override
        public void mouseWheelMoved(final MouseWheelEvent evt) {
            final float inc = 4; // @TODO: Should be based on canvas size
            if (evt.getWheelRotation() < 0) { // @NOTE up
                if (mousePointerSize < inc * 50) { // @TODO: Should be based on canvas size
                    mousePointerSize += inc;
                }
            } else { // @NOTE down
                if (mousePointerSize > inc) {
                    mousePointerSize -= inc;
                }
            }
            // @NOTE in case we will ever have other components who need to do something (e.g scrollpanes)
            // canvas.getParent().dispatchEvent(evt);
        }
    }

    private void createGraphics() {
        final GraphicsEnvironment gfxEnv      = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice gfxDevice        = gfxEnv.getDefaultScreenDevice();
        final GraphicsConfiguration gfxConfig = gfxDevice.getDefaultConfiguration();

        if (gfxConfig.getBufferCapabilities().isMultiBufferAvailable()) {
            canvas.createBufferStrategy(3);
            Main.logger.log(Level.INFO, "3 backbuffers created");
        } else {
            canvas.createBufferStrategy(2);
            Main.logger.log(Level.INFO, "2 backbuffers created");
        }

        Main.logger.log(Level.INFO, "Page flipping: " + gfxConfig.getBufferCapabilities().isPageFlipping());

        // @NOTE deallocate previous one. This means this function has been called again after init.
        // For eaxmplae when entering presentation mode.
        if (backBuffers != null) {
            backBuffers.dispose();
        }
        backBuffers = canvas.getBufferStrategy();
    }

    public void destroyAllSlides() {
        for (final Slide slide : this.slideshow) {
            slide.destroy();
        }
    }

    public void newSlideShow(final Slide[] slideshow) {
        assert slideshow != null;

        destroyAllSlides();

        // @TODO
        //
        // Hmm, why does that work? I feel like I should get problems some times where I index a slide inside the render()
        // function which is not available anymore since this call can happens during accessing the slide.
        //
        this.slideshow = slideshow;

        firstResize = true;
        doResize = true;
    }

    public void showMessage(final String message) {
        assert message != null;

        msg = true;
        this.message = message;
    }

    public void clearMessage() {
        msg = false;
    }

    private void nextSlide() {
        if (slideIndex < slideshow.length - 1) {
            slideshow[slideIndex].onExit();
            slideIndex += 1;
            slideshow[slideIndex].onEnter();
        }
    }

    private void prevSlide() {
        if (slideIndex > 0) {
            slideshow[slideIndex].onExit();
            slideIndex -= 1;
            slideshow[slideIndex].onEnter();
        }
    }

    // @TODO: Make this event based so that input does not suffer when framerate is low!
    private void input() {
        if (inputHandler.isKeyDown(KeyEvent.VK_RIGHT) || inputHandler.isKeyDown(KeyEvent.VK_SPACE) || inputHandler.isKeyDown(KeyEvent.VK_ENTER)) {
            nextSlide();
        } else if (inputHandler.isKeyDown(KeyEvent.VK_LEFT) || inputHandler.isKeyDown(KeyEvent.VK_BACK_SPACE)) {
            prevSlide();
        } else if (inputHandler.isKeyDown(KeyEvent.VK_PAGE_UP)) {
            slideIndex = slideshow.length - 1;
        } else if (inputHandler.isKeyDown(KeyEvent.VK_PAGE_DOWN)) {
            slideIndex = 0;
        } else if (inputHandler.isKeyDown(KeyEvent.VK_F12)) {
            // @TODO In java 17 we can use yield
            switch (debugLevel) {
                case NONE     : debugLevel = DebugLevel.MINIMAL;  break;
                case MINIMAL  : debugLevel = DebugLevel.EXTENDED; break;
                case EXTENDED : debugLevel = DebugLevel.NONE;     break;
            }
        } else if (inputHandler.isKeyDown(KeyEvent.VK_M)) {
            isMousePointerActive = !isMousePointerActive;
            if (!frame.isUndecorated()) { // @NOTE when we are in 'presentation mode' the cursor is already invisible
                if (isMousePointerActive) {
                    frame.setCursor(frame.getToolkit().createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(), null));
                } else {
                    frame.setCursor(Cursor.getDefaultCursor());
                }
            }
        } else if (inputHandler.isKeyDown(KeyEvent.VK_P)) {
            // @NOTE presentation mode
            isMousePointerActive = false;

            final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (frame.isUndecorated()) {
                // @NOTE windowed
                frame.dispose();
                frame.setUndecorated(false);
                mainPanel.setSize(screenSize.width / 2, screenSize.height / 2); // @TODO: Clamp according to screen resolution
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setCursor(Cursor.getDefaultCursor());
                frame.setVisible(true);

                // @NOTE recreate backbuffers since we deallocated the frame (peer)
                createGraphics();
            } else {
                // @NOTE fullscreen
                frame.dispose();
                frame.setUndecorated(true);
                frame.setSize(screenSize.width, screenSize.height);
                frame.setLocationRelativeTo(null);
                frame.setCursor(frame.getToolkit().createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(), null));
                frame.setVisible(true);

                // @NOTE recreate backbuffers since we deallocated the frame (peer)
                createGraphics();
            }
        }

        inputHandler.update(); // @NOTE must be the last call inside this function!
    }

    private void update() {
        if (isMousePointerActive) {
            mousePointer.update(canvas.getMousePosition(), mousePointerSize, mousePointerSize);
        }

        slideshow[slideIndex].update();
    }

    private void render() {
        do {

            // @NOTE render to offscreen buffer
            do {
                final Graphics2D g = (Graphics2D) backBuffers.getDrawGraphics();
                g.setRenderingHints(renderingHints);

                // @NOTE clear
                g.setColor(new Color(0, 0, 0));
                g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                // @NOTE check if we need to resize our slides
                if (doResize) {
                    Main.logger.log(Level.INFO, "Resized window");

                    if (fill) {
                        canvas.setBounds(0, 0, mainPanel.getWidth(), mainPanel.getHeight());
                    } else {
                        // @TODO: I think there are still issues when an aspect ratio where y (h) is greater than x (w) is passed
                        aspect_ratio: {
                            float w = mainPanel.getWidth();
                            float h = mainPanel.getHeight();
                            while (true) {
                                currentAspectRatio = w / h;

                                final float threshold = 0.00001f; // @NOTE Since we are dealing with float values we can not check for exact (==) values.
                                if (currentAspectRatio < (targetAspectRatio - threshold)) {  // @NOTE height is now bigger than width hence we have to subtract height as long as it takes to resolve thatspectRatio - threshold) {
                                     h -= 1;
                                     continue;
                                }

                                if (w <= h || (currentAspectRatio >= (targetAspectRatio - threshold) && currentAspectRatio <= (targetAspectRatio + threshold))) { // @NOTE threshold (4/3)
                                    Main.logger.log(Level.INFO, String.format("Current aspect ratio is %s", currentAspectRatio));
                                    float xScale = mainPanel.getWidth()  / w;
                                    float yScale = mainPanel.getHeight() / h;
                                    if (xScale < 1) xScale = 1;
                                    if (yScale < 1) yScale = 1;
                                    if (xScale > yScale) xScale = yScale;
                                    if (yScale > xScale) yScale = xScale;

                                    final float xCenter = (mainPanel.getWidth()  - (w * xScale)) / 2;
                                    final float yCenter = (mainPanel.getHeight() - (h * yScale)) / 2;
                                    canvas.setBounds((int) xCenter, (int) yCenter, (int) w, (int) h);
                                    break;
                                }

                                w  -= 1;
                            }
                        }
                    }

                    for (final Slide slide : slideshow) {
                        slide.onResize(g, canvas.getWidth(), canvas.getHeight());
                    }
                    doResize = false;

                    if (firstResize) {
                        slideshow[slideIndex].onEnter();
                        firstResize = false;
                    }
                }

                if (!msg) {
                    slideshow[slideIndex].render(g);
                } else {
                    g.setColor(new Color(50, 0, 0));
                    g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                    // @TODO: Heavily copy pasted from Slide.Text
                    final float sizePercentage = 0.9f;
                    final float targetWidthPx = canvas.getWidth() * (sizePercentage * 100.0f) / 100.0f;
                    float fontSize = 0.0f;
                    final Font font = new Font("Consolas", Font.BOLD, (int) fontSize);
                    while (true) {
                        final FontMetrics metrics = g.getFontMetrics(font.deriveFont(fontSize));
                        float currentWidthPx = metrics.stringWidth(message);
                        if (message.split("\n").length == 0) {
                            currentWidthPx = metrics.stringWidth(message);
                        } else {
                            currentWidthPx = metrics.stringWidth(message.split("\n")[0]); // @NOTE only use the first line
                        }
                        assert currentWidthPx != -1;
                        if (currentWidthPx >= targetWidthPx) {
                            break;
                        }
                        fontSize += 0.20f;
                    }

                    final FontMetrics metrics = g.getFontMetrics(font);
                    final float targetXPosPx = (canvas.getWidth()  * (0.5f * 100.0f) / 100.0f) - (targetWidthPx / 2);
                    final float targetYPosPx = (canvas.getHeight() * (0.5f * 100.0f) / 100.0f);// - (metrics.getHeight() / 2);

                    g.setFont(font.deriveFont(fontSize));
                    g.setColor(Color.WHITE);

                    final String[] lines = message.split("\n"); // @NOTE split by the actual line feed byte
                    final int strHeight = g.getFontMetrics().getHeight();
                    float y = targetYPosPx;
                    for (int i = 0, l = lines.length; i < l; ++i) {
                        final String line = lines[i];
                        if (i != 0) {
                            g.drawString(line, (int) targetXPosPx, y += strHeight);
                        } else {
                            g.drawString(line, (int) targetXPosPx, y);
                        }
                    }
                }

                renderDebugInformation(g);

                if (isMousePointerActive) {
                    mousePointer.render(g);
                }

                g.dispose();
            } while (backBuffers.contentsRestored());

            // @NOTE blit (or page flip) offscreen buffer
            backBuffers.show();

            // @NOTE this is to ensure everything that has been buffered is flushed so the screen is up to date.
            // @TODO Unfortunately this takes a fair bit of time and does not really seem to be necessary. So what we could
            // do is to only do this if we are hitting our target frame rate. If we don't we will not do it. Also it seems
            // like (based on what I read) that this really is only necessary on Linux? Windows seems to do fine without it.
            Toolkit.getDefaultToolkit().sync();

        } while (backBuffers.contentsLost());
    }

    private void renderDebugInformation(final Graphics2D g) {
        if (debugLevel == DebugLevel.NONE) {
            return;
        }

        g.setFont(new Font("Consolas", Font.PLAIN, 14));
        g.setColor(Color.WHITE);

        if (debugLevel == DebugLevel.MINIMAL) {
            g.drawString(String.format("Time: %.3f/%.3f ms (%s hz)", mainLoop.rawFrameTimeMillis, mainLoop.cookedFrameTimeMillis, mainLoop.hz), 16, 16);
            g.drawString(String.format("Frames: %s", mainLoop.totalFramesRendered), 16, 32);
            g.drawString(String.format("Render dimension: %s:%s", canvas.getWidth(), canvas.getHeight()), 16, 48);
            g.drawString(String.format("Aspect ratio: %s", currentAspectRatio), 16, 64);
            g.drawString(String.format("Current slide: %s/%s", (slideIndex + 1), slideshow.length), 16, 80);
            return;
        }

        if (debugLevel == DebugLevel.EXTENDED) {
            g.drawString(String.format("Time: %.3f/%.3f ms (%s hz)", mainLoop.rawFrameTimeMillis, mainLoop.cookedFrameTimeMillis, mainLoop.hz), 16, 16);
            g.drawString(String.format("Frames: %s", mainLoop.totalFramesRendered), 16, 32);

            // @NOTE this approach of calculating only works when we set the 'Xms' and 'Xmx' to the same value
            final long maxHeapMemoryMb  = Runtime.getRuntime().maxMemory()  / (long) Math.pow(1024, 2);
            final long usedHeapMemoryMb = maxHeapMemoryMb - (Runtime.getRuntime().freeMemory() / (long) Math.pow(1024, 2));
            g.drawString(String.format("Memory: %s/%s mb", usedHeapMemoryMb, maxHeapMemoryMb), 16, 48);

            final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans(); // can not cache this, since it can change during runtime
            long gcTotalTime = 0;
            long gcTotalCount = 0;
            for (final GarbageCollectorMXBean gcBean : gcBeans) {
                final long gcTime  = gcBean.getCollectionTime();
                final long gcCount = gcBean.getCollectionCount();
                if (gcTime  == -1) continue;
                if (gcCount == -1) continue;

                gcTotalTime  += gcTime;
                gcTotalCount += gcCount;
            }
            final String gcStr = String.format("GC: %s/%s (ms/cnt)", gcTotalTime, gcTotalCount);
            g.drawString(gcStr, 16, 64);

            final CompilationMXBean jitBean = ManagementFactory.getCompilationMXBean();
            if (jitBean.isCompilationTimeMonitoringSupported()) {
                final String jitStr =  String.format("JIT: %s ms", jitBean.getTotalCompilationTime());
                g.drawString(jitStr, 16, 80);
            }

            final int threadCount = Thread.getAllStackTraces().keySet().size();
            g.drawString(String.format("Total threads: %s", threadCount), 16, 96);

            final int availableCores = Runtime.getRuntime().availableProcessors();
            g.drawString(String.format("CPU cores: %s", availableCores), 16, 112);

            g.drawString(String.format("Render dimension: %s:%s", canvas.getWidth(), canvas.getHeight()), 16, 128);
            g.drawString(String.format("Aspect ratio: %s", currentAspectRatio), 16, 144);
            g.drawString(String.format("Current slide: %s/%s", (slideIndex + 1), slideshow.length), 16, 160);
        }
    }

    private final class MainLoop implements Runnable {

        public volatile boolean running = false;

        public long totalFramesRendered     = 0;
        public double cookedFrameTimeMillis = 0;
        public double rawFrameTimeMillis    = 0;

        public final int hz;
        private final double targetTimeMillis;
        private final long OVERSLEEP_GUARD = estimateSchedulerGranularity();

        public MainLoop(final int hz) {
            this.hz = hz;

            Main.logger.log(Level.INFO, "Creating mainloop with " + hz + " hz");
            targetTimeMillis = 1000.0d / (double) hz;
        }

        @Override
        public void run() {
            running = true;

            while (running) {
                double startTimeMillis = now();
                try {
                    EventQueue.invokeAndWait(() -> {
                        input();
                        update();
                        render();
                        totalFramesRendered += 1;
                    });
                } catch (final InvocationTargetException ex) {
                    if (ex.getCause() != null) {
                        final Throwable error = ex.getCause();
                        if (error instanceof AssertionError) {
                            Main.handleAssert((AssertionError) error);
                        } else {
                            // @TODO: Display dialog to user?
                            if (Main.isDebugMode()) error.printStackTrace(System.err);
                            Main.logger.log(Level.SEVERE, error.getMessage(), error.toString());
                        }
                    } else {
                        // @TODO: Display dialog to user?
                        if (Main.isDebugMode()) ex.printStackTrace(System.err);
                        Main.logger.log(Level.SEVERE, ex.getMessage(), ex.toString());
                    }
                } catch (final InterruptedException ex) {
                    assert false : "Not supposed to interrupt this thread!";
                }

                double workTimeMillis = now() - startTimeMillis;
                rawFrameTimeMillis = workTimeMillis;
                if (workTimeMillis < targetTimeMillis) {
                    assert !EventQueue.isDispatchThread() : "Must not sleep on UI thread!";

                    // @NOTE We still have time, so we have to artifically wait to reach the target time
                    // @TODO Maybe it is better (and more accurate) to sleep for OVERSLEEP_GUARD until we reached
                    // 'waitTime - OVERSLEEP_GUARD < 0'?.
                    final long waitTime = (long) (targetTimeMillis - workTimeMillis);
                    if (waitTime - OVERSLEEP_GUARD > 0) {
                        sleepMillis(waitTime - OVERSLEEP_GUARD);
                    }

                    // @NOTE Busy wait the rest of time
                    while (workTimeMillis < targetTimeMillis) {
                        workTimeMillis = now() - startTimeMillis;
                    }

                } else {
                    // @NOTE We have missed our target time :(
                }

                cookedFrameTimeMillis = now() - startTimeMillis;
            }
        }

        private void sleepMillis(final long millis) {
            assert millis > 0;

            try {
                Thread.sleep(millis, 0);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }

        private boolean isLagging() {
            return rawFrameTimeMillis > targetTimeMillis;
        }

        private double now() {
            return System.nanoTime() / 1000000.0d;
        }

        private long estimateSchedulerGranularity() {
            final int amountOfTestRuns = 50;
            final long[] result = new long[amountOfTestRuns];
            for (int i = 0, l = result.length; i < l; ++i) {
                final double beforeSleep = now();
                sleepMillis(1);
                long deltaTime = (long) (now() - beforeSleep);
                if (deltaTime == 0) {
                    deltaTime = 1;
                }
                result[i] = deltaTime;
            }

            Arrays.sort(result);
            return result[0];
        }
    }

    public final class InputHandler extends KeyAdapter {

        private final boolean[] keys = new boolean[Short.MAX_VALUE / 2]; // @NOTE pressed in current frame
        private final boolean[] lastKeys = new boolean[keys.length];     // @NOTE pressed in last frame
        {
            assert keys.length == lastKeys.length;
        }

        // @NOTE gets called every frame (must be last call inside input code)
        void update() {
            for (int i = 0; i < keys.length; ++i) {
                lastKeys[i] = keys[i];
            }
        }

        // @NOTE current frame
        public boolean isKeyPressed(final int code) {
            return keys[code];
        }

        // @NOTE when key is released/up
        public boolean isKeyUp(final int code) {
            return !keys[code] && lastKeys[code];
        }

        // @NOTE when key is pressed/down
        public boolean isKeyDown(final int code) {
            return keys[code] && !lastKeys[code];
        }

        @Override
        public void keyPressed(final KeyEvent evt) {
            keys[evt.getKeyCode()] = true;
        }

        @Override
        public void keyReleased(final KeyEvent evt) {
            keys[evt.getKeyCode()] = false;
        }
    }
}
