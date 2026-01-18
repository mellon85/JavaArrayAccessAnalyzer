package staticAnalyzer;

import java.util.*;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ClassNode;

class MethodSignature {
    private MethodNode m;
    private ClassNode c;

    public MethodSignature(MethodNode m, ClassNode c) {
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
        return m.name+" "+m.desc+" "+c.name;
    }

    public int hashCode() {
        String hash = m.name+" "+m.desc+" "+c.name;
        int v = hash.hashCode();
        return v;
    }
}
