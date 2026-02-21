package test;

public class test_athrow {
    public void test() throws Exception {
        try {
            throw new Exception("test");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
