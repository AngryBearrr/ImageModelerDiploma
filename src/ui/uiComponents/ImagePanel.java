// src/ui/ImagePanel.java
package ui.uiComponents;

import model.ImageProcessor;
import model.Point2D;
import ui.theme.Palette;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class ImagePanel extends JPanel {
    private BufferedImage image;
    private final ImageProcessor processor;

    // дополнительные трансформации для зума/панорамы
    private double scale = 1.0;
    private double translateX = 0.0;
    private double translateY = 0.0;
    private Point dragStart;

    public ImagePanel(ImageProcessor processor) {
        this.processor = processor;
        setBackground(Palette.DARKEST_GREY);

        // масштабирование колёсиком мыши
        addMouseWheelListener(e -> {
            if (image == null) return;

            // пересчёт базовой подгонки
            int pw = getWidth(), ph = getHeight();
            int iw = image.getWidth(), ih = image.getHeight();
            double fitRatio = Math.min((double) pw / iw, (double) ph / ih);
            int w = (int) (iw * fitRatio), h = (int) (ih * fitRatio);
            int baseX = (pw - w) / 2, baseY = (ph - h) / 2;

            // текущие координаты курсора
            Point mouse = e.getPoint();
            double mx = mouse.x;
            double my = mouse.y;

            // изображение-координаты точки под курсором до зума
            double ix = (mx - baseX - translateX) / (fitRatio * scale);
            double iy = (my - baseY - translateY) / (fitRatio * scale);

            // обновляем scale
            double delta = -e.getPreciseWheelRotation() * 0.1;
            double newScale = scale + delta * scale;
            newScale = Math.max(0.2, Math.min(newScale, 10));

            // корректируем смещение, чтобы точка осталась под курсором
            translateX = mx - baseX - ix * fitRatio * newScale;
            translateY = my - baseY - iy * fitRatio * newScale;
            scale = newScale;

            repaint();
        });

        // панорамирование перетаскиванием
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) return;
                Point dragEnd = e.getPoint();
                translateX += (dragEnd.x - dragStart.x);
                translateY += (dragEnd.y - dragStart.y);
                dragStart = dragEnd;
                repaint();
            }
        });
    }

    /** Устанавливает новое изображение и сбрасывает зум/пан */
    public void setImage(BufferedImage image) {
        this.image = image;
        scale = 1.0;
        translateX = 0;
        translateY = 0;
        repaint();
    }

    /**
     * Переводит координаты панели в координаты изображения,
     * учитывая зум, панорамирование и подгонку по размеру.
     */
    public Point panelToImageCoords(Point p) {
        if (image == null) return p;

        int pw = getWidth(), ph = getHeight();
        int iw = image.getWidth(), ih = image.getHeight();
        double fitRatio = Math.min((double) pw / iw, (double) ph / ih);
        int w = (int) (iw * fitRatio), h = (int) (ih * fitRatio);
        int baseX = (pw - w) / 2, baseY = (ph - h) / 2;

        // убираем базовое выравнивание и пан/зум
        double ix = (p.x - baseX - translateX) / (fitRatio * scale);
        double iy = (p.y - baseY - translateY) / (fitRatio * scale);

        int imgX = (int) Math.round(ix);
        int imgY = (int) Math.round(iy);
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
        double fitRatio = Math.min((double) pw / iw, (double) ph / ih);
        int w = (int) (iw * fitRatio), h = (int) (ih * fitRatio);
        int baseX = (pw - w) / 2, baseY = (ph - h) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        // применяем базовое выравнивание + панорамирование + масштаб
        g2.translate(baseX + translateX, baseY + translateY);
        g2.scale(fitRatio * scale, fitRatio * scale);

        // рисуем само изображение
        g2.drawImage(image, 0, 0, this);

        // рисуем точки
        if (processor.activeImageExists()) {
            List<Point2D> points = processor.getActiveImagePoints();
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
            g2.setColor(Color.BLUE);
            int r = 6;
            for (Point2D pt : points) {
                int x = (int) Math.round(pt.getX());
                int y = (int) Math.round(pt.getY());
                g2.fillOval(x - r/2, y - r/2, r, r);
                // подпись
                g2.setColor(Color.WHITE);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 24f / (float)scale));
                g2.drawString(pt.getName(), x + r, y - r);
                g2.setColor(Color.BLUE);
            }
        }

        g2.dispose();
    }
}
