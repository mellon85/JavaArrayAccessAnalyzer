package test;

class test14 {
    public int test( int v[][] ) {
        int c = 0;
        for ( int i = 0; i < v.length; i++ ) {
            for( int j = 0; j < v[i].length; j++ ) {
                c += v[i][j];
            }
        }
        return c;
    }
}
