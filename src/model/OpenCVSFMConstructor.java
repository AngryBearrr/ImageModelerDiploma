package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OpenCVSFMConstructor {

    static {
        System.load("E:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java4110.dll");
    }

    private static Point pointFromPoint2D(Point2D point){
        return new Point(point.getX(), point.getY());
    }

    private static Point3 point3FromPoint3D(Point3D point){
        return new Point3 (point.getX(), point.getY(), point.getZ());
    }

    private static MatOfPoint2f MatOfPoints2fFromPointsList(List<Point2D> points){
        Point[] point2f = new Point[points.size()];
        for (int i = 0; i < points.size(); i++){
            point2f[i] = new Point(points.get(i).getX(), points.get(i).getY());
        }
        return new MatOfPoint2f(point2f);
    }

    /**
     * Ищет пару изображений с наибольшим числом inlier-соответствий
     * по 5-точечному алгоритму Essential+RANSAC.
     */
    private static ImagePair findBestPair5(ImageProcessor proc) {
        ImagePair best = new ImagePair();
        // получаем список ключей (имён) всех изображений
        List<String> keys = Collections.list(proc.getImagesModel().elements());

        for (int i = 0; i < keys.size() - 1; i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                String keyA = keys.get(i), keyB = keys.get(j);

                // 1) Собираем списки разметки
                proc.setActiveImage(keyA);
                List<Point2D> ptsA = proc.getActiveImagePoints();
                proc.setActiveImage(keyB);
                List<Point2D> ptsB = proc.getActiveImagePoints();

                // 2) Фильтруем только общие точки по имени
                Map<String,Point2D> mapB = ptsB.stream()
                        .collect(Collectors.toMap(Point2D::getName, Function.identity()));
                List<Point2D> commonA = new ArrayList<>(), commonB = new ArrayList<>();
                for (Point2D p : ptsA) {
                    Point2D q = mapB.get(p.getName());
                    if (q != null) {
                        commonA.add(p);
                        commonB.add(q);
                    }
                }
                // Если меньше 5 — пропускаем
                if (commonA.size() < 5) continue;

                // 3) Аппроксимация K для обоих изображений (они одинаковые по размеру)
                proc.setActiveImage(keyA);
                BufferedImage img = proc.getActiveImage();
                Mat K = estimateCameraMatrix(img);
                // Нормализуем через K^{-1}: (u-cx)/fx, (v-cy)/fy
                MatOfPoint2f mA = normalizePoints(commonA, K);
                MatOfPoint2f mB = normalizePoints(commonB, K);

                // 4) Оцениваем Essential-матрицу и RANSAC-маску
                Mat mask = new Mat();
                Mat E = Calib3d.findEssentialMat(
                        mA,                    // MatOfPoint2f, ваши нормированные точки A
                        mB,                    // MatOfPoint2f, ваши нормированные точки B
                        K,                     // Mat камеры
                        Calib3d.RANSAC,        // метод
                        0.99,                  // confidence
                        1.0,                   // threshold в нормированных coords
                        10000,
                        mask                   // выходная маска inliers
                );

                // 5) Считаем inlier-точки
                int inliers = Core.countNonZero(mask);
                if (inliers > best.getCor()) {
                    best.setCor(inliers);
                    best.setImage1(keyA);
                    best.setImage2(keyB);
                }
            }
        }

        return best;
    }


//    private static List<Point3D> reconstructAll(ImageProcessor processor){
//
//    }

}

@Data
@AllArgsConstructor
@NoArgsConstructor
class ImagePair{
    private String image1;
    private String image2;
    private int cor = 0;
}