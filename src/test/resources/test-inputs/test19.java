package test;

class test19 {
    public int f(int x) {
        return 0;
    }

    public int g( int x, int a[] ) {
        int i = f(x);
        if( i < a.length ) {
            return a[i];
        }
        return 0;
    }
}
