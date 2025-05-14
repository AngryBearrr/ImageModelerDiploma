// src/ui/ImagePanel.java
package ui;

import model.ImageProcessor;
import model.Point2D;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class ImagePanel extends JPanel {
    private BufferedImage image;
    private final ImageProcessor processor;

    public ImagePanel(ImageProcessor processor) {
        this.processor = processor;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    public Point panelToImageCoords(Point p) {
        if (image == null) return p;

        int pw = getWidth(), ph = getHeight();
        int iw = image.getWidth(), ih = image.getHeight();
        double ratio = Math.min((double) pw / iw, (double) ph / ih);
        int w = (int) (iw * ratio), h = (int) (ih * ratio);
        int offsetX = (pw - w) / 2, offsetY = (ph - h) / 2;

        int imgX = (int) ((p.x - offsetX) / ratio);
        int imgY = (int) ((p.y - offsetY) / ratio);

        imgX = Math.max(0, Math.min(iw - 1, imgX));
        imgY = Math.max(0, Math.min(ih - 1, imgY));
        return new Point(imgX, imgY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) return;

        int pw = getWidth(), ph = getHeight();
        int iw = image.getWidth(), ih = image.getHeight();
        double ratio = Math.min((double) pw / iw, (double) ph / ih);
        int w = (int) (iw * ratio), h = (int) (ih * ratio);
        int offsetX = (pw - w) / 2, offsetY = (ph - h) / 2;

        setBackground(Palette.DARKEST_GREY);
        g.drawImage(image, offsetX, offsetY, w, h, this);

        if (processor.activeImageExists()) {
            List<Point2D> points = processor.getActiveImagePoints();
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            for (Point2D pt : points) {
                int drawX = offsetX + (int)(pt.getX() * ratio);
                int drawY = offsetY + (int)(pt.getY() * ratio);

                g2d.setColor(Color.BLUE);
                int r = 6;
                g2d.fillOval(drawX - r/2, drawY - r/2, r, r);
            }
            g2d.dispose();
        }
    }
}
