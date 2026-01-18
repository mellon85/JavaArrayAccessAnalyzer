package test;

class test3 {
    public int test( int x, int a[]) {
        if (x < a.length) {
            return a[x]; // error! x<0
        } else {
            return -1;
        }

    }
}

