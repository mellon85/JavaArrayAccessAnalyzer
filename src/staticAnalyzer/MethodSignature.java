package staticAnalyzer;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.*;

class MethodSignature {
    private Method m;
    private JavaClass c;

    public MethodSignature(Method m, JavaClass c) {
        this.m = m;
        this.c = c;
    }

    public boolean equals( Object o ) {
        if ( ! (o instanceof MethodSignature) ) 
            throw new RuntimeException("The object wasn't comparable");

        MethodSignature om = (MethodSignature)o;
        
        if ( ! om.toString().equals(this.toString()) ) {
            return false;
        } else {
            return true;
        }
    }

    public String toString() {
        return m.getName()+" "+m.getSignature()+" "+c.getClassName();
    }

    public int hashCode() {
        String hash = m.getName()+" "+m.getSignature()+" "+c.getClassName();
        int v = hash.hashCode();
        return v;
    }
    /*
    public static Vector<Variable> setupVariables( Vector<Variable> v ) {
        //@TODO must setup the vector of variables to have only dependencies
        // beertwen them.
        Vector<Variable> vv = new Vector<Variable>();

        // build set of variables for faster lookup
        Set<Variable> variable_table = new HashSet<Variable>();
        for( Variable i : v ) {
            variable_table.put(v);
        }

        // clean safe and edge of the variables
        for( Variable i : v ) {
            Iterator<Variable> j = i.safe.iterator();
            while( j.hasNext() ) {
                Variable t = j.next();
                if( ! variable_table.contains(t) ) {
                    j.remove();
                }
            }
            j = i.edge.iterator();
            while( j.hasNext() ) {
                Variable t = j.next();
                if( ! variable_table.contains(t) ) {
                    j.remove();
                }
            }
            vv.add(i.);
        }

        return vv;
    }
    */
}
