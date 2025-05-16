package model;

import lombok.Data;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class Image implements Serializable {
    private static final long serialVersionUID = 2L;

    private String imagePath;
    private final List<Point2D> points = new ArrayList<>();

    private transient BufferedImage bufferedImage;

    public Image(String imagePath, BufferedImage bufferedImage) {
        this.imagePath = imagePath;
        this.bufferedImage = bufferedImage;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Image image = (Image) o;
        return Objects.equals(imagePath, image.imagePath) && Objects.equals(points, image.points) && Objects.equals(bufferedImage, image.bufferedImage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imagePath);
    }
}
