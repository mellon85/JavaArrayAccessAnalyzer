package test;

class test22 {

    public static int gets(int x, int a[]) {
        return a[x];
    }

    public static void dummy( int x ) {
    }

    public int f( int[] a ) {
        int c = 0;
        for( int i = 0; i < a.length; i++ ) {
            c += test22.gets(i,a);   
            dummy(c);
        }
        return c;
    }
}
