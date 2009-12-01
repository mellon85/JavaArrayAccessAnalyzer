package staticAnalyzer;

import java.util.*;
import org.apache.bcel.classfile.*;
import java.io.*;

class State implements Cloneable, Serializable {
    // stack of the data
    private Stack<Variable> stack = new Stack<Variable>();
    
    // local variables, constants, fields, static fields
    private Vector<Variable> variables     = new Vector<Variable>();
    private Vector<Variable> constants     = new Vector<Variable>();
    //private Vector<Variable> fields        = new Vector<Variable>();
    //private Vector<Variable> static_fields = new Vector<Variable>();
    
    // The state is built for analyzing this method of this class
    private Method    method;
    //private JavaClass jclass;
    
    private int pc = 0;
    private int jump = -1;

    private Variable result = null;

    private State() {}

    public State( Method m, JavaClass j ) {
        assert m != null;
        assert j != null;

        method = m;
        //jclass = j;
        
        if ( ! method.isStatic() ) {
            variables.add(0,new Variable("L"+j.getClassName()
                                        ,Variable.Kind.LOCAL
                                        ,Variable.DomainValue.TOP,0,0));
        }
        
        // Initialize the constant pool
        ConstantPool cpool = m.getConstantPool();
        for( int idx = 0; idx < cpool.getLength(); idx++ ) {
            Constant c = cpool.getConstant(idx);
            if( c instanceof ConstantDouble ) {
                double d = (Double)((ConstantDouble)c).getConstantValue(cpool);
                if ( d >= 0 ) {
                    constants.add(new Variable("D",Variable.Kind.CONST
                                          ,Variable.DomainValue.GEQ0,idx,0));
                } else if ( d > 0 ) {
                    constants.add(new Variable("D",Variable.Kind.CONST
                                          ,Variable.DomainValue.G0,idx,0));
                } else {
                    constants.add(new Variable("D",Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
                }
            } else if ( c instanceof ConstantInteger ) {
                int d = (Integer)((ConstantInteger)c).getConstantValue(cpool);
                if ( d >= 0 ) {
                    constants.add(new Variable("I",Variable.Kind.CONST
                                          ,Variable.DomainValue.GEQ0,idx,0));
                } else if ( d > 0 ) {
                    constants.add(new Variable("I",Variable.Kind.CONST
                                          ,Variable.DomainValue.G0,idx,0));
                } else {
                    constants.add(new Variable("I",Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
                }
            } else if ( c instanceof ConstantLong ) {
                long d = (Long)((ConstantLong)c).getConstantValue(cpool);
                if ( d >= 0 ) {
                    constants.add(new Variable("J",Variable.Kind.CONST
                                          ,Variable.DomainValue.GEQ0,idx,0));
                } else if ( d > 0 ) {
                    constants.add(new Variable("J",Variable.Kind.CONST
                                          ,Variable.DomainValue.G0,idx,0));
                } else {
                    constants.add(new Variable("J",Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
                }
            } else if ( c instanceof ConstantFloat ) {
                float d = (Float)((ConstantFloat)c).getConstantValue(cpool);
                if ( d >= 0 ) {
                    constants.add(new Variable("J",Variable.Kind.CONST
                                          ,Variable.DomainValue.GEQ0,idx,0));
                } else if ( d > 0 ) {
                    constants.add(new Variable("J",Variable.Kind.CONST
                                          ,Variable.DomainValue.G0,idx,0));
                } else {
                    constants.add(new Variable("J",Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
                }
            } else if ( c instanceof ConstantString ) {
                constants.add(new Variable("Ljava/lang/String",Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
            } else if ( c instanceof ConstantUtf8 ) {
                constants.add(new Variable("Ljava/lang/String",Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
            } else if ( c instanceof ConstantClass ) {
                ConstantClass cc = (ConstantClass)c;
                Object b = cc.getConstantValue(cpool);
                constants.add(new Variable("L"+b.getClass().getName()
                                          ,Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
            } else if ( c instanceof ConstantCP ) {
                ConstantCP cc = (ConstantCP)c;
                constants.add(new Variable(
                            cc.getClassIndex()+"|"+cc.getNameAndTypeIndex()
                                          ,Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
            } else if ( c == null ) {
                constants.add(new Variable("V",Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
            } else {
                assert c instanceof ConstantNameAndType;
                ConstantNameAndType cc = (ConstantNameAndType)c;
                constants.add(new Variable(
                            cc.getSignature(cpool)+"|"+cc.getName(cpool)
                                          ,Variable.Kind.CONST
                                          ,Variable.DomainValue.TOP,idx,0));
            }
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
        System.out.println(a.stack.size()+" "+stack.size());
        assert a.stack.size() == stack.size();
        
        boolean changed = false;

        if( pc < a.pc ) {
            pc = a.pc;
        }

        for( int i = 0; i < stack.size(); i++ ) {
            Variable v = stack.get(i);
            Variable va = a.stack.get(i);
            if ( v.intersect(va,this) ) 
                changed = true;
            stack.set(i,v); 
        }

        for( int i = 0; i < variables.size(); i++ ) {
            Variable v = variables.get(i);
            Variable va = a.variables.get(i);
            if ( v.intersect(va,this) ) 
                changed = true;
            variables.set(i,v);
        }
        return changed;
    }

    public Variable loadConstant( int index ) {
        assert index < constants.size();
        return constants.get(index);
    }

    public Variable load( int index ) {
        assert variables.size() > index;
        return variables.get(index);
    }
    
    public Variable load( Variable arrayref, Variable index ) {
        // load an element from a vector
        return new Variable(arrayref.getType().substring(1)
                           ,Variable.Kind.LOCAL,Variable.DomainValue.TOP
                           ,Integer.MAX_VALUE,0);
    }

    public void store( int index, Variable v ) {
        assert index <= variables.size();
        if ( index == variables.size() ) {
            variables.add(v);
        } else {
            variables.set(index,v);
        }
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
