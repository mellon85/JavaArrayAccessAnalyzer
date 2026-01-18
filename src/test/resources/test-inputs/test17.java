package test;

class test17 {
    public int test ( int x ) {
        if ( x <= 0 )
            return 0;
        else
            return x+test(x-1);
    }

}
