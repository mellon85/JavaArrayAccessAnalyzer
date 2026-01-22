package test;

class test18 {
    public int f( int x ) {
        if ( x <= 0 )
            return 0;
        else
            return x+g(x-1);
    }

    public int g( int x ) {
        if ( x <= 0 )
            return 0;
        else
            return x+f(x-1);
    }
}
