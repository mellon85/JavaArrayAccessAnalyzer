package staticAnalyzer;

import java.util.*;
import java.io.*;

class Variable implements Serializable, Cloneable {
    private String type; // java type signature as defined here
    // http://java.sun.com/j2se/1.5.0/docs/guide/jni/spec/types.html

    List<Variable> safe = null;
    List<Variable> edge = null;

    // value in the abstract domain
    public enum DomainValue {BOTTOM, G0, GEQ0, TOP;
            boolean geq(DomainValue b) {
                DomainValue a = this;
                // a >= b
                if( a == TOP )
                    return true;
                if( a == BOTTOM ) 
                    return false;
                if( a == G0 ) {
                    return b == GEQ0 ? false : true;
                } else {
                    assert a == GEQ0;
                    return b == TOP ? false : true;
                }
            }
        }; 
    private DomainValue value; 

    /* At the moment analysis of static and non static fields are
     * avoided. they are always considered as TOP.
     * It is possible to analyze private fields analyzing how they are
     * treated in every call classwide.
     * Protected requires a package and subclass wide analysis, public
     * is impossibile (if not in an application driver analysis) */ 
    public enum Kind {LOCAL,STATIC,FIELD,CONST};
    private Kind kind;

    private int startpc;
    private int index;
     // index in the table (vector). vital for parameter
     // counting and verifing fixpoints and results I can't just return
     // a variable telling that it is safe for another variable, i need
     // to know the index of it to know if it is local (and so lost) or
     // if it was a parameter pushing the property up the call stack.

    public Variable( String type, Kind kind, DomainValue value
                   , int index, int startpc ) {
        assert type.length() > 0;

        this.type = type;
        this.kind = kind;
        this.value = value;
        this.index = index;
        this.startpc = startpc;
    }

    public DomainValue getDomainValue() {
        return value;
    }

    protected void setDomainValue( DomainValue value ) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    protected void setType( String type ) {
        this.type = type;
    }

    public boolean isLocal() {
        return kind == Kind.LOCAL;
    }

    public boolean isField() {
        return kind == Kind.FIELD;
    }

    public boolean isStatic() {
        return kind == Kind.STATIC;
    }

    public boolean isConst() {
        return kind == Kind.CONST;
    }

    public Kind getKind() {
        return kind;
    }

    protected void setKind( Kind kind ) {
        this.kind = kind;
    }

    public int getStartPC() {
        return startpc;
    }

    protected void setStartPC(int pc) {
        startpc = pc;
    }

    public int getIndex() {
        return index;
    }

    protected void setIndex( int index ) {
        this.index = index;
    }
    
    public void addSafe( Variable a ) {
        if (safe == null) {
            safe = new LinkedList<Variable>();
        }
        if ( ! safe.contains(a) ) {
            //System.out.println("adding "+a+" as safe to "+this);
            safe.add(a);
        }
    }

    public void addEdge( Variable a ) {
        if (edge == null) {
            edge = new LinkedList<Variable>();
        }
        if ( ! edge.contains(a) ) {
            edge.add(a);
        }
    }

    public boolean isSafe( Variable a ) {
        // System.out.println("testing if "+a+" is safe for "+this);
        if (safe == null || value == DomainValue.BOTTOM 
                || value == DomainValue.TOP )
            return false;
        return safe.contains(a);
    }

    public boolean isEdge( Variable a ) {
        if (edge == null || value == DomainValue.BOTTOM
                || value == DomainValue.TOP )
            return false;
        return edge.contains(a);
    }
   
    public boolean intersect( Variable v, State s ) {
        boolean changed = false;
        Iterator a,b;

        // scorrere le liste di safe e tenere quelli in comune
        if ( safe == null || v.safe == null ) {
            safe = null;
        } else {
            a = safe.iterator();
            while( a.hasNext() ) {
                Variable va = (Variable)a.next();
                boolean found = false;
                b = v.safe.iterator();
                while( b.hasNext() ) {
                    Variable vb = (Variable)b.next();
                    if( va.index == vb.index ) {
                         found = true;  
                    }
                }
                if ( found == false ) {
                   a.remove();
                   changed = true;
                }
            }
        }
        
        // scorrere le liste di edge e tenere quelli in comune
        if ( edge == null || v.edge == null ) {
            edge = null;
        } else {
            a = edge.iterator();
            while( a.hasNext() ) {
                Variable va = (Variable)a.next();
                boolean found = false;
                b = v.edge.iterator();
                while( b.hasNext() ) {
                    Variable vb = (Variable)b.next();
                    if( va.index == vb.index ) {
                         found = true;  
                    }
                }
                if ( ! found ) {
                   a.remove();
                   changed = true;
                }
            }
        }
        
        // if value < v.value
        if ( ! value.geq(v.value) ) {
            value = v.value;
            changed = true;
        }
        return changed;
    }

    public int getCategory() {
        if ( type.equals("D") || type.equals("J") ) {
            return 2;
        } else {
            return 1;
        }
    }

    // this < v
    public void cmplt( Variable v ) {
        // add all safe from v to this
        if ( v.safe != null ) {
           for( Variable s : v.safe ) {
               addSafe(s);
           }
        } 

        // add all edge from v to this as safe
        if ( v.edge != null ) {
           for( Variable s : v.edge ) {
               addSafe(s);
           }
        }
    }

    // this <= v
    public void cmple( Variable v ) {
        // add all safe from v to this
        if ( v.safe != null ) {
           for( Variable s : v.safe ) {
               addSafe(s);
           }
        } 

        // add all edge from v to this 
        if ( v.edge != null ) {
           for( Variable s : v.edge ) {
               addEdge(s);
           }
        }
    }

    // this > v
    public void cmpgt( Variable v ) {
        if (v.value == DomainValue.GEQ0) {
            value = DomainValue.G0;
        }
    }

    // this >= v
    public void cmpge( Variable v ) {
        if (v.value == DomainValue.GEQ0) {
            value = DomainValue.GEQ0;
        } else if (v.value == DomainValue.G0) {
            value = DomainValue.G0;
        }
    }

    // this == v
    public void cmpeq( Variable v ) {
        v.cmple(v);
        cmple(v);
        v.cmpge(this);
        cmpge(v);
    }

    // this += n
    public void iinc( int n ) {
        assert n != 0;
        if ( n == 1 ) {
            edge = safe;
            safe = null;
            if(value == DomainValue.GEQ0) {
                value = DomainValue.G0;
            }
        } else if ( n == -1 ) {
            edge = null;
            safe = edge;
            if(value == DomainValue.G0) {
                value = DomainValue.GEQ0;
            }
        } else if ( n > 1 ) {
            edge = null;
            safe = null;
            if (value == DomainValue.GEQ0) {
                value = DomainValue.G0;
            }
        } else {
            assert n < -1;
            edge = null;
            safe = null;
            value = DomainValue.TOP;
        }
    }

    public Variable add( Variable v ) {
        if (value == DomainValue.GEQ0 && v.value == DomainValue.GEQ0) {
            return new Variable(type,Kind.LOCAL,DomainValue.GEQ0
                    ,Integer.MAX_VALUE,0);
        }
        if ( (v.value == DomainValue.G0 || v.value == DomainValue.GEQ0)
          && (value   == DomainValue.G0 || value   == DomainValue.GEQ0)) {
            return new Variable(type,Kind.LOCAL,DomainValue.G0
                    ,Integer.MAX_VALUE,0);
        }
        return newLocal();
    }

    public Variable div( Variable v ) { //@TODO
        DomainValue dv;
        if (   getDomainValue() == DomainValue.TOP
          || v.getDomainValue() == DomainValue.TOP ) {
            dv = DomainValue.TOP;
        } else {
            dv = DomainValue.GEQ0;
        }
        Variable r = new Variable(v.type,Kind.LOCAL,dv
                ,Integer.MAX_VALUE,0);

        // analyze safe and edge
        Iterator i = safe.iterator();
        while( i.hasNext() ) {
            Variable s = (Variable)i.next();
            if (v.safe.contains(s)) {
                r.addSafe(s);
            }
        }
        i = edge.iterator();
        while( i.hasNext() ) {
            Variable s = (Variable)i.next();
            if (v.edge.contains(s)) {
                r.addEdge(s);
            }
        }
        return r;
    }

    public Variable rem( Variable v ) { //@TODO
        throw new RuntimeException("Variable.rem unimplemented");
    }

    public Variable mul( Variable v ) {
        if (value == DomainValue.GEQ0 && v.value == DomainValue.GEQ0) {
            return new Variable(type,Kind.LOCAL,DomainValue.GEQ0
                    ,Integer.MAX_VALUE,0);
        }
        if ( (v.value == DomainValue.G0 && v.value == DomainValue.GEQ0)
          && (value   == DomainValue.G0 && value   == DomainValue.GEQ0)) {
            return new Variable(type,Kind.LOCAL,DomainValue.G0
                    ,Integer.MAX_VALUE,0);
        }
        return newLocal();
    }

    public Variable ushr( Variable v ) {
        return new Variable(type,Kind.LOCAL,DomainValue.GEQ0
                ,Integer.MAX_VALUE,0);
    }

    public Variable shr( Variable v ) {
        return new Variable(type,Kind.LOCAL,DomainValue.GEQ0
                ,Integer.MAX_VALUE,0);
    }

    public Variable shl( Variable v )   { return newLocal(); }
    public Variable neg()               { return newLocal(); }
    public Variable sub( Variable v )   { return newLocal(); }
    public Variable or( Variable v )    { return newLocal(); }
    public Variable xor( Variable v )   { return newLocal(); }

    public Variable and( Variable v ) {
        if( (v.value == DomainValue.G0 || v.value == DomainValue.GEQ0)
            && (value == DomainValue.G0 || value == DomainValue.GEQ0))  {
            Variable a = new Variable(type,Kind.LOCAL
                        ,DomainValue.GEQ0,Integer.MAX_VALUE,0);
            for ( Variable s : safe   ) a.addSafe(s);
            for ( Variable s : v.safe ) a.addSafe(s);
            for ( Variable s : edge   ) a.addEdge(s);
            for ( Variable s : v.edge ) a.addEdge(s);
        }
        return newLocal(); 
    }

    private final Variable newLocal() {
        return new Variable(type,Kind.LOCAL,DomainValue.TOP
                ,Integer.MAX_VALUE,0);
    }

    public String toString() {
        String s = "Variable "+hashCode()+": value "+value+", kind "+kind+
               ", index: " +index+", "+
               "safe:";
        
        if ( safe != null ) {
            for( Variable v : safe ) {
                s += " "+v.hashCode();
            }
        }
        s += ", edge: ";
        if ( edge != null ) {
            for( Variable v : edge ) {
                s +=" "+v.hashCode();
            }
        }
        return s;
    }

    public Variable clone() {
        Variable v = new Variable(type,kind,value,index,startpc); 
        v.safe = safe;
        v.edge = edge;
        return v;
    }

    public void cleanBounds() {
        safe = null;
        edge = null;
    }

}
