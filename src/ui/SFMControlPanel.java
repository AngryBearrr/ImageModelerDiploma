package ui;

import lombok.Getter;
import lombok.Setter;
import model.Point3D;
import model.OpenCVSFMConstructor;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Панель управления облаком точек SfM:
 * - Список точек
 * - Установка центра облака
 * - Выравнивание двух точек по выбранной оси
 */
public class SFMControlPanel extends JPanel {
    private DefaultListModel<Point3D> listModel;
    /**
     * -- GETTER --
     * Возвращает внутренний JList для навешивания слушателя.
     */
    @Getter
    private JList<Point3D>            pointList;
    private MyButton                  centerButton;
    private MyButton                  alignXButton;
    private MyButton                  alignYButton;
    private MyButton                  alignZButton;

    /**
     * -- SETTER --
     * Устанавливает колбэк, вызываемый после любой трансформации.
     */
    @Setter
    private Runnable onTransform;

    public SFMControlPanel(List<Point3D> points) {
        setLayout(new BorderLayout());
        setBackground(Palette.DARKEST_GREY);

        listModel = new DefaultListModel<>();
        for (Point3D p : points) listModel.addElement(p);

        pointList = new JList<>(listModel);
        pointList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        pointList.setCellRenderer(new MyListCellRenderer());
        pointList.setBackground(Palette.DARK_GREY);
        pointList.setForeground(Palette.TEXT);

        JScrollPane scroll = new JScrollPane(pointList);
        scroll.getViewport().setBackground(Palette.DARK_GREY);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(250,400));
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(4,1,5,5));
        buttons.setBackground(Palette.DARK_GREY);

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
            Point3D p = pointList.getSelectedValue();
            if (p != null) {
                Mat t = new Mat(3,1,CvType.CV_64F);
                t.put(0,0, -p.getX(), -p.getY(), -p.getZ());
                OpenCVSFMConstructor.setGlobalTranslation(t);
                if (onTransform != null) onTransform.run();
            }
        });

        ActionListener aligner = e -> {
            List<Point3D> sel = pointList.getSelectedValuesList();
            if (sel.size()==2) {
                Point3D a=sel.get(0), b=sel.get(1);
                double vx=b.getX()-a.getX(), vy=b.getY()-a.getY(), vz=b.getZ()-a.getZ();
                double n=Math.sqrt(vx*vx+vy*vy+vz*vz); vx/=n; vy/=n; vz/=n;
                double ux=0,uy=0,uz=0;
                if (e.getSource()==alignXButton) ux=1;
                if (e.getSource()==alignYButton) uy=1;
                if (e.getSource()==alignZButton) uz=1;
                double cx=vy*uz-vz*uy, cy=vz*ux-vx*uz, cz=vx*uy-vy*ux;
                double dot=vx*ux+vy*uy+vz*uz, ang=Math.acos(dot);
                Mat rVec=new Mat(3,1,CvType.CV_64F);
                rVec.put(0,0, cx*ang, cy*ang, cz*ang);
                Mat R=new Mat();
                Calib3d.Rodrigues(rVec,R);
                OpenCVSFMConstructor.setGlobalRotation(R);
                if (onTransform!=null) onTransform.run();
            }
        };
        alignXButton.addActionListener(aligner);
        alignYButton.addActionListener(aligner);
        alignZButton.addActionListener(aligner);
    }

}
