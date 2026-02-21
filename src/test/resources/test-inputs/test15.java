package test;

class test15 {
    public int test2( int x ) {
        return x++;
    }

    public int test( int x ) {
        x = test2(x);
        return x;
    }

}
