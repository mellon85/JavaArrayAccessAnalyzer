package staticAnalyzer;

/**
 * Standalone unit test for Variable class.
 * Focuses on testing the div implementation.
 */
public class VariableTest {
    public static void main(String[] args) {
        System.out.println("Running VariableTest...");
        boolean passed = true;
        passed &= testDivBasic();
        passed &= testDivSafePropagation();
        passed &= testDivEdgePropagation();
        passed &= testDivTop();
        passed &= testDivNullSafeList();

        if (passed) {
            System.out.println("All tests passed.");
        } else {
            System.out.println("Some tests failed.");
            System.exit(1);
        }
    }

    private static boolean testDivBasic() {
        System.out.print("testDivBasic: ");
        Variable v1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 1, 0);
        Variable v2 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 2, 0);

        Variable res = v1.div(v2);
        if (res.getDomainValue() == Variable.DomainValue.GEQ0) {
            System.out.println("PASS");
            return true;
        } else {
            System.out.println("FAIL (Expected GEQ0, got " + res.getDomainValue() + ")");
            return false;
        }
    }

    private static boolean testDivSafePropagation() {
        System.out.print("testDivSafePropagation: ");
        Variable v1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 1, 0);
        Variable v2 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 2, 0);
        Variable s = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 3, 0);

        v1.addSafe(s); // v1 < s

        // v1 / v2 <= v1 < s  => result should be safe w.r.t s
        Variable res = v1.div(v2);

        if (res.isSafe(s)) {
            System.out.println("PASS");
            return true;
        } else {
            System.out.println("FAIL (Expected result to be safe w.r.t s)");
            return false;
        }
    }

    private static boolean testDivEdgePropagation() {
        System.out.print("testDivEdgePropagation: ");
        Variable v1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 1, 0);
        Variable v2 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 2, 0);
        Variable e = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 3, 0);

        v1.addEdge(e); // v1 <= e

        // v1 / v2 <= v1 <= e => result should be edge w.r.t e
        Variable res = v1.div(v2);

        if (res.isEdge(e)) {
            System.out.println("PASS");
            return true;
        } else {
            System.out.println("FAIL (Expected result to be edge w.r.t e)");
            return false;
        }
    }

    private static boolean testDivTop() {
        System.out.print("testDivTop: ");
        Variable v1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.TOP, 1, 0);
        Variable v2 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 2, 0);

        Variable res = v1.div(v2);
        if (res.getDomainValue() == Variable.DomainValue.TOP) {
            System.out.println("PASS");
            return true;
        } else {
            System.out.println("FAIL (Expected TOP, got " + res.getDomainValue() + ")");
            return false;
        }
    }

    private static boolean testDivNullSafeList() {
        System.out.print("testDivNullSafeList: ");
        // Ensure no NPE when safe/edge lists are null (default state)
        Variable v1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 1, 0);
        Variable v2 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 2, 0);

        try {
            v1.div(v2);
            System.out.println("PASS");
            return true;
        } catch (NullPointerException e) {
            System.out.println("FAIL (NPE thrown)");
            e.printStackTrace();
            return false;
        }
    }
}
