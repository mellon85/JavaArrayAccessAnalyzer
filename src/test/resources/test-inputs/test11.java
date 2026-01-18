package test;

class test11 {
    public int test ( int x, int a[] ) {
        for( int i : a ) {
            x += i; // safe
        }
        return x;
    }
}
