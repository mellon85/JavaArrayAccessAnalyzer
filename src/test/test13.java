package test;

class test13 {

    private static int c;
    private int d = 0;

    public int test ( int x, int a[] ) {
        while( x < a.length && x >= 0 ) {
            c += a[x]; // safe
            x++;
        }
        d = c+2;
        return c+d;
    }

}
