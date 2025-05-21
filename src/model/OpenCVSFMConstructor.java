package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
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
    private static final double MAX_REPROJECTION_ERROR = 6.0; // Maximum reprojection error in pixels
    private static final double MIN_TRIANGULATION_ANGLE = 3.0; // Minimum angle in degrees for triangulation
    private static final boolean ENABLE_GLOBAL_BA = true; // Enable global bundle adjustment after adding each camera
    private static final int MIN_POINTS_FOR_RESECTION = 4; // Minimum 3D-2D correspondences for PnP
    private static final int MIN_INLIERS_FOR_CAMERA = 6; // Minimum inliers to accept a camera
    private static final int MIN_COMMON_POINTS = 5; // Minimum points to consider connecting two cameras
    private static final double PNP_REPROJECTION_THRESH  = 10.0;

    /**
     * Set a global rotation to be applied to the reconstructed point-cloud.
     */
    public static void setGlobalRotation(Mat R) {
        globalR = R;
    }

    /**
     * Set a global translation to be applied to the reconstructed point-cloud.
     */
    public static void setGlobalTranslation(Mat t) {
        globalT = t;
    }

    /**
     * Perform incremental SfM on all images, starting from the best pair,
     * adding cameras one by one while minimizing reprojection error.
     */
    public static List<Point3D> reconstructAll(ImageProcessor proc) {
        // 1) Собираем все имена изображений
        List<String> allImages = Collections.list(proc.getImagesModel().elements());
        if (allImages.size() < 2) {
            throw new RuntimeException("At least 2 images are required for reconstruction");
        }

        // 2) Собираем 2D-точки по каждому изображению
        Map<String, Map<String, Point2D>> pointsByImage = new HashMap<>();
        for (String imageName : allImages) {
            proc.setActiveImage(imageName);
            List<Point2D> points = proc.getActiveImagePoints();
            Map<String, Point2D> pointMap = points.stream()
                    .collect(Collectors.toMap(Point2D::getName, Function.identity()));
            pointsByImage.put(imageName, pointMap);
        }

        // 3) Находим лучшую первичную пару
        ImagePair bestPair = findBestPair(allImages, pointsByImage);
        System.out.println("Best pair: " + bestPair.getImage1() + " <-> " + bestPair.getImage2() +
                " with " + bestPair.getCor() + " correspondences");

        // 4) Оцениваем K по первому изображению
        BufferedImage img0 = proc.getImage(bestPair.getImage1()).getBufferedImage();
        Mat K = estimateCameraMatrix(img0);

        // 5) Инициализируем реконструкцию по этой паре
        Reconstruction recon = new Reconstruction(K);
        initializeFromPair(recon, bestPair, pointsByImage, proc);

        // 6) Готовим множества добавленных и оставшихся изображений
        Set<String> reconstructedImages = new HashSet<>();
        reconstructedImages.add(bestPair.getImage1());
        reconstructedImages.add(bestPair.getImage2());

        List<String> remainingImages = new ArrayList<>(allImages);
        remainingImages.removeAll(reconstructedImages);

        // 7) Инкрементальное добавление камер
        while (!remainingImages.isEmpty()) {
            ImageScore bestNext = findBestImageToAdd(remainingImages, pointsByImage, recon);
            if (bestNext == null || bestNext.getNumMatches() < MIN_POINTS_FOR_RESECTION) {
                System.out.println("No more images with sufficient matches to the reconstruction");
                break;
            }

            String nextImage = bestNext.getImageName();
            System.out.println("Adding image: " + nextImage + " with " + bestNext.getNumMatches() + " matches");

            boolean success = registerNewImage(recon, nextImage, pointsByImage.get(nextImage));
            if (success) {
                reconstructedImages.add(nextImage);
                triangulateNewPoints(recon, nextImage, pointsByImage, reconstructedImages);
                if (ENABLE_GLOBAL_BA) {
                    performGlobalBA(recon, pointsByImage, reconstructedImages);
                }
            } else {
                System.out.println("Failed to register image: " + nextImage);
            }

            remainingImages.remove(nextImage);
        }

        // 8) Триангулируем глобально все оставшиеся точки, видимые в ≥2 камерах
        triangulateGlobalUninitialized(recon, pointsByImage, reconstructedImages);

        // 9) Финальный глобальный Bundle Adjustment
        performGlobalBA(recon, pointsByImage, reconstructedImages);

        // 10) Применяем глобальный поворот/сдвиг и возвращаем облако
        Map<String, Point3D> finalCloud = recon.getPointCloud();
        applyGlobalTransform(finalCloud);
        return new ArrayList<>(finalCloud.values());
    }


    /**
     * Initialize the reconstruction from a pair of images.
     */
    private static void initializeFromPair(Reconstruction recon, ImagePair pair,
                                           Map<String, Map<String, Point2D>> pointsByImage,
                                           ImageProcessor proc) {
        String img1 = pair.getImage1();
        String img2 = pair.getImage2();

        Map<String, Point2D> points1 = pointsByImage.get(img1);
        Map<String, Point2D> points2 = pointsByImage.get(img2);

        // Find common points between the two images
        Set<String> commonPointNames = new HashSet<>(points1.keySet());
        commonPointNames.retainAll(points2.keySet());

        if (commonPointNames.size() < MIN_COMMON_POINTS) {
            throw new RuntimeException("Insufficient common points for initial reconstruction");
        }

        // Create lists of corresponding points
        List<Point2D> common1 = new ArrayList<>();
        List<Point2D> common2 = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (String name : commonPointNames) {
            common1.add(points1.get(name));
            common2.add(points2.get(name));
            names.add(name);
        }

        // Estimate Essential matrix and recover pose
        BufferedImage imgA = proc.getImage(img1).getBufferedImage();
        double f = recon.getK().get(0, 0)[0];
        Point pp = new Point(recon.getK().get(0, 2)[0], recon.getK().get(1, 2)[0]);

        MatOfPoint2f m1 = mat2f(common1);
        MatOfPoint2f m2 = mat2f(common2);

        Mat maskE = new Mat();
        Mat E = Calib3d.findEssentialMat(m1, m2, f, pp, Calib3d.RANSAC, 0.999, 1.0, 1000, maskE);

        // Convert mask to boolean array
        boolean[] inlierMask = new boolean[maskE.rows()];
        for (int i = 0; i < maskE.rows(); i++) {
            inlierMask[i] = maskE.get(i, 0)[0] > 0;
        }

        // Keep only inliers
        List<Point2D> inliers1 = new ArrayList<>();
        List<Point2D> inliers2 = new ArrayList<>();
        List<String> inlierNames = new ArrayList<>();

        for (int i = 0; i < common1.size(); i++) {
            if (inlierMask[i]) {
                inliers1.add(common1.get(i));
                inliers2.add(common2.get(i));
                inlierNames.add(names.get(i));
            }
        }

        // Recover relative pose
        Mat R = new Mat();
        Mat t = new Mat();
        MatOfPoint2f inlierMat1 = mat2f(inliers1);
        MatOfPoint2f inlierMat2 = mat2f(inliers2);
        Calib3d.recoverPose(E, inlierMat1, inlierMat2, recon.getK(), R, t);

        // Add first two cameras to reconstruction
        recon.addCamera(img1, Mat.eye(3, 3, CvType.CV_64F), Mat.zeros(3, 1, CvType.CV_64F));
        recon.addCamera(img2, R.clone(), t.clone());

        // Triangulate points between the two views
        Mat P1 = buildProjection(recon.getK(), recon.getCameraRotation(img1), recon.getCameraTranslation(img1));
        Mat P2 = buildProjection(recon.getK(), recon.getCameraRotation(img2), recon.getCameraTranslation(img2));

        Mat P1f = new Mat();
        Mat P2f = new Mat();
        P1.convertTo(P1f, CvType.CV_32F);
        P2.convertTo(P2f, CvType.CV_32F);

        MatOfPoint2f m1f = new MatOfPoint2f();
        MatOfPoint2f m2f = new MatOfPoint2f();
        inlierMat1.convertTo(m1f, CvType.CV_32F);
        inlierMat2.convertTo(m2f, CvType.CV_32F);

        Mat pts4d = new Mat();
        Calib3d.triangulatePoints(P1f, P2f, m1f, m2f, pts4d);

        // Add triangulated points to reconstruction
        for (int i = 0; i < pts4d.cols(); i++) {
            double w = pts4d.get(3, i)[0];
            double x = pts4d.get(0, i)[0] / w;
            double y = pts4d.get(1, i)[0] / w;
            double z = pts4d.get(2, i)[0] / w;

            String pointName = inlierNames.get(i);
            Point3D point3D = new Point3D(pointName, x, y, z);

            // Only add points with good reprojection error
            double reproj1 = computeReprojectionError(point3D, inliers1.get(i), P1);
            double reproj2 = computeReprojectionError(point3D, inliers2.get(i), P2);

            if (reproj1 < MAX_REPROJECTION_ERROR && reproj2 < MAX_REPROJECTION_ERROR) {
                recon.addPoint(pointName, point3D);

                // Store observations for these points
                recon.addObservation(pointName, img1, inliers1.get(i));
                recon.addObservation(pointName, img2, inliers2.get(i));
            }
        }

        System.out.println("Initial reconstruction: " + recon.getPointCloud().size() + " points");
    }

    /**
     * Find the best image to add next to the reconstruction.
     */
    private static ImageScore findBestImageToAdd(List<String> remainingImages,
                                                 Map<String, Map<String, Point2D>> pointsByImage,
                                                 Reconstruction recon) {
        ImageScore bestScore = null;

        for (String imageName : remainingImages) {
            Map<String, Point2D> imagePoints = pointsByImage.get(imageName);
            int numMatches = 0;

            // Count how many points in this image are already in the reconstruction
            for (String pointName : imagePoints.keySet()) {
                if (recon.hasPoint(pointName)) {
                    numMatches++;
                }
            }

            if (numMatches >= MIN_POINTS_FOR_RESECTION &&
                    (bestScore == null || numMatches > bestScore.getNumMatches())) {
                bestScore = new ImageScore(imageName, numMatches);
            }
        }

        return bestScore;
    }

    /**
     * Register a new image to the reconstruction using PnP.
     */
    private static boolean registerNewImage(Reconstruction recon,
                                            String imageName,
                                            Map<String, Point2D> imagePoints) {
        // 1) Собираем 3D→2D
        List<Point3D> pts3D = new ArrayList<>();
        List<Point2D> pts2D = new ArrayList<>();
        for (var e : imagePoints.entrySet()) {
            if (!recon.hasPoint(e.getKey())) continue;
            pts3D .add(recon.getPoint(e.getKey()));
            pts2D .add(e.getValue());
        }
        System.out.printf("Trying PnP for %s: %d correspondences\n",
                imageName, pts3D.size());
        if (pts3D.size() < MIN_POINTS_FOR_RESECTION) return false;

        // 2) Подготовка для solvePnP
        MatOfPoint3f objPts = new MatOfPoint3f(
                pts3D.stream().map(p->new Point3(p.getX(),p.getY(),p.getZ()))
                        .toArray(Point3[]::new)
        );
        MatOfPoint2f imgPts = mat2f(pts2D);
        MatOfDouble dist = new MatOfDouble(); // нулевые коэффициенты

        Mat rvec = new Mat(), tvec = new Mat(), inliers = new Mat();
        boolean ok = Calib3d.solvePnPRansac(
                objPts, imgPts, recon.getK(), dist,
                rvec, tvec, false,
                100,           // итераций
                (float)PNP_REPROJECTION_THRESH,
                0.99,          // доверие
                inliers,
                Calib3d.SOLVEPNP_EPNP
        );
        System.out.printf("  → solvePnP ok=%b, inliers=%d\n",
                ok, inliers.rows());
        if (!ok || inliers.rows() < MIN_INLIERS_FOR_CAMERA) {
            // Попробуем AP3P как запасной вариант
            ok = Calib3d.solvePnPRansac(
                    objPts, imgPts, recon.getK(), dist,
                    rvec, tvec, false,
                    200,
                    (float)PNP_REPROJECTION_THRESH,
                    0.99,
                    inliers,
                    Calib3d.SOLVEPNP_AP3P
            );
            System.out.printf("  → fallback AP3P ok=%b, inliers=%d\n",
                    ok, inliers.rows());
        }
        if (!ok || inliers.rows() < MIN_INLIERS_FOR_CAMERA) {
            return false;
        }

        // 3) Успешно: добавляем камеру
        Mat R = new Mat();
        Calib3d.Rodrigues(rvec, R);
        recon.addCamera(imageName, R, tvec);

        // 4) Храним только инлиер-наблюдения
        Set<Integer> inlSet = new HashSet<>();
        for (int i = 0; i < inliers.rows(); i++) {
            inlSet.add((int)inliers.get(i,0)[0]);
        }
        for (int i = 0; i < pts2D.size(); i++) {
            if (inlSet.contains(i)) {
                recon.addObservation(imagePoints.keySet().toArray(new String[0])[i],
                        imageName,
                        pts2D.get(i));
            }
        }

        return true;
    }


    /**
     * Triangulate new points visible in the newly added camera.
     */
    private static void triangulateNewPoints(Reconstruction recon, String newImageName,
                                             Map<String, Map<String, Point2D>> pointsByImage,
                                             Set<String> reconstructedImages) {
        Map<String, Point2D> newImagePoints = pointsByImage.get(newImageName);

        // Create list of points visible in new image but not yet in 3D reconstruction
        Set<String> newPointNames = new HashSet<>(newImagePoints.keySet());
        newPointNames.removeAll(recon.getPointCloud().keySet());

        // For each point not in the reconstruction yet
        for (String pointName : newPointNames) {
            // Find other images that see this point
            List<String> visibleImages = new ArrayList<>();
            List<Point2D> observations = new ArrayList<>();

            for (String otherImage : reconstructedImages) {
                if (otherImage.equals(newImageName)) continue;

                Map<String, Point2D> otherImagePoints = pointsByImage.get(otherImage);
                Point2D observation = otherImagePoints.get(pointName);

                if (observation != null) {
                    visibleImages.add(otherImage);
                    observations.add(observation);
                }
            }

            // Need at least one other view to triangulate
            if (visibleImages.isEmpty()) {
                continue;
            }

            // Triangulate the point using all available views
            Point3D bestPoint = null;
            double bestError = Double.MAX_VALUE;
            String bestOtherImage = null;

            for (int i = 0; i < visibleImages.size(); i++) {
                String otherImage = visibleImages.get(i);
                Point2D otherObservation = observations.get(i);

                // Get camera matrices
                Mat P1 = buildProjection(
                        recon.getK(),
                        recon.getCameraRotation(otherImage),
                        recon.getCameraTranslation(otherImage)
                );

                Mat P2 = buildProjection(
                        recon.getK(),
                        recon.getCameraRotation(newImageName),
                        recon.getCameraTranslation(newImageName)
                );

                // Check triangulation angle
                Mat C1 = getCameraCenter(recon.getCameraRotation(otherImage), recon.getCameraTranslation(otherImage));
                Mat C2 = getCameraCenter(recon.getCameraRotation(newImageName), recon.getCameraTranslation(newImageName));

                // Convert to float for triangulation
                Mat P1f = new Mat();
                Mat P2f = new Mat();
                P1.convertTo(P1f, CvType.CV_32F);
                P2.convertTo(P2f, CvType.CV_32F);

                // Create point matrices
                MatOfPoint2f points1 = new MatOfPoint2f();
                MatOfPoint2f points2 = new MatOfPoint2f();
                points1.fromArray(new Point(otherObservation.getX(), otherObservation.getY()));
                points2.fromArray(new Point(newImagePoints.get(pointName).getX(), newImagePoints.get(pointName).getY()));

                // Triangulate
                Mat pts4d = new Mat();
                Calib3d.triangulatePoints(P1f, P2f, points1, points2, pts4d);

                // Convert to 3D point
                double w = pts4d.get(3, 0)[0];
                double x = pts4d.get(0, 0)[0] / w;
                double y = pts4d.get(1, 0)[0] / w;
                double z = pts4d.get(2, 0)[0] / w;

                Point3D point3D = new Point3D(pointName, x, y, z);

                // Create 3D point of triangulated position
                Mat X = new Mat(4, 1, CvType.CV_64F);
                X.put(0, 0, x, y, z, 1.0);

                // Check triangulation angle
                double angle = triangulationAngle(C1, C2, X);
                if (angle < Math.toRadians(MIN_TRIANGULATION_ANGLE)) {
                    continue;
                }

                // Calculate reprojection errors
                double error1 = computeReprojectionError(point3D, otherObservation, P1);
                double error2 = computeReprojectionError(point3D, newImagePoints.get(pointName), P2);
                double avgError = (error1 + error2) / 2.0;

                // Keep the best triangulation
                if (avgError < bestError && avgError < MAX_REPROJECTION_ERROR) {
                    bestError = avgError;
                    bestPoint = point3D;
                    bestOtherImage = otherImage;
                }
            }

            // If we found a good triangulation, add it to the reconstruction
            if (bestPoint != null) {
                recon.addPoint(pointName, bestPoint);
                recon.addObservation(pointName, newImageName, newImagePoints.get(pointName));
                recon.addObservation(pointName, bestOtherImage, pointsByImage.get(bestOtherImage).get(pointName));

                // Add observations from other views if they have good reprojection error
                for (int i = 0; i < visibleImages.size(); i++) {
                    String otherImage = visibleImages.get(i);
                    if (otherImage.equals(bestOtherImage)) continue;

                    Point2D observation = observations.get(i);

                    Mat P = buildProjection(
                            recon.getK(),
                            recon.getCameraRotation(otherImage),
                            recon.getCameraTranslation(otherImage)
                    );

                    double error = computeReprojectionError(bestPoint, observation, P);
                    if (error < MAX_REPROJECTION_ERROR) {
                        recon.addObservation(pointName, otherImage, observation);
                    }
                }
            }
        }
    }

    /**
     * Compute the angle between rays from two camera centers to a 3D point.
     */
    private static double triangulationAngle(Mat C1, Mat C2, Mat X) {
        // Extract point coordinates
        double x = X.get(0, 0)[0];
        double y = X.get(1, 0)[0];
        double z = X.get(2, 0)[0];

        // Camera centers
        double c1x = C1.get(0, 0)[0];
        double c1y = C1.get(1, 0)[0];
        double c1z = C1.get(2, 0)[0];

        double c2x = C2.get(0, 0)[0];
        double c2y = C2.get(1, 0)[0];
        double c2z = C2.get(2, 0)[0];

        // Vectors from cameras to point
        double v1x = x - c1x;
        double v1y = y - c1y;
        double v1z = z - c1z;
        double v1n = Math.sqrt(v1x * v1x + v1y * v1y + v1z * v1z);

        double v2x = x - c2x;
        double v2y = y - c2y;
        double v2z = z - c2z;
        double v2n = Math.sqrt(v2x * v2x + v2y * v2y + v2z * v2z);

        // Normalize vectors
        v1x /= v1n;
        v1y /= v1n;
        v1z /= v1n;

        v2x /= v2n;
        v2y /= v2n;
        v2z /= v2n;

        // Compute dot product
        double dot = v1x * v2x + v1y * v2y + v1z * v2z;

        // Clamp dot product to [-1, 1] range
        dot = Math.max(-1.0, Math.min(1.0, dot));

        // Return angle in radians
        return Math.acos(dot);
    }

    /**
     * Get the camera center from rotation and translation.
     */
    private static Mat getCameraCenter(Mat R, Mat t) {
        // C = -R^T * t
        Mat Rt = new Mat();
        Core.transpose(R, Rt);

        Mat C = new Mat();
        Core.gemm(Rt, t, -1.0, new Mat(), 0.0, C);

        return C;
    }

    /**
     * Perform global bundle adjustment on the entire reconstruction.
     */
    private static void performGlobalBA(Reconstruction recon,
                                        Map<String, Map<String, Point2D>> pointsByImage,
                                        Set<String> reconstructedImages) {
        // Get all camera rotations and translations
        List<Mat> rotations = new ArrayList<>();
        List<Mat> translations = new ArrayList<>();
        List<String> cameraNames = new ArrayList<>();

        for (String imageName : reconstructedImages) {
            rotations.add(recon.getCameraRotation(imageName));
            translations.add(recon.getCameraTranslation(imageName));
            cameraNames.add(imageName);
        }

        // Prepare observations for bundle adjustment
        List<List<Point2D>> observations = new ArrayList<>();
        List<List<String>> observationNames = new ArrayList<>();

        for (String imageName : reconstructedImages) {
            List<Point2D> imageObservations = new ArrayList<>();
            List<String> imageObservationNames = new ArrayList<>();

            for (Map.Entry<String, Point3D> entry : recon.getPointCloud().entrySet()) {
                String pointName = entry.getKey();

                // If this point is observed in this image
                if (recon.hasObservation(pointName, imageName)) {
                    Point2D observation = recon.getObservation(pointName, imageName);
                    imageObservations.add(observation);
                    imageObservationNames.add(pointName);
                }
            }

            observations.add(imageObservations);
            observationNames.add(imageObservationNames);
        }

        // Run bundle adjustment
        BundleAdjuster ba = new BundleAdjuster(
                recon.getPointCloud(),
                rotations,
                translations,
                observations,
                observationNames,
                recon.getK(),
                200,
                200
        );

        ba.optimize();

        // Update reconstruction with optimized values
        ba.updateCloudMap(recon.getPointCloud());

        // Update camera poses
        for (int i = 0; i < cameraNames.size(); i++) {
            recon.updateCamera(cameraNames.get(i), rotations.get(i), translations.get(i));
        }
    }

    /**
     * Compute reprojection error for a 3D point and its 2D observation.
     */
    private static double computeReprojectionError(Point3D point3D, Point2D point2D, Mat P) {
        // Create homogeneous 3D point
        Mat X = new Mat(4, 1, CvType.CV_64F);
        X.put(0, 0, point3D.getX(), point3D.getY(), point3D.getZ(), 1.0);

        // Project 3D point to image
        Mat x = new Mat();
        Core.gemm(P, X, 1.0, new Mat(), 0.0, x);

        // Convert to homogeneous coordinates
        double px = x.get(0, 0)[0] / x.get(2, 0)[0];
        double py = x.get(1, 0)[0] / x.get(2, 0)[0];

        // Compute Euclidean distance
        double dx = px - point2D.getX();
        double dy = py - point2D.getY();

        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Applies the global rotation and translation to each point.
     */
    private static void applyGlobalTransform(Map<String, Point3D> cloudMap) {
        for (Map.Entry<String, Point3D> e : cloudMap.entrySet()) {
            Point3D p = e.getValue();
            Mat vec = new Mat(3, 1, CvType.CV_64F);
            vec.put(0, 0, p.getX(), p.getY(), p.getZ());
            Mat rotated = new Mat();
            Core.gemm(globalR, vec, 1.0, new Mat(), 0.0, rotated);
            double rx = rotated.get(0, 0)[0] + globalT.get(0, 0)[0];
            double ry = rotated.get(1, 0)[0] + globalT.get(1, 0)[0];
            double rz = rotated.get(2, 0)[0] + globalT.get(2, 0)[0];
            e.setValue(new Point3D(e.getKey(), rx, ry, rz));
        }
    }

    // Helper methods:

    private static Mat estimateCameraMatrix(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
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
        Mat Rt = Mat.zeros(3, 4, CvType.CV_64F);
        R.copyTo(Rt.colRange(0, 3));
        t.copyTo(Rt.col(3));
        Mat P = new Mat();
        Core.gemm(K, Rt, 1.0, new Mat(), 0.0, P);
        return P;
    }

    private static ImagePair findBestPair(List<String> images,
                                          Map<String, Map<String, Point2D>> pointsByImage) {
        ImagePair best = new ImagePair();
        int max = 0;

        for (int i = 0; i < images.size() - 1; i++) {
            String img1 = images.get(i);
            Set<String> A = pointsByImage.get(img1).keySet();

            for (int j = i + 1; j < images.size(); j++) {
                String img2 = images.get(j);
                Set<String> B = pointsByImage.get(img2).keySet();

                // Count common points
                Set<String> common = new HashSet<>(A);
                common.retainAll(B);
                int cnt = common.size();

                if (cnt > max && cnt >= MIN_COMMON_POINTS) {
                    max = cnt;
                    best.setImage1(img1);
                    best.setImage2(img2);
                    best.setCor(cnt);
                }
            }
        }

        if (max == 0) {
            throw new RuntimeException("No image pair with sufficient correspondences found");
        }

        return best;
    }

    private static void triangulateGlobalUninitialized(
            Reconstruction recon,
            Map<String, Map<String, Point2D>> pointsByImage,
            Set<String> cameras) {

        for (String pointName : pointsByImage.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet())) {

            if (recon.hasPoint(pointName)) continue;

            // Собираем список камер, где точка видна:
            List<String> views = cameras.stream()
                    .filter(cam -> pointsByImage.get(cam).containsKey(pointName))
                    .toList();
            if (views.size() < 2) continue;

            Point3D bestP = null;
            double bestErr = Double.MAX_VALUE;

            // Пробуем все пары камер-«наблюдателя»:
            for (int i = 0; i < views.size() - 1; i++) {
                for (int j = i + 1; j < views.size(); j++) {
                    String camA = views.get(i), camB = views.get(j);
                    Point2D pA = pointsByImage.get(camA).get(pointName);
                    Point2D pB = pointsByImage.get(camB).get(pointName);

                    Mat PA = buildProjection(recon.getK(),
                            recon.getCameraRotation(camA),
                            recon.getCameraTranslation(camA));
                    Mat PB = buildProjection(recon.getK(),
                            recon.getCameraRotation(camB),
                            recon.getCameraTranslation(camB));

                    // Простейшая триангуляция между двумя камерами:
                    Mat pts4d = new Mat();
                    MatOfPoint2f mA = new MatOfPoint2f(new Point(pA.getX(), pA.getY()));
                    MatOfPoint2f mB = new MatOfPoint2f(new Point(pB.getX(), pB.getY()));
                    Calib3d.triangulatePoints(PA, PB, mA, mB, pts4d);

                    double w = pts4d.get(3,0)[0];
                    Point3D P = new Point3D(
                            pointName,
                            pts4d.get(0,0)[0]/w,
                            pts4d.get(1,0)[0]/w,
                            pts4d.get(2,0)[0]/w
                    );

                    // Проверяем базисный угол:
                    Mat C1 = getCameraCenter(
                            recon.getCameraRotation(camA),
                            recon.getCameraTranslation(camA));
                    Mat C2 = getCameraCenter(
                            recon.getCameraRotation(camB),
                            recon.getCameraTranslation(camB));
                    if (triangulationAngle(C1, C2, toHomog(P)) < Math.toRadians(MIN_TRIANGULATION_ANGLE))
                        continue;

                    // Средняя ошибка проекции:
                    double e1 = computeReprojectionError(P, pA, PA);
                    double e2 = computeReprojectionError(P, pB, PB);
                    double avg = (e1 + e2) * 0.5;
                    if (avg > MAX_REPROJECTION_ERROR) continue;

                    if (avg < bestErr) {
                        bestErr = avg;
                        bestP = P;
                    }
                }
            }

            if (bestP != null) {
                // Добаляем точку + её наблюдения во все подходящие камеры
                recon.addPoint(pointName, bestP);
                for (String cam : views) {
                    Point2D obs = pointsByImage.get(cam).get(pointName);
                    Mat Pcam = buildProjection(recon.getK(),
                            recon.getCameraRotation(cam),
                            recon.getCameraTranslation(cam));
                    if (computeReprojectionError(bestP, obs, Pcam) < MAX_REPROJECTION_ERROR) {
                        recon.addObservation(pointName, cam, obs);
                    }
                }
            }
        }
    }

    // Вспомогательный перевод Point3D → гомогенная матрица 4×1
    private static Mat toHomog(Point3D p) {
        Mat X = new Mat(4,1,CvType.CV_64F);
        X.put(0,0, p.getX(), p.getY(), p.getZ(), 1.0);
        return X;
    }
}

/**
 * Represents an image and its score for being added to the reconstruction
 */
@Data @AllArgsConstructor
class ImageScore {
    private String imageName;
    private int numMatches;
}

/**
 * Represents the current state of the reconstruction
 */
class Reconstruction {
    @Getter
    private final Mat K; // Camera intrinsic matrix
    private final Map<String, Mat> cameraRotations; // R for each camera
    private final Map<String, Mat> cameraTranslations; // t for each camera
    @Getter
    private final Map<String, Point3D> pointCloud; // 3D points in the reconstruction
    private final Map<String, Map<String, Point2D>> observations; // point -> camera -> observation

    public Reconstruction(Mat K) {
        this.K = K.clone();
        this.cameraRotations = new HashMap<>();
        this.cameraTranslations = new HashMap<>();
        this.pointCloud = new LinkedHashMap<>();
        this.observations = new HashMap<>();
    }

    public void addCamera(String cameraName, Mat R, Mat t) {
        cameraRotations.put(cameraName, R.clone());
        cameraTranslations.put(cameraName, t.clone());
    }

    public void updateCamera(String cameraName, Mat R, Mat t) {
        cameraRotations.put(cameraName, R.clone());
        cameraTranslations.put(cameraName, t.clone());
    }

    public Mat getCameraRotation(String cameraName) {
        return cameraRotations.get(cameraName);
    }

    public Mat getCameraTranslation(String cameraName) {
        return cameraTranslations.get(cameraName);
    }

    public void addPoint(String pointName, Point3D point) {
        pointCloud.put(pointName, point);
        observations.put(pointName, new HashMap<>());
    }

    public boolean hasPoint(String pointName) {
        return pointCloud.containsKey(pointName);
    }

    public Point3D getPoint(String pointName) {
        return pointCloud.get(pointName);
    }

    public void addObservation(String pointName, String cameraName, Point2D observation) {
        if (!observations.containsKey(pointName)) {
            observations.put(pointName, new HashMap<>());
        }
        observations.get(pointName).put(cameraName, observation);
    }

    public boolean hasObservation(String pointName, String cameraName) {
        return observations.containsKey(pointName) &&
                observations.get(pointName).containsKey(cameraName);
    }

    public Point2D getObservation(String pointName, String cameraName) {
        return observations.get(pointName).get(cameraName);
    }

    public Set<String> getCameraNames() {
        return cameraRotations.keySet();
    }
}

@Data @AllArgsConstructor @NoArgsConstructor
class ImagePair {
    private String image1;
    private String image2;
    private int cor;
}