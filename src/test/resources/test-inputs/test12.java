package test;

class test12 {
    public int test ( int x, int a[] ) {
        int i = 0;
        do {
            x += a[i]; // unsafe at first loop
            i++;
        } while( i < a.length );
        return x;
    }
}
