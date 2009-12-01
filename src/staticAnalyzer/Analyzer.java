package staticAnalyzer;

import java.util.*;
import org.apache.bcel.*;

public class Analyzer {

    private Analysis result = new Analysis(this);
    private Vector<String> class_names = new Vector<String>();

    public Analyzer() {}

    public Analyzer( Vector<String> class_names ) 
            throws ClassNotFoundException {
        analyzeClasses(class_names);
    }

    public void analyzeClasses( Vector<String> class_names )
            throws ClassNotFoundException {
        for( String s : class_names ) {
            this.class_names.add(s);
        }
        for( String s : class_names ) {
            result.analyzeMethods(Repository.lookupClass(s));
        }
        if( result.getReports().size() > 0 ) {
            System.out.println(result.getReports());
        } else {
            System.out.println("No error found");
        }
    }

    // A class is not analyzable if it is not in the repository
    public boolean isAnalyzable( String class_name ) {
        return class_names.contains(class_name);
    }
}
