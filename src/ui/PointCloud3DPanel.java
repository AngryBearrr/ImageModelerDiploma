package ui;

import model.Point3D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

// Если ваш Point3D в другом пакете, исправьте импорт
// import model.Point3D;

public class PointCloud3DPanel extends JPanel {
    private final List<Point3D> points;
    private double fov = 800;       // «фокусное расстояние» – регулирует глубину перспективы
    private double rotX = 0, rotY = 0;  // углы поворота модели

    public PointCloud3DPanel(List<Point3D> points) {
        this.points = points;
        setBackground(Color.BLACK);
        // Обработчик мыши для вращения модели
        MouseAdapter ma = new MouseAdapter() {
            private Point prev;
            @Override
            public void mousePressed(MouseEvent e) {
                prev = e.getPoint();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                Point cur = e.getPoint();
                rotY += (cur.x - prev.x) * 0.01;
                rotX += (cur.y - prev.y) * 0.01;
                prev = cur;
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.WHITE);
        int w = getWidth(), h = getHeight();
        int cx = w/2, cy = h/2;

        // Вспомогательные матрицы вращения по X и Y
        double sinX = Math.sin(rotX), cosX = Math.cos(rotX);
        double sinY = Math.sin(rotY), cosY = Math.cos(rotY);

        for (Point3D p : points) {
            // 1) Поворот вокруг осей X и Y
            double x = p.getX(), y = p.getY(), z = p.getZ();
            // сначала вокруг X
            double y1 = y * cosX - z * sinX;
            double z1 = y * sinX + z * cosX;
            // затем вокруг Y
            double x2 = x * cosY + z1 * sinY;
            double z2 = -x * sinY + z1 * cosY;

            // 2) Перспективная проекция
            double scale = fov / (fov + z2);
            int sx = cx + (int) Math.round(x2 * scale);
            int sy = cy - (int) Math.round(y1 * scale);

            // 3) Рисуем точку
            int r = 4;  // радиус круга
            g2.fillOval(sx - r/2, sy - r/2, r, r);
        }
    }

    // Точка входа: создаём окно и добавляем наш панель
    public static void showPointCloud(List<Point3D> cloud) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("3D Point Cloud Viewer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);
            frame.add(new PointCloud3DPanel(cloud));
            frame.setVisible(true);
        });
    }
}
