package test;

class test16 {
    public int test2( int x ) {
        return x++;
    }

    public int test ( int x, int a[] ) {
        int c = 0;
        while( x < a.length && x >= 0 ) {
            c += a[x]; // safe
            x = test2(x);
        }
        return c;
    }

}
