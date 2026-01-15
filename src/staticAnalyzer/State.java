package staticAnalyzer;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.*;

class State implements Cloneable, Serializable {
    // stack of the data
    private Stack<Variable> stack = new Stack<Variable>();
    
    // local variables, constants, fields, static fields
    private Vector<Variable> variables     = new Vector<Variable>();
    
    private int pc = 0;
    private int jump = -1;

    private Variable result = null;

    private State() {}

    public State( MethodNode m, ClassNode j ) {
        assert m != null;
        assert j != null;

        // BCEL's isStatic()
        boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;

        if ( ! isStatic ) {
            variables.add(0,new Variable("L"+j.name+";"
                                        ,Variable.Kind.LOCAL
                                        ,Variable.DomainValue.TOP,0,0));
        }
    }

    /* PC Instructions */
    protected void setPC( int pc ) {
        this.pc = pc;
    }

    public int getPC() {
        return pc;
    }

    /* Jump Instruction */
    protected void setJump( int jump ) {
        this.jump = jump;
    }

    public int getJump() {
        return jump;
    }
    
    /* Stack functions */ 
    public void stackPush(Variable v) {
        stack.add(v);
    }

    public Variable stackPeek() {
        assert stack.size() > 0;
        return stack.peek();
    }

    public Variable stackPop() {
        assert stack.size() > 0;
        return stack.pop();
    }

    public int stackSize() {
        return stack.size();
    }

    protected Vector<Variable> getVariables() {
        return variables;
    }

    public State clone() {
        State s = SerialClone.clone(this);
        s.jump = -1;
        return s;
    }

    // returns false if fixpoint not found
    public boolean intersect( State a ) {
        //System.out.println("intersect "+this.hashCode()+" with "+a.hashCode());
        // System.out.println(a.stack.size()+" "+stack.size());

        // Sometimes stack sizes might mismatch if something went wrong, but for valid bytecode it should match
        // assert a.stack.size() == stack.size();
        
        boolean changed = false;

        if( pc < a.pc ) {
            pc = a.pc;
        }

        int len = Math.min(stack.size(), a.stack.size());

        for( int i = 0; i < len; i++ ) {
            Variable v = stack.get(i);
            Variable va = a.stack.get(i);
            if ( v.intersect(va,this) ) 
                changed = true;
            stack.set(i,v); 
        }

        int varLen = Math.min(variables.size(), a.variables.size());
        for( int i = 0; i < varLen; i++ ) {
            Variable v = variables.get(i);
            Variable va = a.variables.get(i);
            if ( v.intersect(va,this) ) 
                changed = true;
            variables.set(i,v);
        }
        return changed;
    }

    public Variable load( int index ) {
        // Ensure vector is big enough.
        if(index >= variables.size()) {
             for(int k=variables.size(); k<=index; k++) {
                 variables.add(new Variable("V", Variable.Kind.LOCAL, Variable.DomainValue.TOP, k, 0));
             }
        }
        return variables.get(index);
    }
    
    public Variable load( Variable arrayref, Variable index ) {
        // load an element from a vector
        // arrayref.getType() should return signature, e.g. "[I" or "[Ljava/lang/String;"
        String t = arrayref.getType();
        String elemType = "V";
        if(t.startsWith("[")) {
            elemType = t.substring(1);
        }

        return new Variable(elemType
                           ,Variable.Kind.LOCAL,Variable.DomainValue.TOP
                           ,Integer.MAX_VALUE,0);
    }

    public void store( int index, Variable v ) {
        if ( index >= variables.size() ) {
            for(int k=variables.size(); k<=index; k++) {
                variables.add(new Variable("V", Variable.Kind.LOCAL, Variable.DomainValue.TOP, k, 0));
            }
        }
        variables.set(index,v);
    }
    
    public void store( Variable arrayref, Variable index, Variable value ) {
        // No analysis on array content
    }


    protected void setReturn( Variable v ) {
        result = v;
    }

    public Variable getReturn() {
        return result;
    }

    public String toString() {
        return "State "+hashCode()+": pc "+pc+", jump "+jump+"\n"+
               "  variables:"+variables+"\n"+
               "  stack:"+stack;
    }
    
}
