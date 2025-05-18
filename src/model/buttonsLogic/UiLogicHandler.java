package model.buttonsLogic;

import model.ImageProcessor;
import model.Point2D;
import ui.uiComponents.ImagePanel;
import ui.mainWindow.MainFrame;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.event.ListSelectionListener;

public class UiLogicHandler {

    public static void addPoint(MainFrame frame, ImageProcessor processor, ImagePanel imagePanel) {
        if (!(processor.activeImageExists())){
            JOptionPane.showMessageDialog(
                    imagePanel,
                    "image is not selected",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        String pointName = JOptionPane.showInputDialog(
                frame,
                "Введите имя точки:",
                "Новая точка",
                JOptionPane.PLAIN_MESSAGE
        );
        if (pointName != null && !pointName.isEmpty()) {
            processor.addPointToImage(new Point2D(pointName, 0, 0));
            imagePanel.repaint();
        }
    }

    public static void addImage(MainFrame frame, ImageProcessor processor, ImagePanel imagePanel) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                BufferedImage img = ImageIO.read(file);
                String imageName = file.getName();
                processor.addImage(imageName, img);
                imagePanel.setImage(img);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                        frame,
                        "Не удалось загрузить изображение: " + ex.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    public static ListSelectionListener changeActiveImage(ImageProcessor processor, ImagePanel imagePanel, JList<String> imageList){
        return e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = imageList.getSelectedValue();
                processor.setActiveImage(selected);
                imagePanel.setImage(processor.getActiveImage());
                imagePanel.repaint();
            }
        };
    }
}