package repro;

import java.awt.Point;
import java.awt.geom.Area;
import java.awt.geom.Point2D;

/**
 * Minimal repro for TeaVM 0.16.0 WasmGC function #272.
 *
 * In real Java, java.awt.Point extends java.awt.geom.Point2D, so passing a
 * Point where a Point2D is expected is legal. TeaVM's classlib replaces
 * java.awt.Point with TPoint, which extends java.lang.Object directly
 * (no getX/getY, no Point2D supertype). TeaVM therefore emits a WasmGC
 * module in which a (ref null Point) value is passed to a call_ref whose
 * declared parameter type is (ref null Point2D) - two sibling struct types
 * under Object - and Node rejects the module at validation time.
 *
 * This mirrors org.audiveris.omr.sig.inter.AbstractInter.contains(Point),
 * which calls area.contains(point) with a Point.
 */
public class Repro {
    /** Virtual call through a vtable, like Area.contains in AbstractInter. */
    static boolean check(Area area, Point p) {
        return area.contains(p);
    }

    /** Same call, but through an explicitly Point2D-typed local. */
    static boolean checkViaPoint2D(Area area, Point p) {
        Point2D p2d = p;
        return area.contains(p2d);
    }

    public static void main(String[] args) {
        Area area = new Area();
        Point p = new Point(3, 4);
        System.out.println("direct: " + check(area, p));
        System.out.println("via Point2D local: " + checkViaPoint2D(area, p));
    }
}
