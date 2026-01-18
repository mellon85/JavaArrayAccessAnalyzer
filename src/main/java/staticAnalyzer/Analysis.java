package staticAnalyzer;

import java.util.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

// http://java.sun.com/j2se/1.5.0/docs/guide/jni/spec/types.html

class Analysis {

    private Analyzer analyzer;
    private Vector<BadArrayAccess> reports = new Vector<BadArrayAccess>();
    private Set<MethodSignature> methods = new HashSet<MethodSignature>();
    private Hashtable<MethodSignature,Variable> method_result = 
                new Hashtable<MethodSignature,Variable>();

    public Analysis( Analyzer analyzer ) {
        this.analyzer = analyzer;
    }

    protected void analyzeMethods( ClassNode jclass ) {
        //System.out.println(jclass.methods.size()+" methods to check");
        for( MethodNode m : jclass.methods ) {
            // skips method already analyzed
            if ( method_result.get(new MethodSignature(m,jclass)) == null )
                analyzeMethod(m,jclass);
        }
        //System.out.println(methods);
    }

    protected boolean analyzeMethod( MethodNode m, ClassNode j ) {
        return analyzeMethod(m,j,null);
    }

    protected boolean analyzeMethod( MethodNode m, ClassNode j,
            Vector<Variable> parameters) {
        InsnList il = m.instructions;
        MethodSignature ms = new MethodSignature(m,j);
        boolean recursive = false;
        Variable ret = null;
        
        boolean isNative = (m.access & Opcodes.ACC_NATIVE) != 0;
        boolean isAbstract = (m.access & Opcodes.ACC_ABSTRACT) != 0;

        if( methods.contains(ms) ) {
            recursive = true;
            // return TOP since we don't know what to return!
            ret = new Variable(Type.getReturnType(m.desc).getDescriptor()
                               ,Variable.Kind.LOCAL
                               ,Variable.DomainValue.TOP
                               ,Integer.MAX_VALUE,0);
        } else if ( isNative ) {
            // If method is native i don't know anything about it.
            ret = new Variable(Type.getReturnType(m.desc).getDescriptor()
                              ,Variable.Kind.LOCAL
                              ,Variable.DomainValue.TOP
                              ,Integer.MAX_VALUE,0);
        } else if ( isAbstract ) {
            throw new RuntimeException("Analyzing abstract method!");
        } else {
            //@DEBUG
            System.out.println("Analyzing "+m.name);
            // System.out.println(""+il);

            methods.add(ms);

            // Map labels to indices for our own use if needed, but we can just use AbstractInsnNode index in list?
            // Actually, for jump targets we need something.
            // Let's use index in the InsnList.

            State state = new State(m,j);
            
            if ( parameters == null ) {
                // setup start,state for the method call when in app analysis.
                // no need of anything in library (default for now) analysis.
                Type[] tys = Type.getArgumentTypes(m.desc);
                int i = 0;
                // If not static, 0 is 'this', arguments start at 1.
                // But State constructor handles 'this' if not static.
                // So here we add arguments.
                // Wait, State constructor adds 'this' at 0.
                // So we should append arguments.

                // If not static, argument 0 is at local index 1?
                // Actually State.variables is a vector. 'this' is added at 0.
                // So subsequent args should be added.

                for( Type ty : tys ) {
                    state.getVariables().add(new Variable(ty.getDescriptor()
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
            if (il.size() > 0) {
                state = analyzeInstructions(m, il
                        , 0, il.size() - 1, state, j);
                ret = state.getReturn();
            } else {
                // empty method?
                 ret = new Variable("V"
                               ,Variable.Kind.LOCAL
                               ,Variable.DomainValue.TOP
                               ,Integer.MAX_VALUE,0);
            }
        }
        // remove method from list of called methods in the stack
        methods.remove(ms);
        method_result.put(ms,ret);
        return recursive;
    }

    // Helper to find instruction index
    private int getIndex(InsnList il, AbstractInsnNode node) {
        return il.indexOf(node);
    }

    private AbstractInsnNode getInsn(InsnList il, int index) {
        return il.get(index);
    }

    protected State analyzeInstructions( MethodNode m, InsnList il
                                       , int start_pc, int end_pc
                                       , State s
                                       , ClassNode j ) {

        // start_pc and end_pc are indices in the InsnList
        if (start_pc < 0 || start_pc >= il.size()) return s;

        AbstractInsnNode node = il.get(start_pc);
        int pci = start_pc;

        while(true) {
            if (node == null || pci > end_pc) {
                return s;
            }
            
            s.setPC(pci);
            System.out.println(pci);

            int opcode = node.getOpcode();
            int type = node.getType();

            if (opcode == -1) {
                // Pseudo instruction (Label, LineNumber, Frame)
                if (node instanceof LineNumberNode) {
                    // ignore
                } else if (node instanceof LabelNode) {
                    // ignore
                } else if (node instanceof FrameNode) {
                    // ignore
                }
            } else if ( opcode == Opcodes.FCMPG || opcode == Opcodes.FCMPL ) {
                Variable v1,v2;
                v1 = s.stackPop();
                v2 = s.stackPop();
                assert v1.getType().equals("F");
                assert v2.getType().equals("F");
                s.stackPush(new Variable("I",Variable.Kind.CONST,
                            Variable.DomainValue.TOP,Integer.MAX_VALUE,pci));
            } else if ( opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG ) {
                Variable v1,v2;
                v1 = s.stackPop();
                v2 = s.stackPop();
                assert v1.getType().equals("D");
                assert v2.getType().equals("D");
                s.stackPush(new Variable("I",Variable.Kind.CONST,
                                Variable.DomainValue.TOP,Integer.MAX_VALUE,pci));
            } else if ( (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5)
                     || (opcode >= Opcodes.LCONST_0 && opcode <= Opcodes.LCONST_1)
                     || (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2)
                     || (opcode >= Opcodes.DCONST_0 && opcode <= Opcodes.DCONST_1)
                     || opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH ) {
                 // Constant push instructions
                 // BIPUSH and SIPUSH are IntInsnNode
                 // Consts are InsnNode
                 int val = 0;
                 String sig = "I";
                 if (opcode == Opcodes.ICONST_M1) val = -1;
                 else if (opcode == Opcodes.ICONST_0) val = 0;
                 else if (opcode == Opcodes.ICONST_1) val = 1;
                 else if (opcode == Opcodes.ICONST_2) val = 2;
                 else if (opcode == Opcodes.ICONST_3) val = 3;
                 else if (opcode == Opcodes.ICONST_4) val = 4;
                 else if (opcode == Opcodes.ICONST_5) val = 5;
                 else if (opcode == Opcodes.LCONST_0) { val = 0; sig = "J"; }
                 else if (opcode == Opcodes.LCONST_1) { val = 1; sig = "J"; }
                 else if (opcode == Opcodes.FCONST_0) { val = 0; sig = "F"; }
                 else if (opcode == Opcodes.FCONST_1) { val = 1; sig = "F"; }
                 else if (opcode == Opcodes.FCONST_2) { val = 2; sig = "F"; }
                 else if (opcode == Opcodes.DCONST_0) { val = 0; sig = "D"; }
                 else if (opcode == Opcodes.DCONST_1) { val = 1; sig = "D"; }
                 else if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                     val = ((IntInsnNode)node).operand;
                 }

                Variable.DomainValue f;
                if ( val > 0 ) {
                    f = Variable.DomainValue.G0;
                } else if ( val >= 0 ) {
                    f = Variable.DomainValue.GEQ0;
                } else {
                    f = Variable.DomainValue.TOP;
                }
                Variable v = new Variable(sig,Variable.Kind.CONST
                                         ,f,Integer.MAX_VALUE
                                         ,pci);
                s.stackPush(v);

            } else if ( opcode == Opcodes.ACONST_NULL ) {
                s.stackPush(new Variable("Ljava/lang/Object;",Variable.Kind.CONST
                                         ,Variable.DomainValue.TOP
                                         ,Integer.MAX_VALUE,pci));
            } else if ( (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD)
                     || (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE)
                     || opcode == Opcodes.IINC ) {
                // Local Variable instructions
                // ILOAD, LLOAD, FLOAD, DLOAD, ALOAD = 21..25
                // ISTORE..ASTORE = 54..58
                // IINC = 132

                int var = -1;
                if (node instanceof VarInsnNode) {
                    var = ((VarInsnNode)node).var;
                } else if (node instanceof IincInsnNode) {
                    var = ((IincInsnNode)node).var;
                }

                if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) {
                    s.stackPush(s.load(var));
                } else if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) {
                    Variable v = s.stackPop();
                    v.setIndex(var);
                    // Next instruction pos
                    v.setStartPC(pci + 1);
                    v.setKind(Variable.Kind.LOCAL);
                    s.store(var,v);
                } else if (opcode == Opcodes.IINC) {
                    IincInsnNode iinc = (IincInsnNode)node;
                    Variable v = s.load(iinc.var);
                    v.iinc(iinc.incr);
                }

            } else if ( opcode == Opcodes.DUP ) {
                    Variable top = s.stackPeek();
                    assert top.getCategory() == 1;
                    s.stackPush(top);
            } else if ( opcode == Opcodes.DUP_X1 ) {
                    Variable top_0 = s.stackPop();
                    Variable top_1 = s.stackPop();
                    assert top_0.getCategory() == 1;
                    assert top_1.getCategory() == 1;
                    s.stackPush(top_0);
                    s.stackPush(top_1);
                    s.stackPush(top_0);
            } else if ( opcode == Opcodes.DUP_X2 ) {
                    Variable top_0 = s.stackPop();
                    Variable top_1 = s.stackPop();
                    Variable top_2 = null;
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
            } else if ( opcode == Opcodes.DUP2 ) {
                    Variable top = s.stackPeek();
                    if ( top.getCategory() == 2) {
                        s.stackPush(top);
                    } else {
                        Variable top_0 = s.stackPop();
                        Variable top_1 = s.stackPop();
                        
                        assert top_0.getCategory() == 1;
                        assert top_1.getCategory() == 1;

                        s.stackPush(top_1);
                        s.stackPush(top_0);
                        s.stackPush(top_1);
                        s.stackPush(top_0);
                    }
            } else if ( opcode == Opcodes.DUP2_X1 ) {
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
            } else if ( opcode == Opcodes.DUP2_X2 ) {
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
            } else if ( opcode == Opcodes.POP ) {
                    Variable top = s.stackPop();
                    assert top.getCategory() == 1;
            } else if ( opcode == Opcodes.POP2 ) {
                    Variable top = s.stackPop();
                    if ( top.getCategory() == 1 ) {
                        Variable top_1 = s.stackPop();
                        assert top_1.getCategory() == 1;
                    }
            } else if ( opcode == Opcodes.SWAP ) {
                    Variable a = s.stackPop();
                    Variable b = s.stackPop();

                    assert a.getCategory() == 1;
                    assert b.getCategory() == 1;

                    s.stackPush(a);
                    s.stackPush(b);
            } else if ( (opcode >= Opcodes.IALOAD && opcode <= Opcodes.SALOAD)
                     || (opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE) ) {
                // Array instructions
                Variable value = null;
                boolean isStore = (opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE);

                if ( isStore ) { // store command
                    // eliminate value from stack.
                    value = s.stackPop();
                }
                Variable index    = s.stackPop(); // index variable
                Variable arrayref = s.stackPop(); // array reference

                // check if the index is safe
                if ( ! index.isSafe(arrayref) ) {
                    // load is not safe, add report.
                    makeNewReport(m,j,pci);
                    // mark index as safe for arrayref.
                    index.addSafe(arrayref);
                }
                if ( !isStore ) {
                    s.stackPush(s.load(arrayref,index));
                } else {
                    s.store(arrayref,index,value);
                }
            } else if ( (opcode >= Opcodes.IADD && opcode <= Opcodes.LXOR) ) {
                // Arithmetic Instructions
                // IADD..LXOR
                Variable v1,v2,v;
                v1 = v2 = s.stackPop(); // first value on the stack

                if( opcode == Opcodes.DADD || opcode == Opcodes.FADD
                 || opcode == Opcodes.IADD || opcode == Opcodes.LADD ) {
                    v1 = s.stackPop();
                    v = v1.add(v2);
                } else if ( opcode == Opcodes.DDIV || opcode == Opcodes.FDIV
                         || opcode == Opcodes.IDIV || opcode == Opcodes.LDIV ) {
                    v1 = s.stackPop();
                    v = v1.div(v2);
                } else if ( opcode == Opcodes.DMUL || opcode == Opcodes.FMUL
                         || opcode == Opcodes.IMUL || opcode == Opcodes.LMUL ) {
                    v1 = s.stackPop();
                    v = v1.mul(v2);
                } else if ( opcode == Opcodes.DNEG || opcode == Opcodes.FNEG
                         || opcode == Opcodes.INEG || opcode == Opcodes.LNEG ) {
                    v = v2.neg();
                } else if ( opcode == Opcodes.DREM || opcode == Opcodes.FREM
                         || opcode == Opcodes.IREM || opcode == Opcodes.LREM ) {
                    v1 = s.stackPop();
                    v = v1.rem(v2);
                } else if ( opcode == Opcodes.DSUB || opcode == Opcodes.FSUB
                         || opcode == Opcodes.ISUB || opcode == Opcodes.LSUB ) {
                    v1 = s.stackPop();
                    v = v1.sub(v2);
                } else if ( opcode == Opcodes.IAND || opcode == Opcodes.LAND ) {
                    v1 = s.stackPop();
                    v = v1.and(v2);
                } else if ( opcode == Opcodes.ISHL || opcode == Opcodes.LSHL ) {
                    v1 = s.stackPop();
                    v = v1.shl(v2);
                } else if ( opcode == Opcodes.ISHR || opcode == Opcodes.LSHR ) {
                    v1 = s.stackPop();
                    v = v1.shr(v2);
                } else if ( opcode == Opcodes.IUSHR || opcode == Opcodes.LUSHR ) {
                    v1 = s.stackPop();
                    v = v1.ushr(v2);
                } else if ( opcode == Opcodes.IOR || opcode == Opcodes.LOR ) {
                    v1 = s.stackPop();
                    v = v1.or(v2);
                } else if (opcode == Opcodes.IXOR || opcode == Opcodes.LXOR) {
                    v1 = s.stackPop();
                    v = v1.xor(v2);
                } else {
                    throw new RuntimeException(
                            "Unknown arithmetic bytecode "+opcode);
                }
                v.setStartPC(pci);
                s.stackPush(v);
            } else if ( (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE) || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL ) {
                // Branch Instructions
                // IFEQ..IF_ACMPNE, IFNULL, IFNONNULL

                if (node instanceof JumpInsnNode) {
                    Variable v1,v2;

                    State false_branch = s.clone();
                    State true_branch  = s;
                    
                    JumpInsnNode ifi = (JumpInsnNode)node;
                    LabelNode true_target = ifi.label;
                    int true_target_idx = getIndex(il, true_target);
                    int next_pc_idx = pci + 1; // Assuming next instruction

                    if( opcode == Opcodes.IF_ICMPEQ ) {
                        { // true v1 == v2
                            v2 = true_branch.stackPop();
                            v1 = true_branch.stackPop();
                            v2.cmpeq(v1);
                        }
                        { // false v1 ≠ v2
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                        }
                    } else if ( opcode == Opcodes.IF_ICMPNE ) {
                        { // true v1 ≠ v2
                            true_branch.stackPop();
                            true_branch.stackPop();
                        }
                        { // false v1 = v2
                            v2 = false_branch.stackPop();
                            v1 = false_branch.stackPop();
                            v2.cmpeq(v1);
                        }
                    } else if ( opcode == Opcodes.IF_ICMPGE ) {
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
                    } else if ( opcode == Opcodes.IF_ICMPGT ) {
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
                    } else if ( opcode == Opcodes.IF_ICMPLE ) {
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
                    }  else if ( opcode == Opcodes.IF_ICMPLT ) {
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
                    } else if ( opcode == Opcodes.IFLT ) {
                        { // true < 0
                            v1 = true_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.TOP);
                        }
                        { // false >= 0
                            v1 = false_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                    } else if ( opcode == Opcodes.IFEQ ) {
                        { // true =0
                            v1 = true_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                        { // false ≠0
                            v1 = false_branch.stackPop();
                        }
                    } else if ( opcode == Opcodes.IFGE ) {
                        { // true >=0
                            v1 = true_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                        { // false <0
                            v1 = false_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.TOP);
                        }
                    } else if ( opcode == Opcodes.IFGT ) {
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
                    } else if ( opcode == Opcodes.IFLE ) {
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
                    } else if ( opcode == Opcodes.IFNE ) {
                        { // true ≠0
                            v1 = true_branch.stackPop();
                        }
                        { // false =0 
                            v1 = false_branch.stackPop();
                            v1.setDomainValue(Variable.DomainValue.GEQ0);
                        }
                    } else if ( opcode == Opcodes.IFNULL
                             || opcode == Opcodes.IFNONNULL ) {
                        { // true
                            v1 = true_branch.stackPop();
                        }
                        { // false
                            v1 = false_branch.stackPop();
                        }
                    } else if ( opcode == Opcodes.IF_ACMPEQ
                             || opcode == Opcodes.IF_ACMPNE ) {
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

                    // analyzeInstructions uses indices.

                    analyzeInstructions(m,il, next_pc_idx
                           ,min(true_target_idx - 1,end_pc)
                           ,false_branch,j);

                    // there is a then only if the else finished with a goto.
                    int jump = false_branch.getJump();
                    if( jump >= pci ) { // there has been a goto!
                        // In BCEL code: pc = il.findHandle(false_branch.getPC()).getNext();
                        // This seems to skip instructions?
                        // If false_branch hit a goto, it set jump.
                        // Here we continue with true branch analysis.

                        analyzeInstructions(m,il, true_target_idx
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

                    System.out.println("pre-intersect "+pci
                            +" max is "+end_pc);
                    true_branch.intersect(false_branch);

                    // In the original, it does 'pc = il.findHandle(true_branch.getPC()).getNext()'
                    // But here we are in a loop iterating instructions.
                    // We need to skip to the merge point.

                    // If we just continue, we process the next instruction.
                    // But we want to process from where the branches met.
                    // The original code uses recursion for branches, so when it returns here,
                    // we need to update our current position 'pci' or just let loop continue?
                    // The original code uses 'continue' which goes to next iteration of while(true).
                    // But it updates 'pc' before continue?
                    // No, the original 'pc' update is inside the if block.

                    // In original: pc = il.findHandle(true_branch.getPC()).getNext();
                    // So we should update 'node' and 'pci'.

                    // Wait, true_branch.getPC() is the last PC executed in that branch.
                    // .getNext() is the instruction after that.

                    int next_idx = true_branch.getPC() + 1;
                    if (next_idx < il.size()) {
                        node = il.get(next_idx);
                        pci = next_idx;
                        continue; // Continue loop with new node
                    }

                    // If no next, we are done?
                    return s;

                }
            } else if ( opcode == Opcodes.GOTO ) {
                JumpInsnNode ig = (JumpInsnNode)node;
                s.setJump(getIndex(il, ig.label));
            } else if ( (opcode >= Opcodes.I2L && opcode <= Opcodes.I2S) ) {
                // Conversion
                 Variable v = s.stackPop().clone();
                if ( opcode == Opcodes.D2F ) {
                    assert v.getType().equals("D");
                    v.setType("F");
                } else if ( opcode == Opcodes.D2L ) {
                    assert v.getType().equals("D");
                    v.setType("J");
                } else if ( opcode == Opcodes.D2I ) {
                    assert v.getType().equals("D");
                    v.setType("I");
                } else if ( opcode == Opcodes.F2D ) {
                    assert v.getType().equals("F");
                    v.setType("D");
                } else if ( opcode == Opcodes.F2L ) {
                    assert v.getType().equals("F");
                    v.setType("J");
                } else if ( opcode == Opcodes.F2I ) {
                    assert v.getType().equals("F");
                    v.setType("I");
                } else if ( opcode == Opcodes.I2B
                         || opcode == Opcodes.I2C || opcode == Opcodes.I2S ) {
                    // ignored
                } else if ( opcode == Opcodes.I2D ) {
                    assert v.getType().equals("I");
                    v.setType("D");
                } else if ( opcode == Opcodes.I2L ) {
                    assert v.getType().equals("I");
                    v.setType("J");
                } else if ( opcode == Opcodes.I2F ) {
                    assert v.getType().equals("I");
                    v.setType("F");
                } else if ( opcode == Opcodes.L2D ) {
                    assert v.getType().equals("J");
                    v.setType("D");
                } else if ( opcode == Opcodes.L2I ) {
                    assert v.getType().equals("J");
                    v.setType("I");
                } else if ( opcode == Opcodes.L2F ) {
                    assert v.getType().equals("J");
                    v.setType("F");
                }
                s.stackPush(v);
            } else if ( opcode == Opcodes.INSTANCEOF || opcode == Opcodes.CHECKCAST ) {
                    s.stackPop(); //@TODO atm ignoring types
                    s.stackPush(new Variable("V"
                                ,Variable.Kind.LOCAL
                                ,Variable.DomainValue.TOP
                                ,Integer.MAX_VALUE,pci));
            } else if ( opcode >= Opcodes.INVOKEVIRTUAL && opcode <= Opcodes.INVOKEINTERFACE ) {
                    // Invoke
                    MethodInsnNode ii = (MethodInsnNode)node;
                    String owner = ii.owner;
                    String name = ii.name;
                    String desc = ii.desc;
                    
                    // Check analyzable
                    if ( ! analyzer.isAnalyzable(owner) ) {
                        // skip instruction, un-analizable method. just clean
                        // the stack from the arguments.
                        Type[] args = Type.getArgumentTypes(desc);
                        for( Type ty : args )
                            s.stackPop();

                        // pop the object reference if needed
                        if ( opcode != Opcodes.INVOKESTATIC )
                            s.stackPop();

                        Type retType = Type.getReturnType(desc);
                        if ( retType.getSort() != Type.VOID ) {
                             s.stackPush(new Variable(retType.getDescriptor()
                                         ,Variable.Kind.LOCAL
                                         ,Variable.DomainValue.TOP
                                         ,Integer.MAX_VALUE,pci));
                        }
                    } else { // the method is in the classes to analyze
                        System.out.println("Analyze call to "+name);

                        Type[] args = Type.getArgumentTypes(desc);
                        for( Type argument : args )
                            s.stackPop();

                        // search method going up in the class tree
                        try { 
                            boolean found = false;
                            ClassNode cl = Repository.lookupClass(owner);
                            MethodNode method = null;
                            do {
                                //System.out.println("method is in " + cl.getClassName()+"?");
                                List<MethodNode> ms = cl.methods;
                                for( MethodNode clmethod : ms ) {
                                    if ( clmethod.name.equals(name) &&
                                            clmethod.desc.equals(desc) ) {
                                        if ( (clmethod.access & Opcodes.ACC_ABSTRACT) != 0 ) {
                                            found = false; // found, but keep going
                                            break;
                                        } else { // may be native
                                            method = clmethod;
                                            found = true; // found, stop it
                                            break;
                                        }
                                    }
                                }
                                if ( !found ) {
                                    if (cl.superName != null)
                                         cl = Repository.lookupClass(cl.superName);
                                    else
                                         cl = null;
                                }
                            } while( ! found && cl != null );
                            if( ! found && cl == null ) {
                                throw new RuntimeException("Can't find method "+name);
                            }

                            // Re-assign cl to the class where method was found?
                            // No, analyzeMethod takes (MethodNode, ClassNode).
                            // If found, cl is the class node.

                            MethodSignature ms = new MethodSignature(method,cl);
                            if( method_result.get(ms) == null ) {
                                boolean recursive = analyzeMethod(method,cl);
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

                            if ( opcode != Opcodes.INVOKESTATIC )
                                s.stackPop();

                            Type retType = Type.getReturnType(desc);

                            if (retType.getSort() != Type.VOID ) {
                                Variable ret = method_result.get(ms);
                                ret = ret.clone();
                                ret.cleanBounds();
                                s.stackPush(ret);
                            }

                        } catch ( ClassNotFoundException e ) {
                            throw new RuntimeException(e);
                        }
                    }
            } else if ( opcode == Opcodes.GETSTATIC || opcode == Opcodes.GETFIELD ) {
                    FieldInsnNode a = (FieldInsnNode)node;
                    if ( opcode == Opcodes.GETFIELD ) {
                        s.stackPop(); // objectref
                    }
                    Variable v;
                    Type t = Type.getType(a.desc);

                    if ( t.getSort() == Type.OBJECT ) {
                        v = new Variable(t.getDescriptor()
                                         ,Variable.Kind.FIELD,Variable.DomainValue.TOP
                                         ,Integer.MAX_VALUE,pci);
                        s.stackPush(v); // object reference
                    } else if ( t.getSort() == Type.ARRAY ) {
                         v = new Variable(t.getDescriptor()
                                             ,Variable.Kind.FIELD,Variable.DomainValue.TOP
                                             ,Integer.MAX_VALUE,pci);
                        s.stackPush(v); // array reference
                    } else {
                         // Primitives
                         v = new Variable(t.getDescriptor()
                                         ,Variable.Kind.FIELD,Variable.DomainValue.TOP
                                         ,Integer.MAX_VALUE,pci);
                        s.stackPush(v);
                    }
            } else if ( opcode == Opcodes.PUTSTATIC ) {
                    s.stackPop(); // value
            } else if ( opcode == Opcodes.PUTFIELD ){
                    s.stackPop(); // value
                    s.stackPop(); // object ref
            } else if ( opcode == Opcodes.LDC ) {
                    LdcInsnNode ii = (LdcInsnNode)node;
                    Object cst = ii.cst;
                    Variable v = null;
                    if (cst instanceof Integer) {
                         int val = (Integer)cst;
                         Variable.DomainValue f = (val>=0)?Variable.DomainValue.GEQ0:Variable.DomainValue.TOP;
                         if(val>0) f = Variable.DomainValue.G0;
                         v = new Variable("I", Variable.Kind.CONST, f, Integer.MAX_VALUE, pci);
                    } else if (cst instanceof Float) {
                         v = new Variable("F", Variable.Kind.CONST, Variable.DomainValue.TOP, Integer.MAX_VALUE, pci);
                    } else if (cst instanceof Long) {
                         v = new Variable("J", Variable.Kind.CONST, Variable.DomainValue.TOP, Integer.MAX_VALUE, pci);
                    } else if (cst instanceof Double) {
                         v = new Variable("D", Variable.Kind.CONST, Variable.DomainValue.TOP, Integer.MAX_VALUE, pci);
                    } else if (cst instanceof String) {
                         v = new Variable("Ljava/lang/String;", Variable.Kind.CONST, Variable.DomainValue.TOP, Integer.MAX_VALUE, pci);
                    } else if (cst instanceof Type) {
                         v = new Variable("Ljava/lang/Class;", Variable.Kind.CONST, Variable.DomainValue.TOP, Integer.MAX_VALUE, pci);
                    }
                    if (v != null) s.stackPush(v);
            } else if ( opcode == Opcodes.NEW ) {
                    TypeInsnNode tin = (TypeInsnNode)node;
                    s.stackPush(new Variable(Type.getObjectType(tin.desc).getDescriptor()
                                ,Variable.Kind.LOCAL
                                ,Variable.DomainValue.TOP
                                ,Integer.MAX_VALUE,pci));
            } else if ( opcode == Opcodes.ARRAYLENGTH ) {
                Variable arrayref = s.stackPop();
                Variable edge     = new Variable("I",Variable.Kind.LOCAL
                                                ,Variable.DomainValue.GEQ0
                                                ,Integer.MAX_VALUE,pci);
                edge.addEdge(arrayref);
                s.stackPush(edge);
            } else if ( opcode == Opcodes.MONITORENTER
                     || opcode == Opcodes.MONITOREXIT ) {
                s.stackPop();
            } else if ( opcode == Opcodes.NOP ) {
                // ignore
            } else if ( opcode == Opcodes.ATHROW ) {
                //@TODO if the exception catch is in the method body
                //then continue, or else terminate.
                throw new RuntimeException("athrow not yet implemented");
            } else if ( opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN ) {
                // Return
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
                 // Ignore unsupported opcodes or throw?
                 // throw new RuntimeException("Uknown type of bytecode: "+opcode);
            }

            // load next bytecode
            node = node.getNext();
            pci = getIndex(il, node);
        }
    }

    public Vector<BadArrayAccess> getReports() {
        return reports;
    }

    private final void makeNewReport( MethodNode m, ClassNode jclass, int idx ) {
        // Line number lookup
        int line = -1;
        // Search for LineNumberNode before idx
        // This is inefficient but works.
        // Actually, we can look at the InsnList.

        // Since idx is index in InsnList.
        for(int i=idx; i>=0; i--) {
            AbstractInsnNode n = m.instructions.get(i);
            if(n instanceof LineNumberNode) {
                line = ((LineNumberNode)n).line;
                break;
            }
        }

        BadArrayAccess ba = new BadArrayAccess(jclass.sourceFile
                                      ,m.name
                                      ,line);
        if ( ! reports.contains(ba) ) {
            reports.add(ba);
        }
    }

    private static final int min( int a, int b ) {
        return a < b ? a : b;
    }
}
