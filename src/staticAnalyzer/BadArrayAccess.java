package staticAnalyzer;

public class BadArrayAccess {
    private int line;
    private String file;
    private String method;

    protected BadArrayAccess( String file, String method, int line ) {
        this.line = line;
        this.file = file;
        this.method = method;
    }

    public String getFile() {
        return file;
    }

    public String getMethod() {
        return method;
    }
    
    public int getLine() {
        return line;
    }

    public String toString() {
        return file+":"+line+" in "+method;
    }

    public boolean equals( Object obj ) {
        if( obj instanceof BadArrayAccess ) {
            BadArrayAccess ba = (BadArrayAccess)obj;
            if ( ba.file.equals(file) &&
                 ba.method.equals(method) &&
                 ba.line == line ) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
