// src/model/ImageProcessor.java
package model;

import javax.swing.DefaultListModel;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;

public class ImageProcessor implements Serializable {
    private static final long serialVersionUID = 2L;

    private final Map<String, Image> images = new HashMap<>();

    // UI-модели не сериализуются
    private transient DefaultListModel<String> imagesModel = new DefaultListModel<>();
    private transient DefaultListModel<String> pointsModel = new DefaultListModel<>();

    private String activeImageKey = null;
    private String activePointName = null;

    public ImageProcessor() {
        imagesModel = new DefaultListModel<>();
        pointsModel = new DefaultListModel<>();
    }

    public void addImage(String key, BufferedImage bufferedImage) {
        if (images.containsKey(key)) return;
        images.put(key, new Image(key, bufferedImage));
        imagesModel.addElement(key);
        setActiveImage(key);
    }

    public void setActiveImage(String key) {
        if (!images.containsKey(key)) {
            throw new RuntimeException("Image \"" + key + "\" does not exist");
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
     * Возвращает список всех Point2D для активного изображения.
     */
    public List<Point2D> getActiveImagePoints() {
        if (activeImageKey == null) {
            return Collections.emptyList();
        }
        return images.get(activeImageKey).getPoints();
    }

    public void addPointToImage(Point2D newPoint) {
        if (activeImageKey == null) {
            throw new IllegalStateException("No active image selected");
        }
        Image img = images.get(activeImageKey);
        boolean existed = img.addPoint(newPoint);
        if (!existed) {
            pointsModel.addElement(newPoint.getName());
        }
        activePointName = newPoint.getName();
    }

    public Point2D getActivePoint() {
        if (activeImageKey == null || activePointName == null) {
            return null;
        }
        for (Point2D p : images.get(activeImageKey).getPoints()) {
            if (p.getName().equals(activePointName)) {
                return p;
            }
        }
        return null;
    }

    public void setActivePoint(String pointName) {
        if (activeImageKey == null) {
            throw new IllegalStateException("No active image selected");
        }
        for (Point2D p : images.get(activeImageKey).getPoints()) {
            if (p.getName().equals(pointName)) {
                activePointName = pointName;
                return;
            }
        }
        throw new IllegalArgumentException("Point \"" + pointName + "\" not found");
    }

    public void save(String saveFilePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(saveFilePath))) {
            oos.writeObject(this);
        }
    }

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

    public void replaceWith(ImageProcessor other) {
        // 1) скопировать map изображений
        this.images.clear();
        this.images.putAll(other.images);

        // 2) скопировать текущий выбор
        this.activeImageKey = other.activeImageKey;
        this.activePointName = other.activePointName;

        // 3) восстановить модели списков
        imagesModel.clear();
        for (String key : images.keySet()) {
            imagesModel.addElement(key);
        }

        pointsModel.clear();
        if (activeImageKey != null) {
            for (Point2D p : images.get(activeImageKey).getPoints()) {
                pointsModel.addElement(p.getName());
            }
        }
    }
}
