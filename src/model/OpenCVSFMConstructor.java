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
 * OpenCVSFMConstructor — Incremental SfM pipeline
 * with reprojection error minimization and multi-view reconstruction.
 * All configuration and methods are static.
 */
public class OpenCVSFMConstructor {
    static {
        // Load OpenCV JNI
        System.load("E:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java4110.dll");
    }

    // Static fields for global transform (applied after reconstruction)
    private static Mat globalR = Mat.eye(3, 3, CvType.CV_64F);
    private static Mat globalT = Mat.zeros(3, 1, CvType.CV_64F);

    // Configuration parameters
    private static final double MAX_REPROJECTION_ERROR = 12.0; // pixels
    private static final double MIN_TRIANGULATION_ANGLE_DEG = 3.0; // degrees
    private static final double MIN_TRIANGULATION_ANGLE_RAD = Math.toRadians(MIN_TRIANGULATION_ANGLE_DEG);
    private static final boolean ENABLE_GLOBAL_BA = true;
    private static final int MIN_POINTS_FOR_RESECTION = 6;
    private static final int MIN_INLIERS_FOR_CAMERA = 6;
    private static final int MIN_COMMON_POINTS = 5;
    private static int MAX_EVALUATIONS = 50;
    private static int MAX_ITERATIONS  = 50;

    public static void setGlobalRotation(Mat R) {
        globalR = R;
    }

    public static void setGlobalTranslation(Mat t) {
        globalT = t;
    }

    public static List<Point3D> reconstructAll(ImageProcessor proc) {
        // Gather all images
        @SuppressWarnings("unchecked")
        Enumeration<String> elems = (Enumeration<String>) proc.getImagesModel().elements();
        List<String> allImages = Collections.list(elems);
        if (allImages.size() < 2) {
            throw new RuntimeException("At least 2 images are required for reconstruction");
        }

        // Collect 2D correspondences
        Map<String, Map<String, Point2D>> pointsByImage = new HashMap<>();
        for (String imageName : allImages) {
            proc.setActiveImage(imageName);
            Map<String, Point2D> pointMap = proc.getActiveImagePoints().stream()
                    .collect(Collectors.toMap(Point2D::getName, Function.identity()));
            pointsByImage.put(imageName, pointMap);
        }

        // 1) Initial pair
        ImagePair bestPair = findBestPair(proc, allImages, pointsByImage);
        System.out.println("Best pair: " + bestPair.getImage1() + " <-> " + bestPair.getImage2()
                + " with " + bestPair.getCor() + " correspondences");

        // 2) Intrinsics
        BufferedImage img = proc.getImage(bestPair.getImage1()).getBufferedImage();
        Mat K = estimateCameraMatrix(img);

        // 3) Reconstruct pair
        Reconstruction recon = new Reconstruction(K);
        initializeFromPair(recon, bestPair, pointsByImage, proc);

        Set<String> reconstructedImages = new HashSet<>();
        reconstructedImages.add(bestPair.getImage1());
        reconstructedImages.add(bestPair.getImage2());

        // 4) Add others
        List<String> remaining = new ArrayList<>(allImages);
        remaining.removeAll(reconstructedImages);
        while (!remaining.isEmpty()) {
            ImageScore next = findBestImageToAdd(remaining, pointsByImage, recon);
            if (next == null || next.getNumMatches() < MIN_POINTS_FOR_RESECTION) {
                System.out.println("No more images with sufficient matches");
                break;
            }
            String imgName = next.getImageName();
            System.out.println("Adding image: " + imgName + " (" + next.getNumMatches() + " matches)");
            boolean ok = registerNewImage(recon, imgName, pointsByImage.get(imgName));
            if (ok) {
                reconstructedImages.add(imgName);
                triangulateNewPoints(recon, imgName, pointsByImage, reconstructedImages);
                if (ENABLE_GLOBAL_BA) {
                    performGlobalBA(recon, pointsByImage, reconstructedImages);
                }
            } else {
                System.out.println("Failed to register image: " + imgName);
            }
            remaining.remove(imgName);
        }

        // 5) Final BA
        performGlobalBA(recon, pointsByImage, reconstructedImages);

        // 6) Global transform
        Map<String, Point3D> finalCloud = recon.getPointCloud();
        applyGlobalTransform(finalCloud);
        return new ArrayList<>(finalCloud.values());
    }

    private static void initializeFromPair(Reconstruction recon, ImagePair pair,
                                           Map<String, Map<String, Point2D>> pointsByImage,
                                           ImageProcessor proc) {
        String img1 = pair.getImage1(), img2 = pair.getImage2();
        Map<String, Point2D> pts1 = pointsByImage.get(img1);
        Map<String, Point2D> pts2 = pointsByImage.get(img2);

        Set<String> common = new HashSet<>(pts1.keySet());
        common.retainAll(pts2.keySet());
        if (common.size() < MIN_COMMON_POINTS) {
            throw new RuntimeException("Insufficient common points");
        }

        List<Point2D> c1 = new ArrayList<>(), c2 = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String n : common) {
            c1.add(pts1.get(n)); c2.add(pts2.get(n)); names.add(n);
        }

        double f = recon.getK().get(0,0)[0];
        Point pp = new Point(recon.getK().get(0,2)[0], recon.getK().get(1,2)[0]);
        MatOfPoint2f m1 = mat2f(c1), m2 = mat2f(c2);

        Mat maskE = new Mat();
        Mat E = Calib3d.findEssentialMat(m1, m2, f, pp,
                Calib3d.RANSAC, 0.99, MAX_REPROJECTION_ERROR, 10000, maskE);

        boolean[] inlierMask = new boolean[maskE.rows()];
        for (int i = 0; i < maskE.rows(); i++) inlierMask[i] = maskE.get(i,0)[0] > 0;

        List<Point2D> in1 = new ArrayList<>(), in2 = new ArrayList<>();
        List<String> inNames = new ArrayList<>();
        for (int i = 0; i < c1.size(); i++) {
            if (inlierMask[i]) {
                in1.add(c1.get(i)); in2.add(c2.get(i)); inNames.add(names.get(i));
            }
        }

        Mat R = new Mat(), t = new Mat();
        int inliers = Calib3d.recoverPose(E, mat2f(in1), mat2f(in2), recon.getK(), R, t);
        maskE.release(); E.release();
        if (inliers < MIN_INLIERS_FOR_CAMERA) {
            throw new RuntimeException("Too few inliers in recoverPose");
        }

        recon.addCamera(img1, Mat.eye(3,3,CvType.CV_64F), Mat.zeros(3,1,CvType.CV_64F));
        recon.addCamera(img2, R.clone(), t.clone());

        Mat P1 = buildProjection(recon.getK(), recon.getCameraRotation(img1), recon.getCameraTranslation(img1));
        Mat P2 = buildProjection(recon.getK(), recon.getCameraRotation(img2), recon.getCameraTranslation(img2));
        Mat P1f = new Mat(), P2f = new Mat();
        P1.convertTo(P1f, CvType.CV_32F); P2.convertTo(P2f, CvType.CV_32F);

        MatOfPoint2f i1f = new MatOfPoint2f(), i2f = new MatOfPoint2f();
        mat2f(in1).convertTo(i1f, CvType.CV_32F);
        mat2f(in2).convertTo(i2f, CvType.CV_32F);

        Mat pts4d = new Mat();
        Calib3d.triangulatePoints(P1f,P2f,i1f,i2f,pts4d);

        for (int i = 0; i < pts4d.cols(); i++) {
            double w = pts4d.get(3,i)[0];
            double x = pts4d.get(0,i)[0]/w;
            double y = pts4d.get(1,i)[0]/w;
            double z = pts4d.get(2,i)[0]/w;
            Point3D p3d = new Point3D(inNames.get(i), x,y,z);
            double e1 = computeReprojectionError(p3d, in1.get(i), P1);
            double e2 = computeReprojectionError(p3d, in2.get(i), P2);
            if (e1 < MAX_REPROJECTION_ERROR && e2 < MAX_REPROJECTION_ERROR) {
                recon.addPoint(inNames.get(i), p3d);
                recon.addObservation(inNames.get(i), img1, in1.get(i));
                recon.addObservation(inNames.get(i), img2, in2.get(i));
            }
        }
        pts4d.release(); P1f.release(); P2f.release(); i1f.release(); i2f.release();
        System.out.println("Initial reconstruction: " + recon.getPointCloud().size() + " points");
    }

    private static ImageScore findBestImageToAdd(List<String> remaining,
                                                 Map<String, Map<String, Point2D>> pointsByImage,
                                                 Reconstruction recon) {
        ImageScore best = null;
        for (String img : remaining) {
            Map<String, Point2D> pts = pointsByImage.get(img);
            if (pts == null) continue;
            int matches = 0;
            for (String name : pts.keySet()) if (recon.hasPoint(name)) matches++;
            if (matches >= MIN_POINTS_FOR_RESECTION &&
                    (best==null||matches>best.getNumMatches())) {
                best = new ImageScore(img, matches);
            }
        }
        return best;
    }

    private static boolean registerNewImage(Reconstruction recon, String imageName,
                                            Map<String, Point2D> imagePoints) {
        if (imagePoints == null || imagePoints.size() < MIN_POINTS_FOR_RESECTION) {
            return false;
        }
        List<Point3D> pts3D = new ArrayList<>();
        List<Point2D> pts2D = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Point2D> e : imagePoints.entrySet()) {
            if (recon.hasPoint(e.getKey())) {
                pts3D.add(recon.getPoint(e.getKey()));
                pts2D.add(e.getValue());
                names.add(e.getKey());
            }
        }
        if (pts3D.size() < MIN_POINTS_FOR_RESECTION) return false;

        Point3[] objArr = new Point3[pts3D.size()];
        for (int i=0;i<pts3D.size();i++) {
            Point3D p = pts3D.get(i);
            objArr[i] = new Point3(p.getX(), p.getY(), p.getZ());
        }
        MatOfPoint3f objPts = new MatOfPoint3f(); objPts.fromArray(objArr);
        MatOfPoint2f imgPts = mat2f(pts2D);

        Mat rvec = new Mat(), tvec = new Mat();
        MatOfInt inliers = new MatOfInt();
        MatOfDouble dist = new MatOfDouble();
        boolean ok = Calib3d.solvePnPRansac(
                objPts, imgPts, recon.getK(), dist,
                rvec, tvec, false, 100, (float)MAX_REPROJECTION_ERROR,
                0.99, inliers, Calib3d.SOLVEPNP_EPNP
        );
        if (!ok || inliers.rows() < MIN_INLIERS_FOR_CAMERA) return false;

        Mat R = new Mat();
        Calib3d.Rodrigues(rvec, R);
        recon.addCamera(imageName, R, tvec);

        Set<Integer> idxs = new HashSet<>();
        for (int i=0;i<inliers.rows();i++) idxs.add((int)inliers.get(i,0)[0]);
        for (int i=0;i<pts2D.size();i++) {
            if (idxs.contains(i)) {
                recon.addObservation(names.get(i), imageName, pts2D.get(i));
            }
        }
        return true;
    }

    private static void triangulateNewPoints(Reconstruction recon, String newImg,
                                             Map<String, Map<String, Point2D>> pointsByImage,
                                             Set<String> reconstructed) {
        Map<String, Point2D> newPts = pointsByImage.get(newImg);
        if (newPts == null) return;
        for (String name : new HashSet<>(newPts.keySet())) {
            if (recon.hasPoint(name)) continue;
            List<String> views = new ArrayList<>();
            List<Point2D> obs = new ArrayList<>();
            for (String other : reconstructed) {
                if (other.equals(newImg)) continue;
                Point2D p = pointsByImage.get(other).get(name);
                if (p!=null) { views.add(other); obs.add(p); }
            }
            if (views.size() < 2) continue;
            Point3D bestP = null; double bestErr = Double.MAX_VALUE;
            String bestView = null;
            for (int i=0;i<views.size();i++) {
                String o = views.get(i);
                Point2D po = obs.get(i), pn = newPts.get(name);
                Mat P1 = buildProjection(recon.getK(), recon.getCameraRotation(o), recon.getCameraTranslation(o));
                Mat P2 = buildProjection(recon.getK(), recon.getCameraRotation(newImg), recon.getCameraTranslation(newImg));
                Mat C1 = getCameraCenter(recon.getCameraRotation(o), recon.getCameraTranslation(o));
                Mat C2 = getCameraCenter(recon.getCameraRotation(newImg), recon.getCameraTranslation(newImg));
                Mat P1f = new Mat(), P2f = new Mat();
                P1.convertTo(P1f, CvType.CV_32F); P2.convertTo(P2f, CvType.CV_32F);
                MatOfPoint2f m1 = new MatOfPoint2f(new Point(po.getX(), po.getY()));
                MatOfPoint2f m2 = new MatOfPoint2f(new Point(pn.getX(), pn.getY()));
                Mat pts4d = new Mat();
                Calib3d.triangulatePoints(P1f,P2f,m1,m2,pts4d);
                double w = pts4d.get(3,0)[0];
                Point3D cand = new Point3D(name,
                        pts4d.get(0,0)[0]/w,
                        pts4d.get(1,0)[0]/w,
                        pts4d.get(2,0)[0]/w);
                Mat X = new Mat(4,1,CvType.CV_64F); X.put(0,0,
                        cand.getX(), cand.getY(), cand.getZ(),1.0);
                double angle = triangulationAngle(C1, C2, X);
                P1f.release(); P2f.release(); pts4d.release(); X.release(); m1.release(); m2.release();
                if (angle < MIN_TRIANGULATION_ANGLE_RAD) continue;
                double e1 = computeReprojectionError(cand, po, P1);
                double e2 = computeReprojectionError(cand, pn, P2);
                double ae = (e1+e2)/2.0;
                if (ae<bestErr && ae<MAX_REPROJECTION_ERROR) {
                    bestErr=ae; bestP=cand; bestView=o;
                }
            }
            if (bestP!=null) {
                recon.addPoint(name, bestP);
                recon.addObservation(name, newImg, newPts.get(name));
                recon.addObservation(name, bestView, pointsByImage.get(bestView).get(name));
                for (String o : views) {
                    if (o.equals(bestView)) continue;
                    Point2D p = pointsByImage.get(o).get(name);
                    double err = computeReprojectionError(bestP, p,
                            buildProjection(recon.getK(), recon.getCameraRotation(o), recon.getCameraTranslation(o)));
                    if (err<MAX_REPROJECTION_ERROR) {
                        recon.addObservation(name, o, p);
                    }
                }
            }
        }
    }

    private static double triangulationAngle(Mat C1, Mat C2, Mat X) {
        double v1x = X.get(0,0)[0]-C1.get(0,0)[0], v1y = X.get(1,0)[0]-C1.get(1,0)[0], v1z = X.get(2,0)[0]-C1.get(2,0)[0];
        double v2x = X.get(0,0)[0]-C2.get(0,0)[0], v2y = X.get(1,0)[0]-C2.get(1,0)[0], v2z = X.get(2,0)[0]-C2.get(2,0)[0];
        double n1 = Math.sqrt(v1x*v1x+v1y*v1y+v1z*v1z), n2= Math.sqrt(v2x*v2x+v2y*v2y+v2z*v2z);
        v1x/=n1; v1y/=n1; v1z/=n1; v2x/=n2; v2y/=n2; v2z/=n2;
        double dot = Math.max(-1.0, Math.min(1.0, v1x*v2x+v1y*v2y+v1z*v2z));
        return Math.acos(dot);
    }

    private static Mat getCameraCenter(Mat R, Mat t) {
        Mat Rt = new Mat(); Core.transpose(R, Rt);
        Mat C = new Mat(); Core.gemm(Rt, t, -1.0, new Mat(), 0.0, C);
        Rt.release();
        return C;
    }

    private static void performGlobalBA(Reconstruction recon,
                                        Map<String, Map<String, Point2D>> pointsByImage,
                                        Set<String> camsSubset) {
        // prepare only cams in subset
        List<Mat> Rs = new ArrayList<>();
        List<Mat> Ts = new ArrayList<>();
        List<List<Point2D>> obs = new ArrayList<>();
        List<List<String>> names = new ArrayList<>();
        for (String c: camsSubset) {
            Rs.add(recon.getCameraRotation(c));
            Ts.add(recon.getCameraTranslation(c));
            List<Point2D> io = new ArrayList<>();
            List<String> in = new ArrayList<>();
            for (String p: recon.getPointCloud().keySet()) {
                if (recon.hasObservation(p,c)) {
                    io.add(recon.getObservation(p,c)); in.add(p);
                }
            }
            obs.add(io); names.add(in);
        }
        // use adaptive stopping: pass thresholds to BundleAdjuster
        BundleAdjuster ba = new BundleAdjuster(
                recon.getPointCloud(), Rs, Ts, obs, names, recon.getK(),
                /*maxEvals*/200, /*maxIters*/200,
                /*minErrorDelta*/1e-3, /*maxNoImprove*/5    // new adaptive params
        );
        ba.optimize(); ba.updateCloudMap(recon.getPointCloud());
        // update cameras
        int idx=0;
        for (String c: camsSubset) {
            recon.updateCamera(c, Rs.get(idx), Ts.get(idx)); idx++;
        }
    }

    private static double computeReprojectionError(Point3D pt, Point2D p2, Mat P) {
        Mat X = new Mat(4,1,CvType.CV_64F); X.put(0,0,pt.getX(),pt.getY(),pt.getZ(),1.0);
        Mat x = new Mat(); Core.gemm(P, X, 1.0, new Mat(), 0.0, x);
        double px = x.get(0,0)[0]/x.get(2,0)[0], py = x.get(1,0)[0]/x.get(2,0)[0];
        X.release(); x.release();
        double dx = px - p2.getX(), dy = py - p2.getY();
        return Math.sqrt(dx*dx + dy*dy);
    }

    private static void applyGlobalTransform(Map<String, Point3D> cloudMap) {
        for (Map.Entry<String, Point3D> e : cloudMap.entrySet()) {
            Point3D p = e.getValue();
            Mat vec = new Mat(3,1,CvType.CV_64F); vec.put(0,0,p.getX(),p.getY(),p.getZ());
            Mat rot = new Mat(); Core.gemm(globalR, vec, 1.0, new Mat(), 0.0, rot);
            double rx = rot.get(0,0)[0] + globalT.get(0,0)[0];
            double ry = rot.get(1,0)[0] + globalT.get(1,0)[0];
            double rz = rot.get(2,0)[0] + globalT.get(2,0)[0];
            rot.release(); vec.release();
            e.setValue(new Point3D(e.getKey(), rx, ry, rz));
        }
    }

    private static Mat estimateCameraMatrix(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double f = 1.2 * Math.max(w, h);
        Mat K = Mat.eye(3, 3, CvType.CV_64F);
        K.put(0,0,f); K.put(1,1,f);
        K.put(0,2,w/2.0); K.put(1,2,h/2.0);
        return K;
    }

    private static MatOfPoint2f mat2f(List<Point2D> list) {
        Point[] arr = new Point[list.size()];
        for (int i=0;i<list.size();i++) {
            Point2D p = list.get(i);
            arr[i] = new Point(p.getX(), p.getY());
        }
        return new MatOfPoint2f(arr);
    }

    private static Mat buildProjection(Mat K, Mat R, Mat t) {
        Mat Rt = Mat.zeros(3,4,CvType.CV_64F);
        R.copyTo(Rt.colRange(0,3)); t.copyTo(Rt.col(3));
        Mat P = new Mat(); Core.gemm(K, Rt, 1.0, new Mat(), 0.0, P);
        Rt.release();
        return P;
    }

    private static ImagePair findBestPair(ImageProcessor proc, List<String> images,
                                          Map<String, Map<String, Point2D>> pointsByImage) {
        ImagePair best = new ImagePair();
        int max = 0;
        for (int i=0; i<images.size()-1; i++) {
            String i1 = images.get(i);
            Set<String> A = pointsByImage.get(i1).keySet();
            for (int j=i+1; j<images.size(); j++) {
                String i2 = images.get(j);
                Set<String> B = pointsByImage.get(i2).keySet();
                Set<String> common = new HashSet<>(A); common.retainAll(B);
                int cnt = common.size();
                if (cnt > max && cnt >= MIN_COMMON_POINTS) {
                    max = cnt;
                    best.setImage1(i1);
                    best.setImage2(i2);
                    best.setCor(cnt);
                }
            }
        }
        if (max == 0) {
            throw new RuntimeException("No image pair with sufficient correspondences found");
        }
        return best;
    }
}

@Data @AllArgsConstructor
class ImageScore {
    private String imageName;
    private int numMatches;
}

class Reconstruction {
    private final Mat K;
    private final Map<String, Mat> cameraRotations = new HashMap<>();
    private final Map<String, Mat> cameraTranslations = new HashMap<>();
    private final Map<String, Point3D> pointCloud = new LinkedHashMap<>();
    private final Map<String, Map<String, Point2D>> observations = new HashMap<>();

    public Reconstruction(Mat K) { this.K = K.clone(); }
    public Mat getK() { return K; }
    public Map<String, Point3D> getPointCloud() { return pointCloud; }
    public void addCamera(String name, Mat R, Mat t) { cameraRotations.put(name, R.clone()); cameraTranslations.put(name, t.clone()); }
    public void updateCamera(String name, Mat R, Mat t) { cameraRotations.put(name, R.clone()); cameraTranslations.put(name, t.clone()); }
    public Mat getCameraRotation(String name) { return cameraRotations.get(name); }
    public Mat getCameraTranslation(String name) { return cameraTranslations.get(name); }
    public void addPoint(String name, Point3D p) { pointCloud.put(name, p); observations.put(name, new HashMap<>()); }
    public boolean hasPoint(String name) { return pointCloud.containsKey(name); }
    public Point3D getPoint(String name) { return pointCloud.get(name); }
    public void addObservation(String pt, String cam, Point2D obs) { observations.computeIfAbsent(pt, k->new HashMap<>()).put(cam, obs); }
    public boolean hasObservation(String pt, String cam) { return observations.containsKey(pt) && observations.get(pt).containsKey(cam); }
    public Point2D getObservation(String pt, String cam) { return observations.get(pt).get(cam); }
    public Set<String> getCameraNames() { return cameraRotations.keySet(); }
}

@Data @AllArgsConstructor @NoArgsConstructor
class ImagePair {
    private String image1;
    private String image2;
    private int cor;
}