package repro;

import java.awt.Point;
import java.awt.geom.Area;

/**
 * Control: same program, but the Point is never passed where a Point2D is
 * expected - the (double, double) overload is used instead. This is the
 * workaround used for AbstractInter.contains: area.contains(point.x, point.y).
 * Expected: compiles to a module Node accepts.
 */
public class ReproFixed {
    static boolean check(Area area, Point p) {
        return area.contains(p.x, p.y);
    }

    public static void main(String[] args) {
        Area area = new Area();
        Point p = new Point(3, 4);
        System.out.println("fixed: " + check(area, p));
    }
}
