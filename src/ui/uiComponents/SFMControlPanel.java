package ui.uiComponents;

import lombok.Getter;
import lombok.Setter;
import model.Point3D;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import ui.theme.Palette;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Панель управления облаком точек SfM:
 *  - храним оригинал
 *  - накапливаем globalT и globalR
 *  - applyTransforms() пересчитывает transformedPoints
 */
public class SFMControlPanel extends JPanel {
    private final List<Point3D> originalPoints;
    private final List<Point3D> transformedPoints = new ArrayList<>();

    @Getter private final JList<Point3D> pointList;
    private final DefaultListModel<Point3D> listModel;

    private final MyButton centerButton;
    private final MyButton alignXButton;
    private final MyButton alignYButton;
    private final MyButton alignZButton;

    private final List<Integer> clickOrder = new ArrayList<>();

    private Mat globalT = Mat.zeros(3, 1, CvType.CV_64F);
    private Mat globalR = Mat.eye(3, 3, CvType.CV_64F);

    @Setter private Runnable onTransform;

    public SFMControlPanel(List<Point3D> points) {
        super(new BorderLayout());

        this.originalPoints = points.stream()
                .map(p -> new Point3D(p.getName(), p.getX(), p.getY(), p.getZ()))
                .collect(Collectors.toList());

        listModel = new DefaultListModel<>();
        originalPoints.forEach(listModel::addElement);
        pointList = new JList<>(listModel);
        pointList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pointList.setCellRenderer(new MyListCellRenderer());

        pointList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = pointList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    clickOrder.remove((Integer) idx);
                    clickOrder.add(idx);
                    if (clickOrder.size() > 2) clickOrder.remove(0);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(pointList);
        scroll.getViewport().setBackground(Palette.DARK_GREY);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(250, 400));
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(4,1,5,5));
        buttons.setBackground(Palette.MEDIUM_GREY);
        buttons.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        centerButton = new MyButton("Set as Origin");
        alignXButton  = new MyButton("Align X-axis");
        alignYButton  = new MyButton("Align Y-axis");
        alignZButton  = new MyButton("Align Z-axis");
        buttons.add(centerButton);
        buttons.add(alignXButton);
        buttons.add(alignYButton);
        buttons.add(alignZButton);
        add(buttons, BorderLayout.SOUTH);

        centerButton.addActionListener(e -> {
            Point3D sel = pointList.getSelectedValue();
            if (sel == null) {
                JOptionPane.showMessageDialog(
                        this, "Select one point to set as origin.",
                        "No selection", JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            globalT = Mat.zeros(3,1,CvType.CV_64F);
            globalT.put(0,0, -sel.getX(), -sel.getY(), -sel.getZ());
            applyTransforms();
        });

        ActionListener aligner = e -> {
            if (clickOrder.size() != 2) {
                JOptionPane.showMessageDialog(
                        this, "Please click exactly two points (in order).",
                        "Invalid selection", JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            Point3D p1 = originalPoints.get(clickOrder.get(0));
            Point3D p2 = originalPoints.get(clickOrder.get(1));

            Mat m1 = new Mat(3,1,CvType.CV_64F),
                    m2 = new Mat(3,1,CvType.CV_64F);
            m1.put(0,0, p1.getX(), p1.getY(), p1.getZ());
            m2.put(0,0, p2.getX(), p2.getY(), p2.getZ());
            Core.add(m1, globalT, m1);
            Core.add(m2, globalT, m2);

            double vx = m2.get(0,0)[0] - m1.get(0,0)[0];
            double vy = m2.get(1,0)[0] - m1.get(1,0)[0];
            double vz = m2.get(2,0)[0] - m1.get(2,0)[0];
            double vnorm = Math.sqrt(vx*vx + vy*vy + vz*vz);
            vx/=vnorm; vy/=vnorm; vz/=vnorm;

            double ux=0, uy=0, uz=0;
            if (e.getSource()==alignXButton) ux=1;
            if (e.getSource()==alignYButton) uy=1;
            if (e.getSource()==alignZButton) uz=1;

            double cx = vy*uz - vz*uy;
            double cy = vz*ux - vx*uz;
            double cz = vx*uy - vy*ux;
            double dot = vx*ux + vy*uy + vz*uz;
            double angle = Math.acos(dot);

            double cn = Math.sqrt(cx*cx + cy*cy + cz*cz);
            if (cn < 1e-6) return;
            double ax = cx/cn, ay = cy/cn, az = cz/cn;

            Mat rVec = new Mat(3,1,CvType.CV_64F);
            rVec.put(0,0, ax*angle, ay*angle, az*angle);
            Mat R_new = new Mat();
            Calib3d.Rodrigues(rVec, R_new);

            // — вот изменение: сначала старый, потом новый
            Mat tmp = new Mat();
            Core.gemm(globalR, R_new, 1.0, new Mat(), 0.0, tmp);
            globalR = tmp;

            applyTransforms();
        };

        alignXButton.addActionListener(aligner);
        alignYButton.addActionListener(aligner);
        alignZButton.addActionListener(aligner);

        applyTransforms();
    }

    private void applyTransforms() {
        transformedPoints.clear();
        for (Point3D p : originalPoints) {
            Mat pt = new Mat(3,1,CvType.CV_64F);
            pt.put(0,0, p.getX(), p.getY(), p.getZ());
            Core.add(pt, globalT, pt);
            Mat out = new Mat();
            Core.gemm(globalR, pt, 1.0, new Mat(), 0.0, out);
            double x = out.get(0,0)[0],
                    y = out.get(1,0)[0],
                    z = out.get(2,0)[0];
            transformedPoints.add(new Point3D(p.getName(), x, y, z));
        }
        if (onTransform != null) onTransform.run();
    }

    public List<Point3D> getTransformedPoints() {
        return new ArrayList<>(transformedPoints);
    }
}
