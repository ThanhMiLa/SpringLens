import com.intellij.util.ui.EmptyIcon;
import javax.swing.Icon;
import java.awt.Component;

public class IconScaleTest2 {
    public static void main(String[] args) {
        Icon original = null;
        Icon scaled = com.intellij.util.IconUtil.scale(original, (Component)null, 1.5f);
    }
}
