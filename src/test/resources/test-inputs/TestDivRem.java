package test;

public class TestDivRem {
    public void testDiv(int[] arr) {
        // Iterate to ensure i is dynamic but bounded
        for (int i = 0; i < arr.length; i++) {
            // i is safe for arr
            int idx = i / 2;
            // idx should be safe for arr
            int x = arr[idx];
        }
    }

    public void testRem(int[] arr, int v) {
        if (arr.length > 0 && v >= 0) {
            int idx = v % arr.length;
            // idx < arr.length, so idx safe for arr
            int x = arr[idx];
        }
    }
}
