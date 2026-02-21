package test;

class test23 {

    public void f( int[] a, int i ) {
        if ( i < 0 || i >= a.length ) {
            return;
        } else {
            a[i] = i;
            f(a,i-1);
        }
    }
}
