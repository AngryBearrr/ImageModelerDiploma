package model;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.calib3d.Calib3d;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import org.opencv.core.*;

public class SFMConstructor {
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    /** Пара значений generic */
    public static class Pair<T,U> {
        public final T first;
        public final U second;
        public Pair(T first, U second) { this.first = first; this.second = second; }
    }

    /** Отображает все изображения из processor с отрисовкой точек поверх них */
    public static void displayAllImages(ImageProcessor processor) {
        for (String key : Collections.list(processor.getImagesModel().elements())) {
            BufferedImage img = processor.getImage(key);
            BufferedImage copy = deepCopy(img);
            Graphics2D g2d = copy.createGraphics();
            g2d.setColor(Color.RED);
            int r = 6;
            for (Point2D p : processor.getActiveImagePoints(key)) {
                g2d.fillOval(p.getX() - r/2, p.getY() - r/2, r, r);
            }
            g2d.dispose();
            JFrame frame = new JFrame("Image: " + key);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.add(new JLabel(new ImageIcon(copy)));
            frame.pack();
            frame.setVisible(true);
        }
    }

    private static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    /**
     * Находит пару изображений с максимальным числом инлайеров при расчёте фундаментальной матрицы.
     */
    public static Pair<String,String> findBestPair(ImageProcessor proc) {
        String bestA = null, bestB = null;
        int bestInliers = 0;
        List<String> keys = Collections.list(proc.getImagesModel().elements());
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i+1; j < keys.size(); j++) {
                String a = keys.get(i), b = keys.get(j);
                List<Point2D> pa = proc.getActiveImagePoints(a);
                List<Point2D> pb = proc.getActiveImagePoints(b);
                Map<String,Point2D> mapB = new HashMap<>();
                for (Point2D p : pb) mapB.put(p.getName(), p);
                List<Point> listA = new ArrayList<>(), listB = new ArrayList<>();
                for (Point2D p : pa) {
                    Point2D q = mapB.get(p.getName());
                    if (q != null) {
                        listA.add(new Point(p.getX(), p.getY()));
                        listB.add(new Point(q.getX(), q.getY()));
                    }
                }
                if (listA.size() >= 8) {
                    MatOfPoint2f ma = new MatOfPoint2f(); ma.fromList(listA);
                    MatOfPoint2f mb = new MatOfPoint2f(); mb.fromList(listB);
                    Mat mask = new Mat();
                    Calib3d.findFundamentalMat(ma, mb,
                            Calib3d.FM_RANSAC, 3, 0.99, mask);
                    int inliers = Core.countNonZero(mask);
                    if (inliers > bestInliers) {
                        bestInliers = inliers;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
        }
        return new Pair<>(bestA, bestB);
    }

    /**
     * Оценка K: focal=(w+h)/2, principal=(w/2,h/2), без искажений.
     */
    public static Mat estimateCameraMatrix(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double f = (w + h) / 2.0;
        Mat K = Mat.eye(3, 3, CvType.CV_64F);
        K.put(0, 0, f); K.put(1, 1, f);
        K.put(0, 2, w/2.0); K.put(1, 2, h/2.0);
        return K;
    }

    public static Mat estimateDistCoeffs() {
        return Mat.zeros(5, 1, CvType.CV_64F);
    }

    /**
     * Нормализация точек: (x-cx)/fx, (y-cy)/fy
     */
    public static MatOfPoint2f normalizePoints(List<Point2D> pts, Mat K) {
        double fx = K.get(0,0)[0], fy = K.get(1,1)[0];
        double cx = K.get(0,2)[0], cy = K.get(1,2)[0];
        List<Point> norm = new ArrayList<>();
        for (Point2D p : pts) {
            double nx = (p.getX() - cx) / fx;
            double ny = (p.getY() - cy) / fy;
            norm.add(new Point(nx, ny));
        }
        MatOfPoint2f mp = new MatOfPoint2f(); mp.fromList(norm);
        return mp;
    }

    /**
     * Полный конвейер: выбор лучшей пары, оценка K/dist, нормализация,
     * восстановление позы, триангуляция и возврат 3D-точек.
     */
    public static List<Point3D> reconstructGeneral(ImageProcessor proc) {
        Pair<String,String> pair = findBestPair(proc);
        if (pair.first == null) throw new RuntimeException("No valid image pair found");

        BufferedImage img1 = proc.getImage(pair.first);
        BufferedImage img2 = proc.getImage(pair.second);
        Mat K1 = estimateCameraMatrix(img1), K2 = estimateCameraMatrix(img2);
        Mat d1 = estimateDistCoeffs(), d2 = estimateDistCoeffs();

        List<Point2D> pts1 = proc.getActiveImagePoints(pair.first);
        List<Point2D> pts2 = proc.getActiveImagePoints(pair.second);
        MatOfPoint2f n1 = normalizePoints(pts1, K1);
        MatOfPoint2f n2 = normalizePoints(pts2, K2);

        // Восстановление позы
        Mat E = Calib3d.findEssentialMat(n1, n2);
        Mat R = new Mat(), T = new Mat(), mask = new Mat();
        Calib3d.recoverPose(E, n1, n2, R, T, mask);

        // Проекционные матрицы
        Mat P1 = Mat.eye(3, 4, CvType.CV_64F);
        Mat P2 = Mat.zeros(3, 4, CvType.CV_64F);
        R.copyTo(P2.colRange(0,3)); T.copyTo(P2.col(3));

        // Триангуляция
        Mat points4D = new Mat();
        Calib3d.triangulatePoints(P1, P2, n1, n2, points4D);

        // Конвертация в List<Point3D>
        List<Point3D> cloud = new ArrayList<>();
        for (int i = 0; i < points4D.cols(); i++) {
            double w = points4D.get(3, i)[0];
            double x = points4D.get(0, i)[0] / w;
            double y = points4D.get(1, i)[0] / w;
            double z = points4D.get(2, i)[0] / w;
            cloud.add(new Point3D(x, y, z));
        }
        return cloud;
    }
}
