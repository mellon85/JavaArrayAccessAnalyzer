package test;

class test4 {
    public int test( int x, int a[]) {
        if (x < a.length) {
            return a[x]; // error. x<0
        } else {
            return a[x]; // error. x<0 || x >= a.length
        }

    }
}

