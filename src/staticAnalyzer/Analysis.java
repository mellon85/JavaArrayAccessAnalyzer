package staticAnalyzer;

import java.util.*;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.*;

// http://java.sun.com/j2se/1.5.0/docs/guide/jni/spec/types.html
// http://jakarta.apache.org/bcel/apidocs/index.html

class Analysis {

    private Analyzer analyzer;
    private Vector<BadArrayAccess> reports = new Vector<BadArrayAccess>();
    private Set<MethodSignature> methods = new HashSet<MethodSignature>();
    private Hashtable<MethodSignature,Variable> method_result = 
                new Hashtable<MethodSignature,Variable>();

    public Analysis( Analyzer analyzer ) {
        this.analyzer = analyzer;
    }

    protected void analyzeMethods( JavaClass jclass ) {
        //System.out.println(jclass.getMethods().length+" methods to check");
        for( Method m : jclass.getMethods() ) {
            // skips method already analyzed
            if ( method_result.get(new MethodSignature(m,jclass)) == null )
                analyzeMethod(m,jclass);
        }
        //System.out.println(methods);
    }

    protected boolean analyzeMethod( Method m, JavaClass j ) {
        return analyzeMethod(m,j,null);
    }

    protected boolean analyzeMethod( Method m, JavaClass j,
            Vector<Variable> parameters) {
        InstructionList il = new InstructionList(m.getCode().getCode());
        MethodSignature ms = new MethodSignature(m,j);
        boolean recursive = false;
        Variable ret = null;
        
        if( methods.contains(ms) ) {
            recursive = true;
            // return TOP since we don't know what to return!
            ret = new Variable(m.getReturnType().getSignature()
                               ,Variable.Kind.LOCAL
                               ,Variable.DomainValue.TOP
                               ,Integer.MAX_VALUE,0);
        } else if ( m.isNative() ) {
            // If method is native i don't know anything about it.
            // If the method is abstract i don't have the runtime
            //   information to choose a method to analyze.
            ret = new Variable(m.getReturnType().getSignature()
                              ,Variable.Kind.LOCAL
                              ,Variable.DomainValue.TOP
                              ,Integer.MAX_VALUE,0);
        } else if ( m.isAbstract() ) {
            throw new RuntimeException("Analyzing abstract method!");
        } else {
            //@DEBUG
            System.out.println("Analyzing "+m.getName());
            System.out.println(""+il);

            methods.add(ms);
            InstructionHandle pc = il.getStart();

            State state = new State(m,j);
            
            if ( parameters == null ) {
                // setup start,state for the method call when in app analysis.
                // no need of anything in library (default for now) analysis.
                Type tys[] = m.getArgumentTypes();
                int i = 0;
                for( Type ty : tys ) {
                    state.getVariables().add(new Variable(ty.getSignature()
                                            ,Variable.Kind.LOCAL
                                            ,Variable.DomainValue.TOP
                                            ,i,0));
                    i++;
                }
            } else {
                // there are some arguments for the function
                for( Variable v : parameters ) {
                    state.getVariables().add(v);
                }
            }

            // analyze method instructions
            state = analyzeInstructions(m,il
                    ,pc.getPosition(),il.getEnd().getPosition(),state,j);
            ret = state.getReturn();
        }
        // remove method from list of called methods in the stack
        methods.remove(ms);
        method_result.put(ms,ret);
        return recursive;
    }

    protected State analyzeInstructions( Method m, InstructionList il
                                       , int start_pc, int end_pc
                                       , State s
                                       , JavaClass j ) {
        InstructionHandle pc = il.findHandle(start_pc);
        Instruction i = pc.getInstruction();
        int pci = pc.getPosition();
        //System.out.println("analyze: start "+start_pc+", end "+end_pc);

        while(true) {
            pci = pc.getPosition();
            if (pci > end_pc || pc == null) {
                return s;
            }
            i = pc.getInstruction();
            s.setPC(pci);
            
            System.out.println(pci);
            if ( i instanceof FCMPG  
              || i instanceof FCMPL ) {
                Variable v1,v2;
                v1 = s.stackPop();
                v2 = s.stackPop();
                assert v1.getType().equals("F");
                assert v2.getType().equals("F");
                s.stackPush(new Variable("I",Variable.Kind.CONST,
                            Variable.DomainValue.TOP,Integer.MAX_VALUE,pci));
            } else if ( i instanceof DCMPL
                     || i instanceof DCMPG ) {
                Variable v1,v2;
                v1 = s.stackPop();
                v2 = s.stackPop();
                assert v1.getType().equals("D");
                assert v2.getType().equals("D");
                s.stackPush(new Variable("I",Variable.Kind.CONST,
                                Variable.DomainValue.TOP,Integer.MAX_VALUE,pci));
            } else if ( i instanceof ConstantPushInstruction ) {
                ConstantPushInstruction icpi = (ConstantPushInstruction)i;
                ConstantPoolGen gen = new ConstantPoolGen(m.getConstantPool());
                int n = icpi.getValue().intValue();
                String signature = icpi.getType(gen).getSignature();
                Variable.DomainValue f;
                if ( n > 0 ) {
                    f = Variable.DomainValue.G0;
                } else if ( n >= 0 ) {
                    f = Variable.DomainValue.GEQ0;
                } else {
                    f = Variable.DomainValue.TOP;
                }
                Variable v = new Variable(signature,Variable.Kind.CONST
                                         ,f,Integer.MAX_VALUE
                                         ,pci);
                s.stackPush(v);
            } else if ( i instanceof ACONST_NULL ) {
                s.stackPush(new Variable("Ljava/lang/Object",Variable.Kind.CONST
                                         ,Variable.DomainValue.TOP
                                         ,Integer.MAX_VALUE,pci));
            } else if ( i instanceof LocalVariableInstruction ) {
                if ( i instanceof LoadInstruction ) {
                    LoadInstruction li = (LoadInstruction)i;
                    s.stackPush(s.load(li.getIndex()));
                } else if ( i instanceof StoreInstruction ) {
                    StoreInstruction si = (StoreInstruction)i;
                    Variable v = s.stackPop();
                    v.setIndex(si.getIndex());
                    v.setStartPC(pc.getNext().getPosition());
                    v.setKind(Variable.Kind.LOCAL);
                    s.store(si.getIndex(),v);
                } else if ( i instanceof IINC ) {
                    IINC iinc = (IINC)i;
                    Variable v = s.load(iinc.getIndex());
                    v.iinc(iinc.getIncrement());
                } else {
                    throw new RuntimeException(
                            "Unkown Local Variable instruction "+pci);
                }
            } else if ( i instanceof StackInstruction ) {
                if ( i instanceof DUP ) {
                    Variable top = s.stackPeek();
                    assert top.getCategory() == 1;
                    s.stackPush(top);
                } else if ( i instanceof DUP_X1 ) {
                    Variable top_0 = s.stackPop();
                    Variable top_1 = s.stackPop();
                    assert top_0.getCategory() == 1;
                    assert top_1.getCategory() == 1;
                    s.stackPush(top_0);
                    s.stackPush(top_1);
                    s.stackPush(top_0);
                } else if ( i instanceof DUP_X2 ) {
                    Variable top_0 = s.stackPop();
                    Variable top_1 = s.stackPop();
                    Variable top_2 = null; // s.stackPop();
                    if ( top_0.getCategory() == 1 && top_1.getCategory() == 2 ) {
                        s.stackPush(top_0);
                        s.stackPush(top_1);
                        s.stackPush(top_0);
                    } else {
                        top_2 = s.stackPop();
                        assert top_0.getCategory() == 1;
                        assert top_1.getCategory() == 1;
                        assert top_2.getCategory() == 1;
                        s.stackPush(top_0);
                        s.stackPush(top_2);
                        s.stackPush(top_1);
                        s.stackPush(top_0);
                    }
                } else if ( i instanceof DUP2 ) {
                    Variable top = s.stackPeek();
                    if ( top.getCategory() == 2) { // 1 category 2
                        s.stackPush(top);
                    } else { // 2 category 1
                        Variable top_0 = s.stackPop();
                        Variable top_1 = s.stackPop();
                        
                        assert top_0.getCategory() == 1;
                        assert top_1.getCategory() == 1;

                        s.stackPush(top_1);
                        s.stackPush(top_0);
                        s.stackPush(top_1);
                        s.stackPush(top_0);
                    }
                } else if ( i instanceof DUP2_X1 ) {
                    Variable top_0 = s.stackPop();
                    Variable top_1 = s.stackPop();
                    Variable top_2 = null;
                    if ( top_0.getCategory() == 2 && top_1.getCategory() == 1 ) {
                        s.stackPush(top_0);
                        s.stackPush(top_1);
                        s.stackPush(top_0);
                    } else {
                        top_2 = s.stackPop();
                        assert top_0.getCategory() == 1;
                        assert top_1.getCategory() == 1;
                        assert top_2.getCategory() == 1;
                        s.stackPush(top_1);
                        s.stackPush(top_0);
                        s.stackPush(top_2);
                        s.stackPush(top_1);
                        s.stackPush(top_0);
                    }
                } else if ( i instanceof DUP2_X2 ) {
                    Variable top_0 = s.stackPop();
                    Variable top_1 = s.stackPop();
                    Variable top_2 = null;
                    Variable top_3 = null;
                    if ( top_0.getCategory() == 2 && top_1.getCategory() == 2 ) {
                        s.stackPush(top_0); // form 4
                        s.stackPush(top_1);
                        s.stackPush(top_0);
                    } else {
                        top_2 = s.stackPop();
                        if ( top_0.getCategory() == 1 && top_1.getCategory() == 1
                             && top_2.getCategory() == 2 ) {
                            s.stackPush(top_1); // form 3
                            s.stackPush(top_0);
                            s.stackPush(top_2);
                            s.stackPush(top_1);
                            s.stackPush(top_0);
                        } else if ( top_0.getCategory() == 2 
                                 && top_1.getCategory() == 1
                                 && top_2.getCategory() == 1 ) { // form 2
                            s.stackPush(top_0); 
                            s.stackPush(top_2);
                            s.stackPush(top_1);
                            s.stackPush(top_0);
                        } else { // form 1
                            top_3 = s.stackPop();
                            assert top_0.getCategory() == 1;
                            assert top_1.getCategory() == 1;
                            assert top_2.getCategory() == 1;
                            assert top_3.getCategory() == 1;
                            s.stackPush(top_1);
                            s.stackPush(top_0);
                            s.stackPush(top_3);
                            s.stackPush(top_2);
                            s.stackPush(top_1);
                            s.stackPush(top_0);
                        }
                    }
                } else if ( i instanceof POP ) {
                    Variable top = s.stackPop();
                    assert top.getCategory() == 1;
                } else if ( i instanceof POP2 ) {
                    Variable top = s.stackPop();
                    if ( top.getCategory() == 1 ) {
                        Variable top_1 = s.stackPop();
                        assert top_1.getCategory() == 1;
                    }
                } else if ( i instanceof SWAP ) {
                    Variable a = s.stackPop();
                    Variable b = s.stackPop();

                    assert a.getCategory() == 1;
                    assert b.getCategory() == 1;

                    s.stackPush(a);
                    s.stackPush(b);
                }
            } else if ( i instanceof ArrayInstruction ) {
                Variable value = null;
                if ( i instanceof StackConsumer ) { // store command
                    // eliminate value from stack.
                    value = s.stackPop();
                }
                Variable index    = s.stackPop(); // index variable
                Variable arrayref = s.stackPop(); // array reference

                // check if the index is safe
                if ( ! index.isSafe(arrayref) ) {
                    // load is not safe, add report.
                    makeNewReport(m,j,pc.getPosition());
                    //System.out.println("Access error found here");
                    // mark index as safe for arrayref.
                    index.addSafe(arrayref);
                }
                if ( i instanceof StackProducer ) {
                    s.stackPush(s.load(arrayref,index));
                } else if ( i instanceof StackConsumer ) {
                    s.store(arrayref,index,value);
                } else {
                    throw new RuntimeException(
                            "Unknown Array instruction "+pci);
                }
            } else if ( i instanceof ArithmeticInstruction ) {
                Variable v1,v2,v;
                v1 = v2 = s.stackPop(); // first value on the stack
                if( i instanceof DADD || i instanceof FADD
                 || i instanceof IADD || i instanceof LADD ) {
                    v1 = s.stackPop();
                    v = v1.add(v2);
                } else if ( i instanceof DDIV || i instanceof FDIV
                         || i instanceof IDIV || i instanceof LDIV ) {
                    v1 = s.stackPop();
                    v = v1.div(v2);
                } else if ( i instanceof DMUL || i instanceof FMUL
                         || i instanceof IMUL || i instanceof LMUL ) {
                    v1 = s.stackPop();
                    v = v1.mul(v2);
                } else if ( i instanceof DNEG || i instanceof FNEG
                         || i instanceof INEG || i instanceof LNEG ) {
                    v = v2.neg();
                } else if ( i instanceof DREM || i instanceof FREM 
                         || i instanceof IREM || i instanceof LREM ) {
                    v1 = s.stackPop();
                    v = v1.rem(v2);
                } else if ( i instanceof DSUB || i instanceof FSUB 
                         || i instanceof ISUB || i instanceof LSUB ) {
                    v1 = s.stackPop();
                    v = v1.sub(v2);
                } else if ( i instanceof IAND || i instanceof LAND ) {
                    v1 = s.stackPop();
                    v = v1.and(v2);
                } else if ( i instanceof ISHL || i instanceof LSHL ) {
                    v1 = s.stackPop();
                    v = v1.shl(v2);
                } else if ( i instanceof ISHR || i instanceof LSHR ) {
                    v1 = s.stackPop();
                    v = v1.shr(v2);
                } else if ( i instanceof IUSHR || i instanceof LUSHR ) {
                    v1 = s.stackPop();
                    v = v1.ushr(v2);
                } else if ( i instanceof IOR || i instanceof LOR ) {
                    v1 = s.stackPop();
                    v = v1.or(v2);
                } else if (i instanceof IXOR || i instanceof LXOR) {
                    v1 = s.stackPop();
                    v = v1.xor(v2);
                } else {
                    throw new RuntimeException(
                            "Unknown arithmetic bytecode "+pci);
                }
                v.setStartPC(pci);
                s.stackPush(v);
            } else if ( i instanceof BranchInstruction ) {
                if ( i instanceof IfInstruction ) {
                    Variable v1,v2;

                    State false_branch = s.clone();
                    State true_branch  = s;
                    
                    IfInstruction ifi = (IfInstruction)i;
                    InstructionHandle true_ih = ifi.getTarget();

                    if( i instanceof IF_ICMPEQ ) {
                        { // true v1 == v2
                            v2 = true_branch.stackPop();
                            v1 = true_branch.stackPop();
                            v2.cmpeq(v1);
                        }
                        { // false v1 ≠ v2
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                        }
                    } else if ( i instanceof IF_ICMPNE ) {
                        { // true v1 ≠ v2
                            true_branch.stackPop();
                            true_branch.stackPop();
                        }
                        { // false v1 = v2
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                            v2.cmpeq(v1);
                        }
                    } else if ( i instanceof IF_ICMPGE ) {
                        { // true v1 >= v2
                            v2 = true_branch.stackPop();
                            v1 = true_branch.stackPop();
                            v1.cmpge(v2); // v1 >= v2
                            v2.cmple(v1); // v2 <= v1
                        }
                        { // false v1 < v2
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                            v1.cmplt(v2); // v1 <  v2
                            v2.cmpge(v1); // v2 >= v1
                        }
                    } else if ( i instanceof IF_ICMPGT ) {
                        { // true v1 > v2
                            v2 = true_branch.stackPop();
                            v1 = true_branch.stackPop();
                            v1.cmpgt(v2); // v1 >  v2
                            v2.cmple(v1); // v2 <= v1
                        }
                        { // false v1 <= v2
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                            v1.cmple(v2); // v1 <= v2
                            v2.cmpge(v1); // v2 >= v1
                        }
                    } else if ( i instanceof IF_ICMPLE ) {
                        { // true v1 <= v2
                            v2 = true_branch.stackPop();
                            v1 = true_branch.stackPop();
                            v1.cmple(v2); // v1 <= v2
                            v2.cmpge(v1); // v2 >= v1
                        }
                        { // false v1 > v2
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                            v1.cmpgt(v2); // v1 >  v2
                            v2.cmple(v1); // v2 <= v1
                        }
                    }  else if ( i instanceof IF_ICMPLT ) {
                        { // true v1 < v2
                            v2 = true_branch.stackPop();
                            v1 = true_branch.stackPop();
                            v1.cmplt(v2); // v1 <  v2
                            v2.cmpge(v1); // v2 >= v1
                        }
                        { // false v1 >= v2
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                            v1.cmpge(v2); // v1 >= v2
                            v2.cmple(v1); // v2 <= v1
                        }
                    } else if ( i instanceof IFLT ) {
                        { // true < 0
                            v1 = true_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.TOP);
                        }
                        { // false >= 0
                            v1 = false_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                    } else if ( i instanceof IFEQ ) {
                        { // true =0
                            v1 = true_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                        { // false ≠0
                            v1 = false_branch.stackPop();
                        }
                    } else if ( i instanceof IFGE ) {
                        { // true >=0
                            v1 = true_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                        { // false <0
                            v1 = false_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.TOP);
                        }
                    } else if ( i instanceof IFGT ) {
                        { // true >0
                            v1 = true_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.G0);
                        }
                        { // false <=0 
                            v1 = false_branch.stackPop();
                            if( v1.getDomainValue() != Variable.DomainValue.GEQ0 ) {
                                v1.setDomainValue(Variable.DomainValue.TOP);
                            }
                        }
                    } else if ( i instanceof IFLE ) {
                        { // true <=0
                            v1 = true_branch.stackPop();
                            if( v1.getDomainValue() != Variable.DomainValue.GEQ0 ) {
                                v1.setDomainValue(Variable.DomainValue.TOP);
                            }
                        }
                        { // false >0 
                            v1 = false_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.G0);
                        }
                    } else if ( i instanceof IFLT ) {
                        { // true <0
                            v1 = true_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.TOP);
                        }
                        { // false >=0 
                            v1 = false_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                    } else if ( i instanceof IFNE ) {
                        { // true ≠0
                            v1 = true_branch.stackPop();
                        }
                        { // false =0 
                            v1 = false_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                    } else if ( i instanceof IFNULL 
                             || i instanceof IFNONNULL ) {
                        { // true
                            v1 = true_branch.stackPop();
                        }
                        { // false
                            v1 = false_branch.stackPop();
                        }
                    } else if ( i instanceof IF_ACMPEQ 
                             || i instanceof IF_ACMPNE ) {
                        { // true
                            v2 = true_branch.stackPop();
                            v1 = true_branch.stackPop();
                        }
                        { // false
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                        }
                    } else {
                        throw new RuntimeException("Unknown if bytecode");
                    }
                    // System.out.println("false_branch "+false_branch);
                    // System.out.println("true_branch "+true_branch);

                    //System.out.println("analyzing false"); 
                    analyzeInstructions(m,il,pc.getNext().getPosition()
                           ,min(true_ih.getPrev().getPosition(),end_pc)
                           ,false_branch,j);
                    // System.out.println(false_branch);

                    // there is a then only if the else finished with a goto.
                    int jump = false_branch.getJump();
                    if( jump >= pci ) { // there has been a goto!
                        // System.out.println(false_branch);
                        //System.out.println("analyzing true");
                        pc  = il.findHandle(false_branch.getPC()).getNext();
                        analyzeInstructions(m,il,pc.getPosition()
                                           ,min(end_pc,jump-1),true_branch,j);
                        // true_branch loop
                        jump = true_branch.getJump();
                        if ( jump < pci && jump >= 0 ) {
                            State loop;
                            do { // loop
                                loop = true_branch.clone();
                                analyzeInstructions(m,il,jump
                                       ,min(true_branch.getPC()-1,end_pc),loop,j);
                            } while(true_branch.intersect(loop));
                        }
                    } 
                    
                    // false branch loop
                    if ( jump < pci && jump >= 0 ) {
                        State loop;
                        do { // loop
                            // System.out.println("loop!");
                            loop = false_branch.clone();
                            analyzeInstructions(m,il,false_branch.getJump(),
                                min(false_branch.getPC()-1,end_pc),loop,j);
                        } while(false_branch.intersect(loop));
                    }

                    System.out.println("pre-intersect "+pc.getPosition()
                            +" max is "+end_pc);
                    true_branch.intersect(false_branch);
                    pc  = il.findHandle(true_branch.getPC()).getNext();
                    if( pc != null ) {
                      //  System.out.println("if end jump to "+pc.getPosition()
                      //          +" max is "+end_pc);
                    }
                    //System.out.println("end if");
                    continue;
                } else if (i instanceof GotoInstruction ) {
                    GotoInstruction ig = (GotoInstruction)i;
                    s.setJump(ig.getTarget().getPosition());
                } else {
                    throw new RuntimeException(
                            "BranchInstruction not implemented yet: "+pci);
                }
            } else if ( i instanceof ConversionInstruction ) {
                Variable v = s.stackPop().clone();
                if ( i instanceof D2F ) {
                    assert v.getType().equals("D");
                    v.setType("F");
                } else if ( i instanceof D2L ) {
                    assert v.getType().equals("D");
                    v.setType("J");
                } else if ( i instanceof D2I ) {
                    assert v.getType().equals("D");
                    v.setType("I");
                } else if ( i instanceof F2D ) {
                    assert v.getType().equals("F");
                    v.setType("D");
                } else if ( i instanceof F2L ) {
                    assert v.getType().equals("F");
                    v.setType("J");
                } else if ( i instanceof F2I ) {
                    assert v.getType().equals("F");
                    v.setType("I");
                } else if ( i instanceof I2B 
                         || i instanceof I2C ) {
                    // can be ignored, does not change anything
                    // for this analysis. it can at most truncare the
                    // higher bits, reducing the value without changing
                    // the value in the abstract domain
                } else if ( i instanceof I2D ) {
                    assert v.getType().equals("I");
                    v.setType("D");
                } else if ( i instanceof I2L ) {
                    assert v.getType().equals("I");
                    v.setType("J");
                } else if ( i instanceof I2F ) {
                    assert v.getType().equals("I");
                    v.setType("F");
                } else if ( i instanceof L2D ) {
                    assert v.getType().equals("J");
                    v.setType("D");
                } else if ( i instanceof L2I ) {
                    assert v.getType().equals("J");
                    v.setType("I");
                } else if ( i instanceof L2F ) {
                    assert v.getType().equals("J");
                    v.setType("F");
                } else {
                    throw new RuntimeException(
                      "Unkonwn Conversion Instruction :"+pci);
                }
                s.stackPush(v);
            } else if ( i instanceof CPInstruction ) {
                if ( i instanceof INSTANCEOF || i instanceof CHECKCAST ) {
                    s.stackPop();
                    ConstantPoolGen gen = new ConstantPoolGen(m.getConstantPool());
                    if ( i instanceof CHECKCAST ) {
                        CHECKCAST c = (CHECKCAST)i;
                        Type t = c.getType(gen);
                        s.stackPush(new Variable(t.getSignature()
                                ,Variable.Kind.LOCAL
                                ,Variable.DomainValue.TOP
                                ,Integer.MAX_VALUE,pci));
                    } else if ( i instanceof INSTANCEOF ) {
                        s.stackPush(new Variable("I"
                                ,Variable.Kind.LOCAL
                                ,Variable.DomainValue.GEQ0
                                ,Integer.MAX_VALUE,pci));
                    }
                } else if ( i instanceof InvokeInstruction ) {
                    InvokeInstruction ii = (InvokeInstruction)i;
                    ConstantPoolGen gen = new ConstantPoolGen(
                            m.getConstantPool());
                    ReferenceType t = ii.getReferenceType(gen); 
                    ObjectType tt = (ObjectType)t; //@TODO may be arraytype or others
                    
                    if ( ! analyzer.isAnalyzable(tt.getClassName()) ) {
                        // skip instruction, un-analizable method. just clean
                        // the stack from the arguments.
                        for( Type ty : ii.getArgumentTypes(gen) )
                            s.stackPop();

                        // pop the object reference if needed
                        if ( ! (ii instanceof INVOKESTATIC) )
                            s.stackPop();

                        if ( ii.getReturnType(gen) != Type.VOID ) {
                             s.stackPush(new Variable(ii.getReturnType(gen).getSignature()
                                         ,Variable.Kind.LOCAL
                                         ,Variable.DomainValue.TOP
                                         ,Integer.MAX_VALUE,pci));
                        }
                    } else { // the method is in the classes to analyze
                        String class_name = tt.getClassName();
                        String method_name = ii.getMethodName(gen);
                        JavaClass cl = null;
                        Type ty[] = ii.getArgumentTypes(gen);
                        Method method = null;
                        System.out.println("Analyze call to "+method_name);

                        //@TODO remove arguments off the stack, should
                        //be passed in the method for better analysys?
                        for( Type argument : ii.getArgumentTypes(gen) )
                            s.stackPop();

                        // search method going up in the class tree
                        try { 
                            boolean found = false;
                            cl = Repository.lookupClass(class_name);
                            do {
                                //System.out.println("method is in " + cl.getClassName()+"?");
                                Method[] ms = cl.getMethods();
                                for( Method clmethod : ms ) {
                                    //System.out.println(method.getName()+"=="+method_name);
                                    if ( clmethod.getName().equals(method_name) &&
                                            equalTypes(clmethod.getArgumentTypes(),ty) ) {
                                        if ( clmethod.isAbstract() ) {
                                            found = false; // found, but keep going
                                            break;
                                        } else { // may be native
                                            //System.out.println("Found");
                                            method = clmethod;
                                            found = true; // found, stop it
                                            break;
                                        }
                                    }
                                }
                                if ( !found )
                                    cl = cl.getSuperClass(); // becomes null if it is Object
                            } while( ! found && cl != null );
                            if( ! found && cl == null ) {
                                // method not found and climbed up to
                                // java.lang.Object!
                                throw new RuntimeException("Can't find method "+method_name);
                            }
                        } catch ( ClassNotFoundException e ) {
                            throw new RuntimeException(e);
                        }

                        MethodSignature ms = new MethodSignature(method,cl);
                        if( method_result.get(ms) == null ) {
                            boolean recursive = analyzeMethod(method,cl);
                            //System.out.println("Is recursive? "+recursive);
                            if( recursive ) {
                                assert method_result.get(ms).getDomainValue() == Variable.DomainValue.TOP;
                                analyzeMethod(method,cl);
                                Variable ret = method_result.get(ms);
                                while( ret.intersect(method_result.get(ms),s) ) {
                                    analyzeMethod(method,cl);
                                }
                                method_result.remove(ms);
                                method_result.put(ms,ret);
                            }
                        }

                        // pop the object reference if needed
                        if ( ! (i instanceof INVOKESTATIC) ) 
                            s.stackPop();

                        if (method.getReturnType() != Type.VOID ) {
                            // System.out.println("put on stack result of "+ms);
                            // put result on top of the stack+ms
                            Variable ret = method_result.get(ms);
                            ret = ret.clone();
                            ret.cleanBounds();// must be null, there are references to variables
                                              // that are not necessarly correct!
                                             
                            // putting in relation arguments with the result
                            // requires a lot more code.
                            s.stackPush(ret);
                        }
                    }
                } else if ( i instanceof FieldInstruction ) {
                    if ( i instanceof GETSTATIC || i instanceof GETFIELD ) {
                        FieldInstruction a = (FieldInstruction)i;
                        ConstantPoolGen gen = new ConstantPoolGen(m.getConstantPool());
                        ReferenceType t = a.getReferenceType(gen); 
                        if ( i instanceof GETFIELD ) {
                            s.stackPop(); // objectref
                        }
                        Variable v;
                        if ( t instanceof ObjectType ) {
                            v = new Variable(((ObjectType)t).getClassName()
                                             ,Variable.Kind.FIELD,Variable.DomainValue.TOP
                                             ,Integer.MAX_VALUE,pci);
                            s.stackPush(v); // object reference
                        } else if ( t instanceof ArrayType ) {
                            v = new Variable("["+((ArrayType)t).getElementType()
                                                 ,Variable.Kind.FIELD,Variable.DomainValue.TOP
                                                 ,Integer.MAX_VALUE,pci);
                            s.stackPush(v); // array reference
                        } else {
                            throw new RuntimeException(
                              "UninitializedObjectType not implemented: "+pci);
                        }
                    } else if( i instanceof PUTSTATIC ) {
                        s.stackPop(); // value
                    } else if ( i instanceof PUTFIELD ){
                        s.stackPop(); // value
                        s.stackPop(); // object ref
                    } else {
                        throw new RuntimeException(
                                "FieldInstruction not completed yet: "+pci);
                    }
                } else if ( i instanceof LDC ) { // LDC_W included
                    LDC ii = (LDC)i;
                    Variable v = s.loadConstant(ii.getIndex());
                    s.stackPush(v);
                } else if ( i instanceof NEW ) {
                    ConstantPoolGen gen = new ConstantPoolGen(m.getConstantPool());
                    ObjectType type = ((NEW)i).getLoadClassType(gen);
                    s.stackPush(new Variable(type.getSignature()
                                ,Variable.Kind.LOCAL
                                ,Variable.DomainValue.TOP
                                ,Integer.MAX_VALUE,pci));
                    // throw new RuntimeException("NEW not completed");
                } else {
                    throw new RuntimeException(
                            "CPInstruction not implemented yet: "+pci);
                }
            } else if ( i instanceof ARRAYLENGTH ) {
                Variable arrayref = s.stackPop();
                Variable edge     = new Variable("I",Variable.Kind.LOCAL
                                                ,Variable.DomainValue.GEQ0
                                                ,Integer.MAX_VALUE,pci);
                edge.addEdge(arrayref);
                s.stackPush(edge);
            } else if ( i instanceof MONITORENTER 
                     || i instanceof MONITOREXIT ) {
                s.stackPop();
            } else if ( i instanceof NOP || i instanceof BREAKPOINT 
                     || i instanceof CompoundInstruction
                     || i instanceof IMPDEP1 
                     || i instanceof IMPDEP2 ) {
                /* ignore. virtual instructions generated only from bcel
                 * classes, not real bytecode (compound) or opcodes to ignore
                 * (breakpoint,nop,..) */
            } else if ( i instanceof ATHROW ) {
                //@TODO if the exception catch is in the method body
                //then continue, or else terminate.
                throw new RuntimeException("athrow not yet implemented");
            } else if ( i instanceof ReturnInstruction ) {
                // set the return value and return the current state.
                // do not removes any intermediate result.
                if ( s.stackSize() > 0 ) {
                    s.setReturn(s.stackPop());
                } else {
                    s.setReturn(new Variable("V"
                                ,Variable.Kind.LOCAL
                                ,Variable.DomainValue.TOP
                                ,Integer.MAX_VALUE,pci));
                }
                return s;
            } else {
                throw new RuntimeException("Uknown type of bytecode: "+i); 
            }

            // load next bytecode
            pc  = pc.getNext();
        }
    }

    public Vector<BadArrayAccess> getReports() {
        return reports;
    }

    private final void makeNewReport( Method m, JavaClass jclass, int idx ) {
        int line = m.getLineNumberTable().getSourceLine(idx);
        BadArrayAccess ba = new BadArrayAccess(jclass.getFileName()
                                      ,m.getName()
                                      ,line);
        if ( ! reports.contains(ba) ) {
            reports.add(ba);
        }
    }

    private static final int min( int a, int b ) {
        return a < b ? a : b;
    }

    private static final boolean equalTypes( Type a[], Type b[] ) {
        if ( a.length != b.length )
            return false;

        for( int i = 0; i < a.length; i++ ) {
            if( ! a[i].equals(b[i]) )
                return false;
        }
        return true;
    }
}
