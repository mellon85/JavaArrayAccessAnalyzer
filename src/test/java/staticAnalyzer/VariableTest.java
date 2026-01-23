package staticAnalyzer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VariableTest {

    @Test
    public void testDivNPE() {
        Variable v1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 1, 0);
        Variable v2 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 2, 0);

        // This is expected to fail with NPE before fix
        assertDoesNotThrow(() -> v1.div(v2), "div should not throw NPE even if safe/edge lists are null");
    }

    @Test
    public void testDivLogic() {
        Variable v1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 1, 0);
        Variable v2 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.G0, 2, 0);

        Variable s1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.TOP, 3, 0);
        Variable e1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.TOP, 4, 0);

        v1.addSafe(s1);
        v1.addEdge(e1);

        Variable res = v1.div(v2);

        // Verify result is GEQ0
        assertEquals(Variable.DomainValue.GEQ0, res.getDomainValue());

        // Verify bounds propagation (v1 / v2 <= v1 < s1) => res < s1
        assertTrue(res.isSafe(s1), "Result should be safe for s1");

        // Verify bounds propagation (v1 / v2 <= v1 <= e1) => res <= e1
        assertTrue(res.isEdge(e1), "Result should be edge for e1");
    }

    @Test
    public void testDivBottom() {
        Variable v1 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.BOTTOM, 1, 0);
        Variable v2 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 2, 0);

        Variable res = v1.div(v2);
        assertEquals(Variable.DomainValue.BOTTOM, res.getDomainValue());

        Variable v3 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.GEQ0, 3, 0);
        Variable v4 = new Variable("I", Variable.Kind.LOCAL, Variable.DomainValue.BOTTOM, 4, 0);

        Variable res2 = v3.div(v4);
        assertEquals(Variable.DomainValue.BOTTOM, res2.getDomainValue());
    }
}
