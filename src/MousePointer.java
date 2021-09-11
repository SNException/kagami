import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;

public final class MousePointer {

    private float mx;
    private float my;
    private float mw;
    private float mh;

    public void update(final Point mouse, final float mw, final float mh) {
        if (mouse != null) {
            this.mx = (float) mouse.getX();
            this.my = (float) mouse.getY();
            this.mw = mw;
            this.mh = mh;
        } else {
            // @NOTE basically don't render since the mouse has been moveed outside of the drawing area
            this.mw = 0;
            this.mh = 0;
        }
    }

    public void render(final Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 255));
        g.setStroke(new BasicStroke(2));
        g.drawOval((int) mx, (int) my, (int) mw, (int) mh);
        g.setColor(new Color(255, 255, 0, 200));
        g.fillOval((int) mx, (int) my, (int) mw, (int) mh);
    }
}
