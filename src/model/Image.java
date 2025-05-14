// src/model/Image.java
package model;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Image implements Serializable {
    private static final long serialVersionUID = 2L;

    private String imagePath;
    private final List<Point2D> points = new ArrayList<>();

    // BufferedImage не Serializable — помечаем transient
    private transient BufferedImage bufferedImage;

    public Image(String imagePath, BufferedImage bufferedImage) {
        this.imagePath = imagePath;
        this.bufferedImage = bufferedImage;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }

    public boolean addPoint(Point2D newPoint) {
        // если точка с таким именем уже есть — обновляем координаты
        for (Point2D point : points) {
            if (point.getName().equals(newPoint.getName())) {
                point.setX(newPoint.getX());
                point.setY(newPoint.getY());
                return true;
            }
        }
        // иначе добавляем новую
        return points.add(newPoint);
    }

    public List<Point2D> getPoints() {
        return points;
    }

    /**
     * Ручная сериализация: сначала дефолтные поля,
     * затем PNG-байты изображения.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        if (bufferedImage != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            byte[] bytes = baos.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes);
        } else {
            out.writeInt(0);
        }
    }

    /**
     * Ручная десериализация: читаем PNG-байты обратно в BufferedImage.
     */
    @Serial
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        int length = in.readInt();
        if (length > 0) {
            byte[] bytes = in.readNBytes(length);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                bufferedImage = ImageIO.read(bais);
            }
        } else {
            bufferedImage = null;
        }
    }
}
