package test;

class test5 {
    public int test( int x, int a[]) {
        if (x < a.length  && x >= 0 ) {
            return a[x]; // safe
        }
        return 0;
    }
}

