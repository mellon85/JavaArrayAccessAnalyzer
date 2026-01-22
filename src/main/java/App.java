import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.jar.*;
import staticAnalyzer.*;

public class App {

    public static void main( String args[] ) {
        try {
            new App().run(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void run( String args[] ) throws IOException, ClassNotFoundException {
        Vector<String> class_names = new Vector<String>();

        // load classes and jar files in the repository
        for( String s : args ) {
            if( s.endsWith(".class") ) {
                addClass(s,class_names);
            } else if ( s.endsWith(".jar") ) {
                addJar(s,class_names);
            } else if ( s.equals("-h") ) {
                System.out.println("App [file.class] [file.jar]");
                return;
            } else {
                throw new IllegalArgumentException("Uknown parameter "+s);
            }
        }

        Analyzer a = new Analyzer();
        // create an instance of the static analyzer
        a.analyzeClasses(class_names);
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
