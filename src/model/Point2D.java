package model;

/**
 * Двумерная точка с именем и координатами x, y.
 */
public class Point2D extends Point {
    private Integer x;
    private Integer y;

    public Point2D(String name, Integer x, Integer y) {
        super(name);
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x){this.x = x;}
    public void setY(int y){this.y = y;}

    @Override
    public String toString() {
        return String.format("Point2D{name='%s', x=%d, y=%d}", getName(), x, y);
    }
}
