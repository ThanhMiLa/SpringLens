package vn.io.codelearning.springapitester.ui;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class ScaledIcon implements Icon {
    private final Icon original;
    private final float scale;

    public ScaledIcon(Icon original, float scale) {
        this.original = original;
        this.scale = scale;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (original == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.scale(scale, scale);
            original.paintIcon(c, g2, 0, 0);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return original != null ? (int) (original.getIconWidth() * scale) : 0;
    }

    @Override
    public int getIconHeight() {
        return original != null ? (int) (original.getIconHeight() * scale) : 0;
    }
}
