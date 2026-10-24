package com.arashi.xposed;

import java.util.Calendar;

/** Uses the clock's own calendar so timezone and demo time stay consistent. */
public final class StatusBarClockFormat {
    private static final String[] WEEKDAYS = { "日", "一", "二", "三", "四", "五", "六" };

    private StatusBarClockFormat() {}

    public static String suffix(Calendar calendar) {
        return "  " + (calendar.get(Calendar.MONTH) + 1) + "月"
                + calendar.get(Calendar.DAY_OF_MONTH) + "日周"
                + WEEKDAYS[calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY];
    }
    /** Date text changes once per local day, independently of the seconds tick. */
    public static final class Cache {
        private int year = Integer.MIN_VALUE;
        private int month, day, weekday, era;
        private String text;

        public String suffix(Calendar calendar) {
            int currentYear = calendar.get(Calendar.YEAR);
            int currentMonth = calendar.get(Calendar.MONTH);
            int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
            int currentWeekday = calendar.get(Calendar.DAY_OF_WEEK);
            int currentEra = calendar.get(Calendar.ERA);
            if (text == null || year != currentYear || month != currentMonth
                    || day != currentDay || weekday != currentWeekday || era != currentEra) {
                year = currentYear;
                month = currentMonth;
                day = currentDay;
                weekday = currentWeekday;
                era = currentEra;
                text = StatusBarClockFormat.suffix(calendar);
            }
            return text;
        }
    }
}
