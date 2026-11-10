package repro;
// Minimal repro: TeaVM TMath.hypot violates the JDK spec for infinite/NaN args.
// JDK (java.lang.Math.hypot): if either arg is infinite -> +Inf; else if either
// is NaN -> NaN; else sqrt(x*x+y*y). Old TeaVM: sqrt(x*x+y*y) always, so
// hypot(NaN, +Inf) = NaN instead of +Inf.
public class HypotMain {
    public static void main(String[] args) {
        System.out.println("hypot(NaN,Inf)=" + Math.hypot(Double.NaN, Double.POSITIVE_INFINITY) + " expected=Infinity");
        System.out.println("hypot(Inf,NaN)=" + Math.hypot(Double.POSITIVE_INFINITY, Double.NaN) + " expected=Infinity");
        System.out.println("hypot(NaN,1.0)=" + Math.hypot(Double.NaN, 1.0) + " expected=NaN");
        System.out.println("hypot(Inf,1.0)=" + Math.hypot(Double.POSITIVE_INFINITY, 1.0) + " expected=Infinity");
        System.out.println("hypot(3,4)=" + Math.hypot(3.0, 4.0) + " expected=5.0");
        System.out.println("hypot(0,0)=" + Math.hypot(0.0, 0.0) + " expected=0.0");
    }
}
