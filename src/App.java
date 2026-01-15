import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.jar.*;
import staticAnalyzer.*;

class App {

    public static void main( String args[] ) {
        new App(args);
    }
    
    public App( String args[] ) {
        Vector<String> class_names = new Vector<String>();

        // load classes and jar files in the repository
        for( String s : args ) {
            try {
                if( s.endsWith(".class") ) {
                    addClass(s,class_names);
                } else if ( s.endsWith(".jar") ) {
                    addJar(s,class_names);
                } else if ( s.equals("-h") ) {
                    System.out.println("App [file.class] [file.jar]");
                    return;
                } else {
                    System.err.println("Uknown parameter "+s);
                    System.exit(1);
                }
            } catch( IOException e ) {
                System.err.println("Error while loading "+s);
                e.printStackTrace();
                return;
            }
        }

        Analyzer a = new Analyzer();
        try {
            // create an instance of the static analyzer
            a.analyzeClasses(class_names);
        } catch( ClassNotFoundException e ) {
            System.err.println("The impossible has happened");
            e.printStackTrace();
        }
    }

    private static final void addClass( String file, Vector<String> v )
            throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        Repository.addClass(cn);
        v.add(cn.name);
        fis.close();
    }

    private static final void addJar( String jar, Vector<String> v ) 
            throws IOException {
        JarInputStream js = new JarInputStream(new FileInputStream(jar));
        JarEntry je;
        while( (je = js.getNextJarEntry()) != null ) {
            String name = je.getName();
            if ( name.endsWith(".class") && ! je.isDirectory() ) {
                ClassReader cr = new ClassReader(js);
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                Repository.addClass(cn);
                v.add(cn.name);
            }
        }
    }
}
