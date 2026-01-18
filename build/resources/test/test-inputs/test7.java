package test;

class test7 {
    public int test ( int x, int a[] ) {
        for( int i = 0; i < a.length; i++ ) {
            x += a[i]; // safe
        }
        return x;
    }
}
