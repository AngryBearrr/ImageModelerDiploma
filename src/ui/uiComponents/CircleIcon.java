package ui.uiComponents;

import javax.swing.*;
import java.awt.*;

public class CircleIcon implements Icon {
    private final int diameter;
    private final Color color;

    public CircleIcon(Color color, int diameter) {
        this.color = color;
        this.diameter = diameter;
    }

    @Override
    public int getIconWidth() {
        return diameter;
    }

    @Override
    public int getIconHeight() {
        return diameter;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        g2d.fillOval(x, y, diameter, diameter);
        g2d.dispose();
    }
}