package app.morphe.extension.tiktok.settings;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NativeSettingsRowsTest {
    private enum Row { BLUEIT_SERVICES, BLUEIT_ENTRY, SUPPORT_HEADER, HELP, PRIVACY }
    @Test public void blueITGetsItsOwnHeadingAndSupportKeepsItsHeadingAndRows() {
        List<Row> original = Arrays.asList(Row.SUPPORT_HEADER, Row.HELP, Row.PRIVACY, Row.BLUEIT_ENTRY);
        List<?> result = NativeSettingsRows.prepare(original, Row.BLUEIT_SERVICES, Row.BLUEIT_ENTRY);
        assertEquals(Arrays.asList(Row.BLUEIT_SERVICES, Row.BLUEIT_ENTRY, Row.SUPPORT_HEADER, Row.HELP, Row.PRIVACY), result);
        assertEquals(4, original.size());
        assertEquals(result, NativeSettingsRows.prepare(result, Row.BLUEIT_SERVICES, Row.BLUEIT_ENTRY));
        assertEquals("BlueIT Services", NativeSettingsRows.headerTitle(Row.BLUEIT_SERVICES, "Support und Info"));
        assertEquals("Support und Info", NativeSettingsRows.headerTitle(Row.SUPPORT_HEADER, "Support und Info"));
    }
    @Test public void unresolvedNativeRowsKeepTheOriginalList() {
        List<Row> rows = Collections.singletonList(Row.HELP);
        assertSame(rows, NativeSettingsRows.prepare(rows, null, Row.BLUEIT_ENTRY));
        assertSame(rows, NativeSettingsRows.prepare(rows, Row.BLUEIT_SERVICES, null));
    }
}
