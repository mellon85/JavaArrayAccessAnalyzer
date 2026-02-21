package test;

class test8 {
    public int test ( int x, int a[] ) {
        int c = 0;
        while( x < a.length && x >= 0 ) {
            c += a[x]; // safe
            x++;
        }
        return c;
    }
}
