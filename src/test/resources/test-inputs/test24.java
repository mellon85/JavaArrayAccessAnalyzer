package test;

class test24 {
    public void test(Object o) {
        if (o instanceof String) {
            String s = (String) o;
        }
    }

    public int test2(Object o) {
        if (o instanceof int[]) {
            return ((int[])o).length;
        }
        return 0;
    }
}
