package model;

import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.fitting.leastsquares.*;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.util.Pair;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.util.*;

/**
 * Глобальный bundle adjustment для SfM.
 */
public class BundleAdjuster {
    // Параметры оптимизации (можно менять здесь)
    private int MAX_EVALUATIONS = 5000;
    private int MAX_ITERATIONS  = 5000;

    private final List<String> ptNames;
    private final List<Mat> Rs;
    private final List<Mat> Ts;
    private final List<List<Point2D>> obs;
    private final List<List<String>> obsNames;
    private final Mat K;
    private double[] params;

    public BundleAdjuster(Map<String, Point3D> points,
                          List<Mat> Rs,
                          List<Mat> Ts,
                          List<List<Point2D>> obs,
                          List<List<String>> obsNames,
                          Mat K,
                          int maxEv,
                          int maxIt) {
        this.ptNames  = new ArrayList<>(points.keySet());
        this.Rs       = Rs;
        this.Ts       = Ts;
        this.obs      = obs;
        this.obsNames = obsNames;
        this.K        = K;
        initParams(points);
        this.MAX_ITERATIONS=maxIt;
        this.MAX_EVALUATIONS=maxEv;
    }

    private void initParams(Map<String, Point3D> map) {
        int nCam = Rs.size();
        int nPt  = ptNames.size();
        params = new double[nCam * 6 + nPt * 3];
        int idx = 0;
        // Камеры: rvec(3) + t(3)
        for (int i = 0; i < nCam; i++) {
            Mat rvec = new Mat();
            Calib3d.Rodrigues(Rs.get(i), rvec);
            params[idx++] = rvec.get(0,0)[0];
            params[idx++] = rvec.get(1,0)[0];
            params[idx++] = rvec.get(2,0)[0];
            params[idx++] = Ts.get(i).get(0,0)[0];
            params[idx++] = Ts.get(i).get(1,0)[0];
            params[idx++] = Ts.get(i).get(2,0)[0];
            rvec.release();
        }
        // Точки: X, Y, Z
        for (String name : ptNames) {
            Point3D p = map.get(name);
            params[idx++] = p.getX();
            params[idx++] = p.getY();
            params[idx++] = p.getZ();
        }
    }

    public void optimize() {
        MultivariateJacobianFunction model = point -> {
            double[] x = point.toArray();
            double[] residuals = computeResiduals(x);
            org.apache.commons.math3.linear.RealMatrix J = computeJacobian(x);
            return new Pair<>(new ArrayRealVector(residuals), J);
        };

        int dim = computeResiduals(params).length;
        double[] target = new double[dim];
        double[] weightsArr = new double[dim];
        Arrays.fill(weightsArr, 1.0);
        DiagonalMatrix weight = new DiagonalMatrix(weightsArr);

        LeastSquaresProblem problem = new LeastSquaresBuilder()
                .start(params)
                .model(model)
                .target(target)
                .weight(weight)
                .maxEvaluations(MAX_EVALUATIONS)
                .maxIterations(MAX_ITERATIONS)
                .build();

        LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();
        try {
            LeastSquaresOptimizer.Optimum optimum = optimizer.optimize(problem);
            params = optimum.getPoint().toArray();
        } catch (MaxCountExceededException ex) {
            System.err.println("Bundle adjustment не сошёлся: превысили " + MAX_EVALUATIONS + " оценок");
        }
    }

    private double[] computeResiduals(double[] p) {
        List<Double> res = new ArrayList<>();
        int nCam = Rs.size();
        int idxCam = 0;
        int idxPt  = nCam * 6;

        for (int i = 0; i < nCam; i++) {
            Mat rv = new Mat(3, 1, CvType.CV_64F);
            rv.put(0,0, p[idxCam], p[idxCam+1], p[idxCam+2]);
            Mat R = new Mat();
            Calib3d.Rodrigues(rv, R);
            Mat t = new Mat(3,1,CvType.CV_64F);
            t.put(0,0, p[idxCam+3], p[idxCam+4], p[idxCam+5]);
            idxCam += 6;

            List<Point2D> obsi = obs.get(i);
            List<String> nmsi    = obsNames.get(i);
            for (int j = 0; j < obsi.size(); j++) {
                int pi = ptNames.indexOf(nmsi.get(j));
                double X = p[idxPt + pi*3];
                double Y = p[idxPt + pi*3 + 1];
                double Z = p[idxPt + pi*3 + 2];

                Mat Xh = new Mat(4,1,CvType.CV_64F);
                Xh.put(0,0, X, Y, Z, 1.0);
                Mat P = buildProjection(K, R, t);
                Mat ph = new Mat();
                Core.gemm(P, Xh, 1.0, new Mat(), 0.0, ph);
                double u = ph.get(0,0)[0] / ph.get(2,0)[0];
                double v = ph.get(1,0)[0] / ph.get(2,0)[0];

                Point2D obsPt = obsi.get(j);
                res.add(u - obsPt.getX());
                res.add(v - obsPt.getY());

                Xh.release(); ph.release(); P.release();
            }
            rv.release(); R.release(); t.release();
        }

        return res.stream().mapToDouble(d -> d).toArray();
    }

    private org.apache.commons.math3.linear.RealMatrix computeJacobian(double[] p) {
        double eps = 1e-6;
        double[] base = computeResiduals(p);
        int m = base.length;
        int n = p.length;
        org.apache.commons.math3.linear.RealMatrix J = new Array2DRowRealMatrix(m, n);

        for (int k = 0; k < n; k++) {
            p[k] += eps;
            double[] pert = computeResiduals(p);
            p[k] -= eps;
            for (int i = 0; i < m; i++) {
                J.setEntry(i, k, (pert[i] - base[i]) / eps);
            }
        }
        return J;
    }

    public void updateCloudMap(Map<String, Point3D> map) {
        int nCam = Rs.size();
        int idx  = nCam * 6;
        for (String name : ptNames) {
            double X = params[idx++];
            double Y = params[idx++];
            double Z = params[idx++];
            map.put(name, new Point3D(name, X, Y, Z));
        }
    }

    private static Mat buildProjection(Mat K, Mat R, Mat t) {
        Mat Rt = Mat.zeros(3,4,CvType.CV_64F);
        R.copyTo(Rt.colRange(0,3));
        t.copyTo(Rt.col(3));
        Mat P = new Mat();
        Core.gemm(K, Rt, 1.0, new Mat(), 0.0, P);
        Rt.release();
        return P;
    }
}
