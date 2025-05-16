package model;

/**
 * Двумерная точка с именем и координатами x, y.
 */
public class Point2D extends NamedPoint {
    private double x;
    private double y;

    public Point2D(String name, Integer x, Integer y) {
        super(name);
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public void setX(double x){this.x = x;}
    public void setY(double y){this.y = y;}

    @Override
    public String toString() {
        return String.format("Point2D{name='%s', x=%d, y=%d}", getName(), x, y);
    }
}
