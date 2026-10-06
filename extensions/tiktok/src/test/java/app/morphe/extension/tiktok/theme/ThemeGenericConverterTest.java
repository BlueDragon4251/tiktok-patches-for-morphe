package app.morphe.extension.tiktok.theme;

import android.content.Context;
import android.util.TypedValue;
import app.morphe.extension.shared.Utils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class ThemeGenericConverterTest {
    public static class Scalar { public Object invoke(TypedValue value) { return value.data; } }
    public static class Gradient { public Object invoke(TypedValue value) { return new int[]{value.data}; } }
    public static class Positions { public Object invoke(TypedValue value) { return new float[]{0}; } }
    public static class ColorList { public Object invoke(TypedValue value) { return Collections.singletonList(value.data); } }
    private Object resolve(Object converter) {
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        ThemeStateStore.saveUserPreset(context, "arctic_blue");
        return ThemeColorResolver.resolveGeneric(0x7f06001c, context, converter, "default");
    }
    @Test public void scalarColorConvertersStillReceiveTheThemeColor() {
        Object result = resolve(new Scalar());
        assertEquals(Integer.valueOf(ThemeEngine.backgroundColor(RuntimeEnvironment.getApplication())), result);
    }
    @Test public void gradientAndPositionArraysKeepTheEntireNativeResource() {
        assertNull(resolve(new Gradient()));
        assertNull(resolve(new Positions()));
    }
    @Test public void multiValueConvertersKeepTheirNativeShape() {
        assertNull(resolve(new ColorList()));
    }
}
