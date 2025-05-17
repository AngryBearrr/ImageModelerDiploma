package ui;

import model.Point3D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Панель для отображения облака точек с возможностью зума, вращения камеры,
 * перспективной проекции, плоскостью, осями координат, отображением имен точек
 * и выделением активной точки.
 */
public class PointCloud3DPanel extends JPanel implements MouseWheelListener {
    private List<Point3D> cloud;

    // Индекс активной точки (-1, если нет)
    private int activeIndex = -1;

    // Зум и шаг зума
    private double zoom = 1.0;
    private static final double ZOOM_STEP = 1.1;

    // Углы вращения камеры
    private double yaw = 0;   // вокруг Y
    private double pitch = 0; // вокруг X
    private Point lastDrag;

    // Масштаб координат точек
    private double scaleFactor = 1.0;

    // Параметры перспективы
    private double focalLength = 1000.0;    // фокусное расстояние
    private double cameraDistance = 1000.0; // базовая глубина камеры

    public PointCloud3DPanel(List<Point3D> cloud) {
        this.cloud = new ArrayList<>(cloud);
        setBackground(Color.BLACK);
        addMouseWheelListener(this);
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastDrag = e.getPoint();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                Point p = e.getPoint();
                int dx = p.x - lastDrag.x;
                int dy = p.y - lastDrag.y;
                yaw   += dx * 0.01;
                pitch += dy * 0.01;
                lastDrag = p;
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    /**
     * Обновить облако точек и перерисовать.
     */
    public void updatePoints(List<Point3D> newCloud) {
        this.cloud = new ArrayList<>(newCloud);
        this.activeIndex = -1;
        repaint();
    }

    /**
     * Получить текущее облако точек.
     */
    public List<Point3D> getPoints() {
        return new ArrayList<>(cloud);
    }

    /**
     * Установить индекс активной точки и перерисовать.
     */
    public void setActiveIndex(int index) {
        this.activeIndex = index;
        repaint();
    }

    public void setScaleFactor(double scaleFactor) {
        this.scaleFactor = scaleFactor;
        repaint();
    }

    public double getScaleFactor() {
        return scaleFactor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        g2.translate(w / 2.0, h / 2.0);
        g2.scale(zoom, zoom);

        drawGrid(g2);
        drawAxes(g2);
        drawPoints(g2);

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(new Color(50, 50, 50));
        int gridSize = 500;
        int step = 50;
        for (int x = -gridSize; x <= gridSize; x += step) {
            drawLine3D(g2, x, 0, -gridSize, x, 0, gridSize);
        }
        for (int z = -gridSize; z <= gridSize; z += step) {
            drawLine3D(g2, -gridSize, 0, z, gridSize, 0, z);
        }
    }

    private void drawAxes(Graphics2D g2) {
        Stroke orig = g2.getStroke();
        g2.setStroke(new BasicStroke(2));
        g2.setColor(Color.RED);
        drawLine3D(g2, 0, 0, 0, 200, 0, 0);
        g2.setColor(Color.GREEN);
        drawLine3D(g2, 0, 0, 0, 0, 200, 0);
        g2.setColor(Color.BLUE);
        drawLine3D(g2, 0, 0, 0, 0, 0, 200);
        g2.setStroke(orig);
    }

    private void drawPoints(Graphics2D g2) {
        int size = 6;
        for (int i = 0; i < cloud.size(); i++) {
            Point3D p3 = cloud.get(i);
            double x = p3.getX() * scaleFactor;
            double y = p3.getY() * scaleFactor;
            double z = p3.getZ() * scaleFactor;
            Point p = project(x, y, z);
            // Цвет точки
            if (i == activeIndex) g2.setColor(Color.YELLOW);
            else g2.setColor(Color.WHITE);
            g2.fillOval(p.x - size/2, p.y - size/2, size, size);
            // Метки точек
            g2.setColor(i == activeIndex ? Color.YELLOW : Color.CYAN);
            g2.drawString(p3.getName(), p.x + size, p.y - size);
        }
    }

    private void drawLine3D(Graphics2D g2,
                            double x1, double y1, double z1,
                            double x2, double y2, double z2) {
        Point p1 = project(x1,y1,z1);
        Point p2 = project(x2,y2,z2);
        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
    }

    private Point project(double x, double y, double z) {
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        double y1 = y*cosP - z*sinP;
        double z1 = y*sinP + z*cosP;
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double x2 = x*cosY + z1*sinY;
        double z2 = -x*sinY + z1*cosY;
        double depth = z2 + cameraDistance;
        if (depth < 1e-3) depth = 1e-3;
        double px = x2 * (focalLength / depth);
        double py = -y1 * (focalLength / depth);
        return new Point((int)Math.round(px), (int)Math.round(py));
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        if (notches < 0) zoom *= Math.pow(ZOOM_STEP, -notches);
        else zoom /= Math.pow(ZOOM_STEP, notches);
        zoom = Math.max(0.1, Math.min(zoom, 10));
        repaint();
    }
}