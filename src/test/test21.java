package test;

class test21 {

    private int x;
    private int y;

    public test21( int x, int y ) {
        this.x = x;
        this.y = y;
    }

    public int get() {
        return x+y;
    }
    
    public static int gets() {
        return 12;
    }

    public int f( int x, int y ) {
        test21 t = new test21(x,x+y);
        return t.gets()+t.get();
    }
}
