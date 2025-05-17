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
import java.util.Arrays;

/**
 * OpenCVSFMConstructor — Incremental SFM pipeline
 * with optional Bundle Adjustment (skipped if insufficient matches).
 */
public class OpenCVSFMConstructor {
    static {
        // Load OpenCV JNI
        System.load("E:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java4110.dll");
    }

    /**
     * Reconstructs a 3D point cloud from the best pair of images.
     * Bundle Adjustment is applied only if there are enough matches.
     */
    public static List<Point3D> reconstructAll(ImageProcessor proc) {
        // 1) Select best image pair
        ImagePair pair = findBestPair(proc);
        proc.setActiveImage(pair.getImage1());
        List<Point2D> pts1 = proc.getActiveImagePoints();
        proc.setActiveImage(pair.getImage2());
        List<Point2D> pts2 = proc.getActiveImagePoints();

        // 2) Find common annotations
        Map<String, Point2D> map2 = pts2.stream()
                .collect(Collectors.toMap(Point2D::getName, Function.identity()));
        List<Point2D> common1 = new ArrayList<>(), common2 = new ArrayList<>();
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
            throw new RuntimeException("Недостаточно соответствий для стартовой пары");
        }

        // 3) Estimate Essential matrix and recover relative pose
        BufferedImage imgA = proc.getImage(pair.getImage1()).getBufferedImage();
        Mat K = estimateCameraMatrix(imgA);
        double f = K.get(0, 0)[0];
        Point pp = new Point(K.get(0, 2)[0], K.get(1, 2)[0]);
        MatOfPoint2f m1 = mat2f(common1), m2 = mat2f(common2);
        Mat maskE = new Mat();
        Mat E = Calib3d.findEssentialMat(
                m1, m2, f, pp,
                Calib3d.RANSAC, 0.999, 1.0, 10000, maskE
        );
        Mat R = new Mat(), t = new Mat();
        Calib3d.recoverPose(E, m1, m2, K, R, t, maskE);

        // 4) Initial triangulation
        Mat P1 = buildProjection(K, Mat.eye(3,3,CvType.CV_64F), Mat.zeros(3,1,CvType.CV_64F));
        Mat P2 = buildProjection(K, R, t);
        Mat P1f = new Mat(), P2f = new Mat();
        P1.convertTo(P1f, CvType.CV_32F);
        P2.convertTo(P2f, CvType.CV_32F);
        MatOfPoint2f m1f = new MatOfPoint2f(), m2f32 = new MatOfPoint2f();
        m1.convertTo(m1f, CvType.CV_32F);
        m2.convertTo(m2f32, CvType.CV_32F);
        Mat pts4d = new Mat();
        Calib3d.triangulatePoints(P1f, P2f, m1f, m2f32, pts4d);

        // Build initial 3D cloud
        Map<String, Point3D> cloudMap = new LinkedHashMap<>();
        for (int i = 0; i < pts4d.cols(); i++) {
            double w4 = pts4d.get(3, i)[0];
            double x = pts4d.get(0, i)[0] / w4;
            double y = pts4d.get(1, i)[0] / w4;
            double z = pts4d.get(2, i)[0] / w4;
            cloudMap.put(names.get(i), new Point3D(names.get(i), x, y, z));
        }

        // 5) Optionally skip BA if too few matches
        int C = 2; // number of cameras to optimize
        int N = cloudMap.size();
        int minMatches = (int)Math.floor((6.0 * C) / (2.0 * C - 3.0)) + 1;
        if (N < minMatches) {
            // Return the unadjusted point cloud
            return new ArrayList<>(cloudMap.values());
        }

        // 6) Bundle Adjustment with two cameras
        Mat I = Mat.eye(3,3,CvType.CV_64F);
        Mat zero = Mat.zeros(3,1,CvType.CV_64F);
        List<Mat> Rs = Arrays.asList(I, R);
        List<Mat> Ts = Arrays.asList(zero, t);
        List<List<Point2D>> observations = Arrays.asList(common1, common2);
        List<List<String>> obsNames = Arrays.asList(names, names);

        BundleAdjuster ba = new BundleAdjuster(
                cloudMap, Rs, Ts,
                observations, obsNames,
                K
        );
        ba.optimize();
        ba.updateCloudMap(cloudMap);

        return new ArrayList<>(cloudMap.values());
    }

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

    private static ImagePair findBestPair(ImageProcessor proc) {
        ImagePair best = new ImagePair();
        List<String> keys = Collections.list(proc.getImagesModel().elements());
        int max = 0;
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