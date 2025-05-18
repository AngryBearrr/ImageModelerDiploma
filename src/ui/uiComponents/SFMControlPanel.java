package ui.uiComponents;

import lombok.Getter;
import lombok.Setter;
import model.Point3D;
import model.OpenCVSFMConstructor;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import ui.theme.Palette;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Панель управления облаком точек SfM:
 * - Список точек (клики запоминаются в порядке выбора)
 * - Установка центра облака
 * - Выравнивание пары точек по выбранной оси, с учётом порядка кликов
 */
public class SFMControlPanel extends JPanel {
    private DefaultListModel<Point3D> listModel;
    /**
     * -- GETTER --
     * Для MainFrame: доступ к списку точек.
     */
    @Getter
    private JList<Point3D> pointList;
    private MyButton centerButton;
    private MyButton alignXButton;
    private MyButton alignYButton;
    private MyButton alignZButton;

    /**
     * -- SETTER --
     * Колбэк, вызываемый после любой трансформации.
     */
    @Setter
    private Runnable onTransform;

    // Сохраняем последние два клика по списку
    private final List<Integer> clickOrder = new ArrayList<>();

    public SFMControlPanel(List<Point3D> points) {
        setLayout(new BorderLayout());

        // Модель и список
        listModel = new DefaultListModel<>();
        for (Point3D p : points) listModel.addElement(p);
        pointList = new JList<>(listModel);
        pointList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        pointList.setCellRenderer(new MyListCellRenderer());
        // Листенер мыши для запоминания порядка кликов
        pointList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = pointList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    // убираем, если уже есть, и добавляем в конец
                    clickOrder.remove((Integer) idx);
                    clickOrder.add(idx);
                    // оставляем только последние два
                    if (clickOrder.size() > 2) {
                        clickOrder.remove(0);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(pointList);
        scroll.getViewport().setBackground(Palette.DARK_GREY);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(250,400));
        add(scroll, BorderLayout.CENTER);

        // Кнопки
        JPanel buttons = new JPanel(new GridLayout(4,1,5,5));
        buttons.setBackground(Palette.MEDIUM_GREY);

        centerButton = new MyButton("Set as Origin");
        alignXButton  = new MyButton("Align X-axis");
        alignYButton  = new MyButton("Align Y-axis");
        alignZButton  = new MyButton("Align Z-axis");
        buttons.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        buttons.add(centerButton);
        buttons.add(alignXButton);
        buttons.add(alignYButton);
        buttons.add(alignZButton);
        add(buttons, BorderLayout.SOUTH);

        // Действия
        centerButton.addActionListener(e -> {
            Point3D p = pointList.getSelectedValue();
            if (p == null) {
                JOptionPane.showMessageDialog(this,
                        "Select one point to set as origin.",
                        "No selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Mat t = new Mat(3,1,CvType.CV_64F);
            t.put(0,0, -p.getX(), -p.getY(), -p.getZ());
            OpenCVSFMConstructor.setGlobalTranslation(t);
            if (onTransform != null) onTransform.run();
        });

        ActionListener aligner = e -> {
            if (clickOrder.size() != 2) {
                JOptionPane.showMessageDialog(this,
                        "Please click two points (in order).",
                        "Invalid selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Point3D p1 = listModel.get(clickOrder.get(0));
            Point3D p2 = listModel.get(clickOrder.get(1));
            // вектор p1->p2
            double vx = p2.getX()-p1.getX(),
                    vy = p2.getY()-p1.getY(),
                    vz = p2.getZ()-p1.getZ();
            double norm = Math.sqrt(vx*vx+vy*vy+vz*vz);
            vx/=norm; vy/=norm; vz/=norm;

            // ось
            double ux=0, uy=0, uz=0;
            if (e.getSource()==alignXButton) ux=1;
            if (e.getSource()==alignYButton) uy=1;
            if (e.getSource()==alignZButton) uz=1;

            // Rodrigues
            double cx = vy*uz - vz*uy;
            double cy = vz*ux - vx*uz;
            double cz = vx*uy - vy*ux;
            double dot = vx*ux + vy*uy + vz*uz;
            double ang = Math.acos(dot);
            Mat rVec = new Mat(3,1,CvType.CV_64F);
            rVec.put(0,0, cx*ang, cy*ang, cz*ang);
            Mat R = new Mat();
            Calib3d.Rodrigues(rVec, R);
            OpenCVSFMConstructor.setGlobalRotation(R);
            if (onTransform != null) onTransform.run();
        };
        alignXButton.addActionListener(aligner);
        alignYButton.addActionListener(aligner);
        alignZButton.addActionListener(aligner);
    }

}
