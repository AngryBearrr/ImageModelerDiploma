package model;

import org.opencv.core.*;
import org.opencv.calib3d.Calib3d;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collections;

import org.opencv.core.Point;


public class OpenCVSFMConstructor {
    static {
        System.load("E:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java4110.dll");
    }



    /** Generic pair of values */
    public static class Pair<T, U> {
        public final T first;
        public final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }

    /** Display all images from processor with points overlay */
    public static void displayAllImages(ImageProcessor processor) {
        String oldKey = null;
        if (processor.activeImageExists()) {
            oldKey = processor.getActiveImagePath();
        }
        List<String> keys = Collections.list(processor.getImagesModel().elements());
        for (String key : keys) {
            processor.setActiveImage(key);
            BufferedImage img = processor.getActiveImage();
            BufferedImage copy = deepCopy(img);
            Graphics2D g2d = copy.createGraphics();
            g2d.setColor(Color.RED);
            int r = 6;
            for (Point2D p : processor.getActiveImagePoints()) {
                int x = (int) Math.round(p.getX() - r / 2.0);
                int y = (int) Math.round(p.getY() - r / 2.0);
                g2d.fillOval(x, y, r, r);
            }
            g2d.dispose();
            JFrame frame = new JFrame("Image: " + key);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.add(new JLabel(new ImageIcon(copy)));
            frame.pack();
            frame.setVisible(true);
        }
        if (oldKey != null) {
            processor.setActiveImage(oldKey);
        }
    }

    private static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    /**
     * Finds the pair of images with the maximum number of inliers when computing the fundamental matrix.
     */
    public static Pair<String, String> findBestPair(ImageProcessor proc) {
        String oldKey = null;
        if (proc.activeImageExists()) {
            oldKey = proc.getActiveImagePath();
        }
        String bestA = null, bestB = null;
        int bestInliers = 0;
        List<String> keys = Collections.list(proc.getImagesModel().elements());
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                String a = keys.get(i);
                String b = keys.get(j);
                proc.setActiveImage(a);
                List<Point2D> pa = proc.getActiveImagePoints();
                proc.setActiveImage(b);
                List<Point2D> pb = proc.getActiveImagePoints();

                Map<String, Point2D> mapB = new HashMap<>();
                for (Point2D p : pb) {
                    mapB.put(p.getName(), p);
                }
                List<Point> listA = new ArrayList<>();
                List<Point> listB = new ArrayList<>();
                for (Point2D p : pa) {
                    Point2D q = mapB.get(p.getName());
                    if (q != null) {
                        listA.add(new Point(p.getX(), p.getY()));
                        listB.add(new Point(q.getX(), q.getY()));
                    }
                }
                if (listA.size() >= 8) {
                    MatOfPoint2f ma = new MatOfPoint2f();
                    ma.fromList(listA);
                    MatOfPoint2f mb = new MatOfPoint2f();
                    mb.fromList(listB);
                    Mat mask = new Mat();
                    Calib3d.findFundamentalMat(ma, mb, Calib3d.FM_RANSAC, 3, 0.99, mask);
                    int inliers = Core.countNonZero(mask);
                    if (inliers > bestInliers) {
                        bestInliers = inliers;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
        }
        if (oldKey != null) {
            proc.setActiveImage(oldKey);
        }
        return new Pair<>(bestA, bestB);
    }

    /**
     * Estimate camera intrinsic matrix K: focal = (w+h)/2, principal = (w/2,h/2), no distortion.
     */
    public static Mat estimateCameraMatrix(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double f = (w + h) / 2.0;
        Mat K = Mat.eye(3, 3, CvType.CV_64F);
        K.put(0, 0, f);
        K.put(1, 1, f);
        K.put(0, 2, w / 2.0);
        K.put(1, 2, h / 2.0);
        return K;
    }

    public static Mat estimateDistCoeffs() {
        return Mat.zeros(5, 1, CvType.CV_64F);
    }

    /**
     * Normalize points: (x-cx)/fx, (y-cy)/fy
     */
    public static MatOfPoint2f normalizePoints(List<Point2D> pts, Mat K) {
        double fx = K.get(0, 0)[0];
        double fy = K.get(1, 1)[0];
        double cx = K.get(0, 2)[0];
        double cy = K.get(1, 2)[0];
        List<Point> norm = new ArrayList<>();
        for (Point2D p : pts) {
            double nx = (p.getX() - cx) / fx;
            double ny = (p.getY() - cy) / fy;
            norm.add(new Point(nx, ny));
        }
        MatOfPoint2f mp = new MatOfPoint2f();
        mp.fromList(norm);
        return mp;
    }

    /**
     * Full two-view reconstruction: returns 3D cloud from best image pair
     */
    public static List<Point3D> reconstructGeneral(ImageProcessor proc) {
        Pair<String, String> pair = findBestPair(proc);
        if (pair.first == null || pair.second == null) {
            throw new RuntimeException("No valid image pair found");
        }
        String keyA = pair.first;
        String keyB = pair.second;
        proc.setActiveImage(keyA);
        BufferedImage img1 = proc.getActiveImage();
        proc.setActiveImage(keyB);
        BufferedImage img2 = proc.getActiveImage();

        Mat K1 = estimateCameraMatrix(img1);
        Mat K2 = estimateCameraMatrix(img2);
        Mat d1 = estimateDistCoeffs();
        Mat d2 = estimateDistCoeffs();

        proc.setActiveImage(keyA);
        List<Point2D> pa = proc.getActiveImagePoints();
        proc.setActiveImage(keyB);
        List<Point2D> pb = proc.getActiveImagePoints();

        // build correspondence lists
        Map<String, Point2D> mapB = new HashMap<>();
        for (Point2D p : pb) mapB.put(p.getName(), p);
        List<String> corrNames = new ArrayList<>();
        List<Point> listA = new ArrayList<>();
        List<Point> listB = new ArrayList<>();
        for (Point2D p : pa) {
            Point2D q = mapB.get(p.getName());
            if (q != null) {
                corrNames.add(p.getName());
                listA.add(new Point(p.getX(), p.getY()));
                listB.add(new Point(q.getX(), q.getY()));
            }
        }
        if (listA.size() < 5) {
            throw new RuntimeException("Not enough correspondences");
        }

        MatOfPoint2f ma = new MatOfPoint2f(); ma.fromList(listA);
        MatOfPoint2f mb = new MatOfPoint2f(); mb.fromList(listB);
        // normalize
        MatOfPoint2f n1 = normalizePointsFromList(listA, K1);
        MatOfPoint2f n2 = normalizePointsFromList(listB, K2);

        Mat E = Calib3d.findEssentialMat(n1, n2);
        Mat R = new Mat(), T = new Mat(), mask = new Mat();
        Calib3d.recoverPose(E, n1, n2, R, T, mask);

        // projection matrices
        Mat P1 = Mat.eye(3, 4, CvType.CV_64F);
        Mat P2 = Mat.zeros(3, 4, CvType.CV_64F);
        R.copyTo(P2.colRange(0, 3));
        T.copyTo(P2.col(3));

        // triangulate
        Mat points4D = new Mat();
        Calib3d.triangulatePoints(P1, P2, n1, n2, points4D);

        List<Point3D> cloud = new ArrayList<>();
        for (int i = 0; i < points4D.cols(); i++) {
            double w = points4D.get(3, i)[0];
            double x = points4D.get(0, i)[0] / w;
            double y = points4D.get(1, i)[0] / w;
            double z = points4D.get(2, i)[0] / w;
            cloud.add(new Point3D(corrNames.get(i), x, y, z));
        }
        return cloud;
    }

    /**
     * Incremental reconstruction across all images.
     */
    public static List<Point3D> reconstructAll(ImageProcessor proc) {
        // 1) находим пару для инициализации
        Pair<String, String> initPair = findBestPair(proc);
        if (initPair.first == null || initPair.second == null) {
            throw new RuntimeException("No valid initial image pair");
        }
        String refKey  = initPair.first;
        String nextKey = initPair.second;

        // 2) получаем начальное облако 3D-точек
        List<Point3D> initialCloud = reconstructGeneral(proc);

        // 3) строим map: имя → 3D-точка
        Map<String, Point3D> cloudMap = new LinkedHashMap<>();
        for (Point3D p3 : initialCloud) {
            cloudMap.put(p3.getName(), p3);
        }

        // 4) параметры референсной камеры
        proc.setActiveImage(refKey);
        Mat Kref            = estimateCameraMatrix(proc.getActiveImage());
        MatOfDouble distRef = new MatOfDouble(0, 0, 0, 0, 0);  // нулевые коэффициенты
        Mat    Rref         = Mat.eye(3, 3, CvType.CV_64F);
        Mat    Tref         = Mat.zeros(3, 1, CvType.CV_64F);

        // 5) обход остальных изображений
        List<String> keys = Collections.list(proc.getImagesModel().elements());
        for (String key : keys) {
            if (key.equals(refKey) || key.equals(nextKey)) continue;

            // 5.1) готовим параметры текущей камеры
            proc.setActiveImage(key);
            BufferedImage img   = proc.getActiveImage();
            Mat           Kcurr = estimateCameraMatrix(img);
            MatOfDouble   distCurr = new MatOfDouble(0, 0, 0, 0, 0);

            // 5.2) собираем 3D→2D соответствия
            List<Point3>    objPts = new ArrayList<>();
            List<Point>     imgPts = new ArrayList<>();
            for (Point2D p2 : proc.getActiveImagePoints()) {
                Point3D p3 = cloudMap.get(p2.getName());
                if (p3 != null) {
                    objPts.add(new Point3(p3.getX(), p3.getY(), p3.getZ()));
                    imgPts.add(new Point(p2.getX(), p2.getY()));
                }
            }
            if (objPts.size() < 6) {
                continue;  // недостаточно точек для PnP
            }

            // 5.3) конвертируем в MatOfPoint3f и MatOfPoint2f
            MatOfPoint3f objectMat = new MatOfPoint3f();
            objectMat.fromList(objPts);

            MatOfPoint2f imageMat = new MatOfPoint2f();
            imageMat.fromList(imgPts);

            // 5.4) решаем PnP
            Mat rvec = new Mat(), tvec = new Mat();
            Calib3d.solvePnP(
                    objectMat,
                    imageMat,
                    Kcurr,
                    distCurr,
                    rvec,
                    tvec
            );

            // 5.5) извлекаем R и P матрицы
            Mat Rcurr = new Mat();
            Calib3d.Rodrigues(rvec, Rcurr);
            Mat Pcurr = concatProjection(Rcurr, tvec);

            // 5.6) триангулируем новые точки против референсной камеры
            proc.setActiveImage(refKey);
            List<Point2D> prefPts = proc.getActiveImagePoints();
            proc.setActiveImage(key);
            List<Point2D> currPts = proc.getActiveImagePoints();

            List<String>   newNames = new ArrayList<>();
            List<Point>    pRef     = new ArrayList<>();
            List<Point>    pCurr    = new ArrayList<>();
            Map<String,Point2D> mapCurr = new java.util.HashMap<>();
            for (Point2D p : currPts) {
                mapCurr.put(p.getName(), p);
            }
            for (Point2D p : prefPts) {
                if (!cloudMap.containsKey(p.getName())) {
                    Point2D q = mapCurr.get(p.getName());
                    if (q != null) {
                        newNames.add(p.getName());
                        pRef.add(new Point(p.getX(), p.getY()));
                        pCurr.add(new Point(q.getX(), q.getY()));
                    }
                }
            }
            if (newNames.size() < 8) {
                continue;
            }

            MatOfPoint2f nRef  = normalizePointsFromList(pRef,  Kref);
            MatOfPoint2f nCurr = normalizePointsFromList(pCurr, Kcurr);
            Mat         pts4D = new Mat();
            Calib3d.triangulatePoints(
                    concatProjection(Rref, Tref),
                    Pcurr,
                    nRef, nCurr,
                    pts4D
            );

            // 5.7) добавляем новые 3D-точки в облако
            for (int i = 0; i < pts4D.cols(); i++) {
                double w = pts4D.get(3, i)[0];
                double x = pts4D.get(0, i)[0] / w;
                double y = pts4D.get(1, i)[0] / w;
                double z = pts4D.get(2, i)[0] / w;
                cloudMap.put(newNames.get(i), new Point3D(newNames.get(i), x, y, z));
            }
        }

        return new ArrayList<>(cloudMap.values());
    }

    // Helper: normalize from Point lists
    private static MatOfPoint2f normalizePointsFromList(List<Point> pts, Mat K) {
        double fx = K.get(0,0)[0], fy = K.get(1,1)[0], cx = K.get(0,2)[0], cy = K.get(1,2)[0];
        List<Point> norm = new ArrayList<>();
        for (Point p: pts) norm.add(new Point((p.x-cx)/fx, (p.y-cy)/fy));
        MatOfPoint2f mp = new MatOfPoint2f(); mp.fromList(norm); return mp;
    }

    // Helper: build projection from R and T
    private static Mat concatProjection(Mat R, Mat t) {
        Mat P = Mat.zeros(3,4,CvType.CV_64F);
        R.copyTo(P.colRange(0,3));
        t.copyTo(P.col(3));
        return P;
    }
}