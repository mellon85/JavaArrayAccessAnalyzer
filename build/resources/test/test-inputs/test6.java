package test;

// safe
class test6 {
    public static int p( int x, int y, int z) {

        if( x >= y ) {
            if( x == y ) {
                x = 0;
            } else {
                y = 2;
            }
        } 
        x = y+2*z+x;
        return x;
    }
}
