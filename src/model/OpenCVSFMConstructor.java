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

/**
 * Класс для Incremental SFM без ручной нормализации,
 * с исправленной триангуляцией (унификация типов в CV_32F).
 */
public class OpenCVSFMConstructor {
    static {
        System.load("E:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java4110.dll");
    }

    public static final List<Point3> debugPoints = new ArrayList<>();
    public static final List<Point3> debugCameras = new ArrayList<>();

    private static Mat estimateCameraMatrix(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double f = 1.2 * Math.max(w, h);

        Mat K = Mat.eye(3, 3, CvType.CV_64F);
        K.put(0, 0, f);
        K.put(1, 1, f);
        K.put(0, 2, w / 2.0);
        K.put(1, 2, h / 2.0);
        return K;
    }

    private static MatOfPoint2f mat2f(List<Point2D> list) {
        Point[] arr = new Point[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Point2D p = list.get(i);
            arr[i] = new Point(p.getX(), p.getY());
        }
        return new MatOfPoint2f(arr);
    }

    private static Mat buildProjection(Mat K, Mat R, Mat t) {
        Mat Rt = new Mat(3, 4, CvType.CV_64F);
        R.copyTo(Rt.colRange(0, 3));
        t.copyTo(Rt.col(3));

        Mat P = new Mat();
        Core.gemm(K, Rt, 1, new Mat(), 0, P);
        return P;
    }

    private static Point3 cameraCenter(Mat R, Mat t) {
        Mat Rt = new Mat();
        Core.transpose(R, Rt);

        Mat c = new Mat();
        Core.gemm(Rt, t, -1, new Mat(), 0, c);
        double x = c.get(0, 0)[0];
        double y = c.get(1, 0)[0];
        double z = c.get(2, 0)[0];
        return new Point3(x, y, z);
    }

    public static List<Point3D> reconstructAll(ImageProcessor proc) {
        debugPoints.clear();
        debugCameras.clear();

        // 1) Выбор базовой пары
        ImagePair pair = findBestPair(proc);
        proc.setActiveImage(pair.getImage1());
        List<Point2D> pts1 = proc.getActiveImagePoints();
        proc.setActiveImage(pair.getImage2());
        List<Point2D> pts2 = proc.getActiveImagePoints();

        // 2) Поиск общих точек
        Map<String, Point2D> map2 = pts2.stream()
                .collect(Collectors.toMap(Point2D::getName, Function.identity()));

        List<Point2D> common1 = new ArrayList<>();
        List<Point2D> common2 = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Point2D p : pts1) {
            Point2D q = map2.get(p.getName());
            if (q != null) {
                common1.add(p);
                common2.add(q);
                names.add(p.getName());
            }
        }
        if (common1.size() < 5) {
            throw new RuntimeException("Not enough correspondences for initial pair");
        }

        // 3) Оценка K и Essential
        Mat K = estimateCameraMatrix(proc.getImage(pair.getImage1()).getBufferedImage());
        double focal = K.get(0, 0)[0];
        Point pp = new Point(K.get(0, 2)[0], K.get(1, 2)[0]);

        MatOfPoint2f m1 = mat2f(common1);
        MatOfPoint2f m2f = mat2f(common2);

        Mat maskE = new Mat();
        Mat E = Calib3d.findEssentialMat(
                m1, m2f,
                focal, pp,
                Calib3d.RANSAC,
                0.999,
                1.0,
                10000,
                maskE
        );

        Mat R = new Mat();
        Mat t = new Mat();
        Calib3d.recoverPose(E, m1, m2f, K, R, t, maskE);

        // 4) Триангуляция начального облака в CV_32F
        Mat P1 = buildProjection(K, Mat.eye(3, 3, CvType.CV_64F), new Mat(3, 1, CvType.CV_64F));
        Mat P2 = buildProjection(K, R, t);

        Mat P1_32 = new Mat();
        Mat P2_32 = new Mat();
        P1.convertTo(P1_32, CvType.CV_32F);
        P2.convertTo(P2_32, CvType.CV_32F);

        MatOfPoint2f m1f = new MatOfPoint2f();
        MatOfPoint2f m2f32 = new MatOfPoint2f();
        m1.convertTo(m1f, CvType.CV_32F);
        m2f.convertTo(m2f32, CvType.CV_32F);

        Mat pts4d = new Mat();
        Calib3d.triangulatePoints(P1_32, P2_32, m1f, m2f32, pts4d);

        Map<String, Point3D> cloudMap = new LinkedHashMap<>();
        for (int i = 0; i < pts4d.cols(); i++) {
            double w4 = pts4d.get(3, i)[0];
            double x = pts4d.get(0, i)[0] / w4;
            double y = pts4d.get(1, i)[0] / w4;
            double z = pts4d.get(2, i)[0] / w4;

            Point3D p3 = new Point3D(names.get(i), x, y, z);
            cloudMap.put(names.get(i), p3);
            debugPoints.add(new Point3(x, y, z));
        }
        debugCameras.add(new Point3(0, 0, 0));
        debugCameras.add(cameraCenter(R, t));

        // 5) Инкрементальная оценка PnP + триангуляция новых точек
        List<String> keys = Collections.list(proc.getImagesModel().elements());
        for (String key : keys) {
            if (key.equals(pair.getImage1()) || key.equals(pair.getImage2())) {
                continue;
            }

            proc.setActiveImage(key);

            // --- Сбор соответствий 3D->2D ---
            List<Point3> objPts = new ArrayList<>();
            List<Point> imgPts = new ArrayList<>();
            List<String> trackNames = new ArrayList<>();

            for (Map.Entry<String, Point3D> entry : cloudMap.entrySet()) {
                String nm = entry.getKey();
                for (Point2D ip : proc.getActiveImagePoints()) {
                    if (ip.getName().equals(nm)) {
                        Point3D cp = entry.getValue();
                        objPts.add(new Point3(cp.getX(), cp.getY(), cp.getZ()));
                        imgPts.add(new Point(ip.getX(), ip.getY()));
                        trackNames.add(nm);
                        break;
                    }
                }
            }

            if (objPts.size() < 4) continue;

            Mat rvec = new Mat();
            Mat tvec = new Mat();
            Calib3d.solvePnPRansac(
                    new MatOfPoint3f(objPts.toArray(new Point3[0])),
                    new MatOfPoint2f(imgPts.toArray(new Point[0])),
                    K,
                    new MatOfDouble(),
                    rvec,
                    tvec
            );

            Mat Rn = new Mat();
            Calib3d.Rodrigues(rvec, Rn);
            debugCameras.add(cameraCenter(Rn, tvec));

            // --- Триангуляция новых точек ---
            Mat Pn = buildProjection(K, Rn, tvec);
            Mat Pn_32 = new Mat();
            Pn.convertTo(Pn_32, CvType.CV_32F);

            List<Point2D> firstPts = new ArrayList<>();
            List<Point2D> currPts = new ArrayList<>();
            List<String> newNames = new ArrayList<>();

            for (Point2D pt : proc.getActiveImagePoints()) {
                if (!cloudMap.containsKey(pt.getName())) {
                    for (Point2D p0 : common1) {  // common1 из начала метода
                        if (p0.getName().equals(pt.getName())) {
                            firstPts.add(p0);
                            currPts.add(pt);
                            newNames.add(pt.getName());
                            break;
                        }
                    }
                }
            }

            if (firstPts.size() >= 4) {
                MatOfPoint2f fp32 = new MatOfPoint2f();
                MatOfPoint2f cp32 = new MatOfPoint2f();
                mat2f(firstPts).convertTo(fp32, CvType.CV_32F);
                mat2f(currPts).convertTo(cp32, CvType.CV_32F);

                Mat new4d = new Mat();
                Calib3d.triangulatePoints(
                        P1_32,
                        Pn_32,
                        fp32,
                        cp32,
                        new4d
                );

                for (int i = 0; i < new4d.cols(); i++) {
                    double w4 = new4d.get(3, i)[0];
                    double x = new4d.get(0, i)[0] / w4;
                    double y = new4d.get(1, i)[0] / w4;
                    double z = new4d.get(2, i)[0] / w4;

                    String nm = newNames.get(i);
                    Point3D p3 = new Point3D(nm, x, y, z);
                    cloudMap.put(nm, p3);
                    debugPoints.add(new Point3(x, y, z));
                }
            }
        }

        return new ArrayList<>(cloudMap.values());
    }

    private static ImagePair findBestPair(ImageProcessor proc) {
        ImagePair best = new ImagePair();
        List<String> keys = Collections.list(proc.getImagesModel().elements());
        int maxMatches = 0;

        for (int i = 0; i < keys.size() - 1; i++) {
            proc.setActiveImage(keys.get(i));
            Set<String> A = proc.getActiveImagePoints().stream()
                    .map(Point2D::getName)
                    .collect(Collectors.toSet());

            for (int j = i + 1; j < keys.size(); j++) {
                proc.setActiveImage(keys.get(j));
                Set<String> B = proc.getActiveImagePoints().stream()
                        .map(Point2D::getName)
                        .collect(Collectors.toSet());

                int count = 0;
                for (String n : A) {
                    if (B.contains(n)) count++;
                }

                if (count > maxMatches) {
                    maxMatches = count;
                    best.setImage1(keys.get(i));
                    best.setImage2(keys.get(j));
                    best.setCor(count);
                }
            }
        }

        return best;
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class ImagePair {
    private String image1;
    private String image2;
    private int cor;
}
