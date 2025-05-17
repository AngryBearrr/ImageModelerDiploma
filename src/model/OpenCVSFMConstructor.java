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



    private static Point pointFromPoint2D(Point2D point) {
        return new Point(point.getX(), point.getY());
    }

    private static Point3 point3FromPoint3D(Point3D point) {
        return new Point3(point.getX(), point.getY(), point.getZ());
    }

    private static MatOfPoint2f MatOfPoints2fFromPointsList(List<Point2D> points) {
        Point[] point2f = new Point[points.size()];
        for (int i = 0; i < points.size(); i++) {
            point2f[i] = new Point(points.get(i).getX(), points.get(i).getY());
        }
        return new MatOfPoint2f(point2f);
    }

    /**
     * Нормализует точки в нормированные координаты изображения (x-cx)/fx, (y-cy)/fy.
     */
    private static MatOfPoint2f normalizePoints(List<Point2D> points, Mat K) {
        double fx = K.get(0, 0)[0];
        double fy = K.get(1, 1)[0];
        double cx = K.get(0, 2)[0];
        double cy = K.get(1, 2)[0];
        Point[] normPts = new Point[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Point2D p = points.get(i);
            double x = (p.getX() - cx) / fx;
            double y = (p.getY() - cy) / fy;
            normPts[i] = new Point(x, y);
        }
        return new MatOfPoint2f(normPts);
    }

    /**
     * Оценивает матрицу камеры K, используя простую аппроксимацию:
     * f = 0.8 * max(width, height), центр в середине изображения.
     */
    private static Mat estimateCameraMatrix(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double focal = 0.8 * Math.max(w, h);
        double cx = w / 2.0;
        double cy = h / 2.0;
        Mat K = Mat.eye(3, 3, CvType.CV_64F);
        K.put(0, 0, focal);
        K.put(1, 1, focal);
        K.put(0, 2, cx);
        K.put(1, 2, cy);
        return K;
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

                // 3) Аппроксимация K для обоих изображений
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

    public static List<Point3D> reconstructAll(ImageProcessor proc) {
        // находим лучшую пару
        ImagePair best = findBestPair5(proc);
        if (best.getCor() < 5 || best.getImage1() == null) {
            throw new RuntimeException("No valid initial image pair found");
        }
        String im1 = best.getImage1(), im2 = best.getImage2();
        // получаем общие размеченные точки
        proc.setActiveImage(im1);
        List<Point2D> pts1 = proc.getActiveImagePoints();
        proc.setActiveImage(im2);
        List<Point2D> pts2 = proc.getActiveImagePoints();
        Map<String,Point2D> map2 = pts2.stream()
                .collect(Collectors.toMap(Point2D::getName, Function.identity()));
        List<Point2D> common1 = new ArrayList<>(), common2 = new ArrayList<>();
        for (Point2D p : pts1) {
            Point2D q = map2.get(p.getName());
            if (q != null) {
                common1.add(p);
                common2.add(q);
            }
        }
        if (common1.size() < 5) {
            throw new RuntimeException("Not enough correspondences to reconstruct");
        }
        // загружаем изображения и K
        BufferedImage imgA = proc.getImage(im1).getBufferedImage();
        BufferedImage imgB = proc.getImage(im2).getBufferedImage();
        Mat KA = estimateCameraMatrix(imgA);
        Mat KB = estimateCameraMatrix(imgB);
        // нормализуем и найдем E
        MatOfPoint2f m1 = MatOfPoints2fFromPointsList(common1);
        MatOfPoint2f m2 = MatOfPoints2fFromPointsList(common2);
        Mat mask = new Mat();
        Mat E = Calib3d.findEssentialMat(m1, m2, KA, Calib3d.RANSAC, 0.99, 1.0, 1000, mask);
        // восстанавливаем позы
        Mat R = new Mat(), t = new Mat();
        Calib3d.recoverPose(E, m1, m2, KA, R, t, mask);
        // создаем проекции
        Mat P1 = Mat.eye(3,4,CvType.CV_64F);
        KA.copyTo(P1.colRange(0,3));
        Mat Rt = new Mat(3,4,CvType.CV_64F);
        R.copyTo(Rt.colRange(0,3));
        t.copyTo(Rt.col(3));
        Mat P2 = new Mat();
        Core.gemm(KB, Rt, 1.0, Mat.zeros(3,4,CvType.CV_64F),0, P2);
        // триангуляция
        Mat points4D = new Mat();
        Calib3d.triangulatePoints(P1, P2, m1, m2, points4D);
        // преобразуем в List<Point3D>
        List<Point3D> cloud = new ArrayList<>();
        for (int i = 0; i < points4D.cols(); i++) {
            double w4 = points4D.get(3,i)[0];
            double x = points4D.get(0,i)[0]/w4;
            double y = points4D.get(1,i)[0]/w4;
            double z = points4D.get(2,i)[0]/w4;
            String id = common1.get(i).getName();
            cloud.add(new Point3D(id, x, y, z));
        }
        return cloud;
    }

    //    private static List<Point3D> reconstructAll(ImageProcessor processor){
    //        //...
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
