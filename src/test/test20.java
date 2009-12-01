package test;

class test20 {

    private int x;
    private int y;

    public test20( int x, int y ) {
        this.x = x;
        this.y = y;
    }

    public int get() {
        return x+y;
    }

    public int f( int x, int y ) {
        test20 t = new test20(x,x+y);
        return t.get();
    }
}
