package test;

class test10 {
    public int test ( int x, int a[] ) {
        float i = 0;
        while (i < a.length) {
            x += a[(int)i]; // unsafe
            i++;
        }
        return x;
    }
}
