public class TestApp {
    public static void main(String[] args) {
        try {
            Class.forName("com.intellij.ui.RoundedLineBorder");
            System.out.println("RoundedLineBorder exists");
        } catch (Exception e) {
            System.out.println("Not found");
        }
    }
}
