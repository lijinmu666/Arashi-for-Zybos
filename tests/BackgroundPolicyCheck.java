import com.arashi.xposed.BackgroundPolicy;
import java.util.Set;
public final class BackgroundPolicyCheck {
    private static int checks;
    private static void check(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Failed check " + checks);
    }
    public static void main(String[] args) {
        String music = "org.example.music";
        String other = "org.example.other";
        check(BackgroundPolicy.shouldKeep(true, Set.of(), music));
        check(BackgroundPolicy.shouldKeep(true, Set.of(), other));
        check(BackgroundPolicy.shouldKeep(true, Set.of(music), music));
        check(!BackgroundPolicy.shouldKeep(true, Set.of(music), other));
        check(!BackgroundPolicy.shouldKeep(false, Set.of(), music));
        check(!BackgroundPolicy.shouldKeep(false, Set.of(music), music));
        check(!BackgroundPolicy.shouldKeep(true, Set.of(), null));
        check(!BackgroundPolicy.shouldKeep(true, Set.of(), ""));
        for (String exempt : Set.of("com.tencent.mm", "com.tencent.mobileqq",
                "com.zuoyebang.iot.pad.zpeyecare", "com.zuoyebang.iot.pad.zpvoiceassistant",
                "com.android.deskclock")) check(BackgroundPolicy.isExempt(exempt));
        check(!BackgroundPolicy.isExempt(music));
        check(!BackgroundPolicy.isExempt("com.android.settings"));
        check(!BackgroundPolicy.isExempt(null));
        Set<String> selected = Set.of(music);
        check(!BackgroundPolicy.shouldKeep(false, selected, music));
        check(BackgroundPolicy.shouldKeep(true, selected, music));
        check(BackgroundPolicy.shouldKeep(true, Set.of(), other));
        check(!BackgroundPolicy.shouldKeep(true, Set.of("org.example.uninstalled"), music));
        System.out.println("PASS: " + checks + " background policy checks");
    }
}
