import com.lijinmu.arashi.xposed.SettingsHeaderCompat;
import java.util.ArrayList;
import java.util.List;

public final class SettingsHeaderCompatCheck {
    // Public APIs from the supplied Android 12 and OTA 7.10 Settings menu models.
    public static class Header {
        int title, icon;
        String fragment;
        public void setTitleResId(int value) { title = value; }
        public void setImgResId(int value) { icon = value; }
        public void setFragmentName(String value) { fragment = value; }
    }

    public static final class LegacyHeader extends Header {
        boolean visible;
        public void setVisible(boolean value) { visible = value; }
    }

    public static final class ModernHeader extends Header {
        private int zybId;
        public int getZybId() { return zybId; }
        public void setZybId(int value) { zybId = value; }
        // No setVisible in OTA 7.10.
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        List<Header> original = new ArrayList<>();
        LegacyHeader legacy = (LegacyHeader) SettingsHeaderCompat.create(
                LegacyHeader.class, original, 123, 456, "developer");
        check(legacy.visible, "Android 12 visibility setter must be called");
        check(legacy.title == 123 && legacy.icon == 456
                && "developer".equals(legacy.fragment), "Legacy menu content lost");

        ModernHeader wifi = new ModernHeader();
        ModernHeader bluetooth = new ModernHeader();
        bluetooth.setZybId(1);
        original.add(wifi);
        original.add(bluetooth);
        ModernHeader modern = (ModernHeader) SettingsHeaderCompat.create(
                ModernHeader.class, original, 123, 456, "developer");
        check(modern.getZybId() != 0 && modern.getZybId() != 1,
                "Developer entry must not use Wi-Fi/Bluetooth rendering");
        check(modern.title == 123 && modern.icon == 456
                && "developer".equals(modern.fragment), "Modern menu content lost");
        ModernHeader repeated = (ModernHeader) SettingsHeaderCompat.create(
                ModernHeader.class, original, 123, 456, "developer");
        check(repeated.getZybId() == modern.getZybId(), "Menu ID must be stable across reads");
        original.add(modern);
        ModernHeader collision = (ModernHeader) SettingsHeaderCompat.create(
                ModernHeader.class, original, 123, 456, "developer");
        check(collision.getZybId() != modern.getZybId(), "Existing menu IDs must be preserved");
        check(original.size() == 3 && wifi.getZybId() == 0 && bluetooth.getZybId() == 1,
                "Original headers changed");
        System.out.println("PASS: Android 12 / 14 menu compatibility, stable IDs and collision handling");
    }
}
