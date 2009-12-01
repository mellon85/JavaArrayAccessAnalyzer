import java.util.*;
import org.apache.bcel.classfile.*;
import org.apache.bcel.Repository;
import java.io.*;
import java.util.jar.*;
import staticAnalyzer.*;

class App {

    public static void main( String args[] ) {
        new App(args);
    }
    
    public App( String args[] ) {
        Vector<String> class_names = new Vector<String>();

        // load classes and jar files in the bcel repository
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

        //@DEBUG
        // System.err.println(class_names.toString()); 

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
        JavaClass j = new ClassParser(file).parse();
        Repository.addClass(j);
        v.add(j.getClassName());
    }

    private static final void addJar( String jar, Vector<String> v ) 
            throws IOException {
        JarInputStream js = new JarInputStream(new FileInputStream(jar));
        JarEntry je;
        while( (je = js.getNextJarEntry()) != null ) {
            String name = je.getName();
            if ( name.endsWith(".class") && ! je.isDirectory() ) {
                JavaClass j = new ClassParser(jar,name).parse();
                Repository.addClass(j);
                v.add(j.getClassName());
            }
        }
    }
}
