import com.lijinmu.arashi.xposed.StatusBarClockFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class StatusBarClockFormatCheck {
    private static void expect(Calendar calendar, String text) {
        String result = StatusBarClockFormat.suffix(calendar);
        if (!text.equals(result)) throw new AssertionError(result + " != " + text);
    }

    public static void main(String[] args) {
        Calendar clock = new GregorianCalendar(TimeZone.getTimeZone("Asia/Shanghai"));
        clock.clear();
        clock.set(2026, Calendar.OCTOBER, 17, 23, 59);
        expect(clock, "  10月17日周六");
        clock.add(Calendar.MINUTE, 1);
        expect(clock, "  10月18日周日");
        clock.set(2026, Calendar.DECEMBER, 31, 23, 59);
        expect(clock, "  12月31日周四");
        clock.add(Calendar.MINUTE, 1);
        expect(clock, "  1月1日周五");
        clock.set(2024, Calendar.FEBRUARY, 28, 23, 59);
        clock.add(Calendar.MINUTE, 1);
        expect(clock, "  2月29日周四");
        clock.clear();
        clock.set(2026, Calendar.OCTOBER, 18, 0, 30);
        clock.getTimeInMillis();
        clock.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
        expect(clock, "  10月17日周六");
        for (int i = 0; i < 7; i++) {
            clock.set(2026, Calendar.OCTOBER, 18 + i, 12, 0);
            expect(clock, "  10月" + (18 + i) + "日周" + "日一二三四五六".charAt(i));
        }
        StatusBarClockFormat.Cache cache = new StatusBarClockFormat.Cache();
        clock.set(2026, Calendar.OCTOBER, 17, 16, 30, 57);
        String sameDay = cache.suffix(clock);
        for (int i = 0; i < 10; i++) {
            if (cache.suffix(clock) != sameDay) throw new AssertionError("date allocated during seconds tick");
            clock.add(Calendar.SECOND, 1);
        }
        clock.set(2026, Calendar.OCTOBER, 17, 23, 59, 59);
        cache.suffix(clock);
        clock.add(Calendar.SECOND, 1);
        if (!"  10月18日周日".equals(cache.suffix(clock))) throw new AssertionError("stale cached day");
        clock.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        if (!StatusBarClockFormat.suffix(clock).equals(cache.suffix(clock))) throw new AssertionError("stale timezone");
        System.out.println("PASS: two spaces, weekday mapping, midnight, year, leap day and timezone");
    }
}
