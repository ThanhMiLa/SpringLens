import com.intellij.util.IconUtil;
import javax.swing.Icon;
import java.awt.Component;

public class IconScaleTest {
    public static void main(String[] args) {
        Icon original = null;
        Icon scaled = IconUtil.scale(original, (Component)null, 1.5f);
    }
}
