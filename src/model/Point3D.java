package model;

public class Point3D extends Point {
    private final Integer x;
    private final Integer y;
    private final Integer z;

    public Point3D(String name, Integer x, Integer y, Integer z) {
        super(name);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    @Override
    public String toString() {
        return String.format("Point3D{name='%s', x=%d, y=%d, z=%d}", getName(), x, y, z);
    }
}
