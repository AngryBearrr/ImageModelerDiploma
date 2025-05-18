package model.buttonsLogic;

import model.ImageProcessor;
import model.Point2D;
import ui.uiComponents.ImagePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MouseClickLogic extends MouseAdapter {
    private final ImageProcessor processor;
    private final ImagePanel imagePanel;

    public MouseClickLogic(ImageProcessor processor, ImagePanel panel) {
        this.processor = processor;
        this.imagePanel = panel;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Point imgPt = imagePanel.panelToImageCoords(e.getPoint());

        if (!processor.activeImageExists()){
            JOptionPane.showMessageDialog(
                    imagePanel,
                    "image is not selected",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
            Point2D selectedPoint = processor.getActivePoint();
            if (selectedPoint == null) {
                JOptionPane.showMessageDialog(
                        imagePanel,
                        "Point is not selected",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            selectedPoint.setX(imgPt.x);
            selectedPoint.setY(imgPt.y);
            imagePanel.repaint();

        } else if (SwingUtilities.isRightMouseButton(e)) {
            String name = JOptionPane.showInputDialog(
                    imagePanel,
                    "New point name",
                    "New point",
                    JOptionPane.PLAIN_MESSAGE
            );
            if (name == null || name.isEmpty()) {
                JOptionPane.showMessageDialog(
                        imagePanel,
                        "Empty point name",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            processor.addPointToImage(new Point2D(name, imgPt.x, imgPt.y));
            imagePanel.repaint();
        }
    }
}
