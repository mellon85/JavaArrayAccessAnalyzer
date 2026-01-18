package test;

class test9 {
    public int test ( int x, int a[] ) {
        int i = 0;
        while (i < a.length) {
            x += a[i]; // safe
            i++;
        }
        return x;
    }
}
