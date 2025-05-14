// src/model/ImageProcessor.java
package model;

import javax.swing.DefaultListModel;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ImageProcessor implements Serializable {
    private static final long serialVersionUID = 2L;

    private final Map<String, Image> images = new HashMap<>();
    private transient DefaultListModel<String> imagesModel = new DefaultListModel<>();
    private transient DefaultListModel<String> pointsModel = new DefaultListModel<>();

    private String activeImageKey = null;
    private String activePointName = null;

    public ImageProcessor() {
        // инициализация транзиентных полей
        this.imagesModel = new DefaultListModel<>();
        this.pointsModel = new DefaultListModel<>();
    }

    public void addImage(String key, BufferedImage bufferedImage) {
        if (images.containsKey(key)) return;
        images.put(key, new Image(key, bufferedImage));
        imagesModel.addElement(key);
        setActiveImage(key);
    }

    public void setActiveImage(String key) {
        if (!images.containsKey(key)) {
            throw new RuntimeException("image " + key + " does not exist");
        }
        activeImageKey = key;
        activePointName = null;
        pointsModel.clear();
        for (Point2D p : images.get(key).getPoints()) {
            pointsModel.addElement(p.getName());
        }
    }

    public BufferedImage getActiveImage() {
        if (activeImageKey == null) {
            throw new IllegalStateException("No active image selected");
        }
        return images.get(activeImageKey).getBufferedImage();
    }

    public DefaultListModel<String> getImagesModel() {
        return imagesModel;
    }

    public DefaultListModel<String> getPointsModel() {
        return pointsModel;
    }

    public boolean activeImageExists() {
        return activeImageKey != null;
    }

    public String getActiveImagePath() {
        if (activeImageKey == null) {
            throw new IllegalStateException("No active image selected");
        }
        return activeImageKey;
    }

    /**
     * Сохраняет состояние ImageProcessor в файл.
     */
    public void save(String saveFilePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(saveFilePath))) {
            oos.writeObject(this);
        }
    }

    /**
     * Загружает состояние ImageProcessor из файла.
     */
    public static ImageProcessor load(String saveFilePath)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(saveFilePath))) {
            return (ImageProcessor) ois.readObject();
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    @Serial
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // восстанавливаем транзиентные поля
        imagesModel = new DefaultListModel<>();
        for (String key : images.keySet()) {
            imagesModel.addElement(key);
        }
        pointsModel = new DefaultListModel<>();
        if (activeImageKey != null) {
            for (Point2D p : images.get(activeImageKey).getPoints()) {
                pointsModel.addElement(p.getName());
            }
        }
    }
}