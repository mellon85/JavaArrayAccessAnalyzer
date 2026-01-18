package test;

class test2 {
    public int test( int x, int a[]) {
        return a[x]; // error! x<0 || x > a.length
    }
}

