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
 * с исправленной триангуляцией, и местом для BA-реализации.
 */
public class OpenCVSFMConstructor {
    static {
        System.load("E:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java4110.dll");
    }

    public static final List<Point3> debugPoints  = new ArrayList<>();
    public static final List<Point3> debugCameras = new ArrayList<>();

    private static Mat estimateCameraMatrix(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double f = 1.2 * Math.max(w, h);
        Mat K = Mat.eye(3,3,CvType.CV_64F);
        K.put(0,0,f);
        K.put(1,1,f);
        K.put(0,2,w/2.0);
        K.put(1,2,h/2.0);
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
        Mat Rt = Mat.zeros(3,4,CvType.CV_64F);
        R.copyTo(Rt.colRange(0,3));
        t.copyTo(Rt.col(3));
        Mat P = new Mat();
        Core.gemm(K, Rt, 1.0, new Mat(), 0.0, P);
        return P;
    }

    private static Point3 cameraCenter(Mat R, Mat t) {
        Mat Rt = new Mat(); Core.transpose(R, Rt);
        Mat c  = new Mat(); Core.gemm(Rt, t, -1.0, new Mat(), 0.0, c);
        return new Point3(c.get(0,0)[0], c.get(1,0)[0], c.get(2,0)[0]);
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
        Map<String,Point2D> map2 = pts2.stream()
                .collect(Collectors.toMap(Point2D::getName, Function.identity()));
        List<Point2D> common1 = new ArrayList<>(), common2 = new ArrayList<>();
        List<String> names    = new ArrayList<>();
        for (Point2D p : pts1) {
            Point2D q = map2.get(p.getName());
            if (q != null) {
                common1.add(p);
                common2.add(q);
                names.add(p.getName());
            }
        }
        if (common1.size() < 5) {
            throw new RuntimeException("Not enough correspondences");
        }

        // 3) Оценка K и Essential
        Mat K  = estimateCameraMatrix(proc.getImage(pair.getImage1()).getBufferedImage());
        double focal = K.get(0,0)[0];
        Point pr    = new Point(K.get(0,2)[0], K.get(1,2)[0]);

        MatOfPoint2f m1 = mat2f(common1);
        MatOfPoint2f m2 = mat2f(common2);

        Mat maskE = new Mat();
        Mat E = Calib3d.findEssentialMat(
                m1, m2,
                focal, pr,
                Calib3d.RANSAC,
                0.999,
                1.0,
                10000,
                maskE
        );

        Mat R = new Mat(), t = new Mat();
        Calib3d.recoverPose(E, m1, m2, K, R, t, maskE);

        // 4) Триангуляция CV_32F
        Mat P1 = buildProjection(K, Mat.eye(3,3,CvType.CV_64F), Mat.zeros(3,1,CvType.CV_64F));
        Mat P2 = buildProjection(K, R, t);
        Mat P1f = new Mat(), P2f = new Mat();
        P1.convertTo(P1f, CvType.CV_32F);
        P2.convertTo(P2f, CvType.CV_32F);

        MatOfPoint2f m1f = new MatOfPoint2f(), m2f = new MatOfPoint2f();
        m1.convertTo(m1f, CvType.CV_32F);
        m2.convertTo(m2f, CvType.CV_32F);

        Mat pts4d = new Mat();
        Calib3d.triangulatePoints(P1f, P2f, m1f, m2f, pts4d);

        Map<String,Point3D> cloudMap = new LinkedHashMap<>();
        for (int i = 0; i < pts4d.cols(); i++) {
            double w4 = pts4d.get(3,i)[0];
            double x  = pts4d.get(0,i)[0] / w4;
            double y  = pts4d.get(1,i)[0] / w4;
            double z  = pts4d.get(2,i)[0] / w4;
            cloudMap.put(names.get(i), new Point3D(names.get(i), x,y,z));
            debugPoints.add(new Point3(x,y,z));
        }
        debugCameras.add(new Point3(0,0,0));
        debugCameras.add(cameraCenter(R,t));

        // Здесь можно вызывать внешний BA (COLMAP) или оставить без BA,
        // т.к. в чистом org.opencv нет встроенного BundleAdjusterReproj.

        return new ArrayList<>(cloudMap.values());
    }

    private static ImagePair findBestPair(ImageProcessor proc) {
        ImagePair best = new ImagePair();
        List<String> keys = Collections.list(proc.getImagesModel().elements());
        int max = 0;
        for (int i = 0; i < keys.size()-1; i++) {
            proc.setActiveImage(keys.get(i));
            Set<String> A = proc.getActiveImagePoints().stream()
                    .map(Point2D::getName).collect(Collectors.toSet());
            for (int j = i+1; j < keys.size(); j++) {
                proc.setActiveImage(keys.get(j));
                Set<String> B = proc.getActiveImagePoints().stream()
                        .map(Point2D::getName).collect(Collectors.toSet());
                int cnt = 0;
                for (String n : A) if (B.contains(n)) cnt++;
                if (cnt > max) {
                    max = cnt;
                    best.setImage1(keys.get(i));
                    best.setImage2(keys.get(j));
                    best.setCor(cnt);
                }
            }
        }
        return best;
    }
}

@Data @AllArgsConstructor @NoArgsConstructor
class ImagePair {
    private String image1;
    private String image2;
    private int cor;
}
