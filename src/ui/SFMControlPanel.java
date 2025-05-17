package ui;

import model.Point3D;
import model.OpenCVSFMConstructor;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Панель управления облаком точек SfM:
 * - Список точек
 * - Установка центральной точки (перенос в начало)
 * - Выравнивание пары точек по выбранной глобальной оси
 */
public class SFMControlPanel extends JPanel {
    private DefaultListModel<Point3D> listModel;
    private JList<Point3D> pointList;
    private JButton centerButton;
    private JButton alignXButton;
    private JButton alignYButton;
    private JButton alignZButton;

    public SFMControlPanel(List<Point3D> points) {
        setLayout(new BorderLayout());

        // Список точек
        listModel = new DefaultListModel<>();
        for (Point3D p : points) {
            listModel.addElement(p);
        }
        pointList = new JList<>(listModel);
        pointList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(pointList);
        scrollPane.setPreferredSize(new Dimension(250, 400));
        add(scrollPane, BorderLayout.CENTER);

        // Кнопки действий
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        centerButton = new JButton("Set as Origin");
        alignXButton = new JButton("Align with X-axis");
        alignYButton = new JButton("Align with Y-axis");
        alignZButton = new JButton("Align with Z-axis");

        buttonPanel.add(centerButton);
        buttonPanel.add(alignXButton);
        buttonPanel.add(alignYButton);
        buttonPanel.add(alignZButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Обработчики
        centerButton.addActionListener(new CenterAction());
        alignXButton.addActionListener(new AlignAction(AlignAction.Axis.X));
        alignYButton.addActionListener(new AlignAction(AlignAction.Axis.Y));
        alignZButton.addActionListener(new AlignAction(AlignAction.Axis.Z));
    }

    /**
     * Действие для установки центра облака в выбранную точку.
     */
    private class CenterAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Point3D p = pointList.getSelectedValue();
            if (p == null) {
                JOptionPane.showMessageDialog(SFMControlPanel.this,
                        "Please select a single point to set as origin.",
                        "No point selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Трансляция: сдвинуть так, чтобы p оказался в (0,0,0)
            double tx = -p.getX();
            double ty = -p.getY();
            double tz = -p.getZ();
            Mat t = new Mat(3, 1, CvType.CV_64F);
            t.put(0, 0, tx);
            t.put(1, 0, ty);
            t.put(2, 0, tz);
            OpenCVSFMConstructor.setGlobalTranslation(t);
            JOptionPane.showMessageDialog(SFMControlPanel.this,
                    "Global translation set.",
                    "Center Applied", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Действие для выравнивания выбранной пары точек по оси.
     */
    private class AlignAction implements ActionListener {
        enum Axis { X, Y, Z }
        private Axis axis;

        AlignAction(Axis axis) { this.axis = axis; }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<Point3D> sel = pointList.getSelectedValuesList();
            if (sel.size() != 2) {
                JOptionPane.showMessageDialog(SFMControlPanel.this,
                        "Please select exactly two points.",
                        "Invalid selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Point3D p1 = sel.get(0), p2 = sel.get(1);
            // Compute vector between points
            double vx = p2.getX() - p1.getX();
            double vy = p2.getY() - p1.getY();
            double vz = p2.getZ() - p1.getZ();
            // Normalize
            double norm = Math.sqrt(vx*vx + vy*vy + vz*vz);
            vx /= norm; vy /= norm; vz /= norm;
            // Desired axis unit vector
            double ux=0, uy=0, uz=0;
            switch(axis) {
                case X: ux=1; break;
                case Y: uy=1; break;
                case Z: uz=1; break;
            }
            // Cross and dot
            double cx = vy*uz - vz*uy;
            double cy = vz*ux - vx*uz;
            double cz = vx*uy - vy*ux;
            double dot = vx*ux + vy*uy + vz*uz;
            double angle = Math.acos(dot);
            // Build Rodrigues rotation
            Mat rVec = new Mat(3,1, CvType.CV_64F);
            rVec.put(0,0, cx * angle, cy * angle, cz * angle);
            Mat R = new Mat();
            Calib3d.Rodrigues(rVec, R);
            OpenCVSFMConstructor.setGlobalRotation(R);
            JOptionPane.showMessageDialog(SFMControlPanel.this,
                    String.format("Cloud rotated to align with %s-axis.", axis),
                    "Rotation Applied", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
