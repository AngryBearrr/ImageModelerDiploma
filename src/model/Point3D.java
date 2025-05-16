package model;

public class Point3D extends NamedPoint {
    private final double x;
    private final double y;
    private final double z;

    /**
     * Constructor without name; name will be empty.
     */
    public Point3D(double x, double y, double z) {
        super("");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Constructor with explicit name.
     */
    public Point3D(String name, double x, double y, double z) {
        super(name);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    @Override
    public String toString() {
        return String.format("Point3D{name='%s', x=%.6f, y=%.6f, z=%.6f}", getName(), x, y, z);
    }
}
