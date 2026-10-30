package com.lijinmu.arashi.logs;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import com.lijinmu.arashi.BuildConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The app process is the only database writer, including logs received from hooked processes. */
public final class RuntimeLogStore {
    public static final String PREFS = "runtime_logging";
    public static final String KEY_ENABLED = "enabled";
    private static final String ENABLED_SINCE = "enabled_since";
    public static final long RETENTION_MILLIS = 12L * 60 * 60 * 1000;
    public static final int MAX_RECORDS = 10000;
    private static final String EXPIRED = "time <= ? OR time > ?";
    private static final String PAGE = "_id < ? AND time > ? AND time <= ?";
    private static final String TRIM = "DELETE FROM logs WHERE _id NOT IN "
            + "(SELECT _id FROM logs ORDER BY _id DESC LIMIT 10000)";
    private static Helper helper;

    private RuntimeLogStore() {}

    private static SharedPreferences settings(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return settings(context).getBoolean(KEY_ENABLED, false);
    }

    /** Shares the writer lock so a late local/IPC write cannot undo a disable-and-clear. */
    public static synchronized void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = settings(context);
        if (!enabled) {
            boolean saved = prefs.edit().putBoolean(KEY_ENABLED, false).commit();
            clear(context);
            if (!saved) throw new IllegalStateException("无法保存日志开关");
            return;
        }
        if (isEnabled(context)) return;
        clear(context);
        long now = System.currentTimeMillis();
        if (!prefs.edit().putLong(ENABLED_SINCE, now).putBoolean(KEY_ENABLED, true).commit()) {
            throw new IllegalStateException("无法保存日志开关");
        }
        // Record the switch-on immediately, even when it shares a millisecond with clearing.
        Bundle first = new Bundle();
        first.putString("level", "INFO");
        first.putString("tag", "Logs");
        first.putString("process", context.getPackageName());
        first.putInt("pid", android.os.Process.myPid());
        first.putString("message", "日志记录已开启");
        insert(database(context), first, now);
    }

    private static SQLiteDatabase database(Context context) {
        if (helper == null) helper = new Helper(context.getApplicationContext());
        return helper.getWritableDatabase();
    }

    private static String text(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "\n[内容已截断]";
    }

    public static synchronized void append(Context context, Bundle data) {
        ArrayList<Bundle> batch = new ArrayList<>();
        batch.add(data);
        appendBatch(context, batch);
    }

    public static synchronized void appendBatch(Context context, List<Bundle> entries) {
        if (!isEnabled(context)) return;
        SQLiteDatabase db = database(context);
        long now = System.currentTimeMillis();
        long enabledSince = settings(context).getLong(ENABLED_SINCE, now);
        if (enabledSince > now) {
            enabledSince = now - 1;
            settings(context).edit().putLong(ENABLED_SINCE, enabledSince).commit();
        }
        db.beginTransaction();
        try {
            prune(db, now);
            long clearedBefore = 0;
            try (Cursor cursor = db.rawQuery("SELECT cleared_before FROM state WHERE _id = 1", null)) {
                if (cursor.moveToFirst()) clearedBefore = cursor.getLong(0);
            }
            // Reset a stale watermark if the device clock was moved backwards.
            if (clearedBefore > now) {
                clearedBefore = now - 1;
                db.execSQL("UPDATE state SET cleared_before = ? WHERE _id = 1", new Object[] {clearedBefore});
            }
            for (Bundle entry : entries) {
                if (entry == null) continue;
                long time = Math.min(now, entry.getLong("time", now));
                if (time <= now - RETENTION_MILLIS || time <= clearedBefore || time <= enabledSince) continue;
                insert(db, entry, time);
            }
            db.execSQL(TRIM);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void insert(SQLiteDatabase db, Bundle entry, long time) {
        ContentValues values = new ContentValues();
        values.put("time", time);
        values.put("level", text(entry.getString("level"), 8));
        values.put("tag", text(entry.getString("tag"), 128));
        values.put("process", text(entry.getString("process"), 256));
        values.put("pid", entry.getInt("pid", -1));
        values.put("message", text(entry.getString("message"), 12000));
        db.insertOrThrow("logs", null, values);
    }

    private static void prune(SQLiteDatabase db, long now) {
        db.delete("logs", EXPIRED,
                new String[] {Long.toString(now - RETENTION_MILLIS), Long.toString(now)});
    }

    public static synchronized void prune(Context context) {
        if (!isEnabled(context)) {
            clear(context);
            return;
        }
        prune(database(context), System.currentTimeMillis());
    }

    public static synchronized List<Record> read(Context context, long beforeId, int limit) {
        if (!isEnabled(context)) {
            clear(context);
            return new ArrayList<>();
        }
        SQLiteDatabase db = database(context);
        long now = System.currentTimeMillis();
        prune(db, now);
        ArrayList<Record> records = new ArrayList<>();
        try (Cursor cursor = db.query("logs",
                new String[] {"_id", "time", "level", "tag", "process", "pid", "message"},
                PAGE, new String[] {Long.toString(beforeId), Long.toString(now - RETENTION_MILLIS),
                        Long.toString(now)}, null, null, "_id DESC",
                Integer.toString(Math.max(1, Math.min(limit, 101))))) {
            while (cursor.moveToNext()) records.add(record(cursor));
        }
        return records;
    }

    public static synchronized int clear(Context context) {
        SQLiteDatabase db = database(context);
        db.beginTransaction();
        try {
            int count = db.delete("logs", null, null);
            // A log queued before clearing must not reappear when its worker next runs.
            db.execSQL("UPDATE state SET cleared_before = ? WHERE _id = 1",
                    new Object[] {System.currentTimeMillis()});
            db.setTransactionSuccessful();
            return count;
        } finally {
            db.endTransaction();
        }
    }

    /** Stream the complete retained snapshot, rather than only the visible UI page. */
    public static synchronized int export(Context context, OutputStream output) throws IOException {
        if (!isEnabled(context)) throw new IOException("日志功能已关闭");
        SQLiteDatabase db = database(context);
        long now = System.currentTimeMillis();
        prune(db, now);
        write(output, "Arashi for Zybos 运行日志\n版本：" + BuildConfig.VERSION_NAME
                + "\n导出时间：" + date(now) + "\n保留范围：最近 12 小时\n\n");
        int count = 0;
        try (Cursor cursor = db.query("logs",
                new String[] {"_id", "time", "level", "tag", "process", "pid", "message"},
                "time > ? AND time <= ?",
                new String[] {Long.toString(now - RETENTION_MILLIS), Long.toString(now)},
                null, null, "_id ASC")) {
            while (cursor.moveToNext()) {
                write(output, format(record(cursor)) + "\n\n");
                count++;
            }
        }
        if (count == 0) write(output, "暂无日志\n");
        output.flush();
        return count;
    }

    private static void write(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String date(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date(time));
    }

    public static String format(Record record) {
        return date(record.time) + " [" + record.level + "] " + record.tag
                + "\n进程：" + record.process + " (" + record.pid + ")\n" + record.message;
    }

    private static Record record(Cursor cursor) {
        return new Record(cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                cursor.getString(3), cursor.getString(4), cursor.getInt(5), cursor.getString(6));
    }

    public static final class Record {
        public final long id, time;
        public final int pid;
        public final String level, tag, process, message;
        private Record(long id, long time, String level, String tag, String process, int pid, String message) {
            this.id = id; this.time = time; this.level = level; this.tag = tag;
            this.process = process; this.pid = pid; this.message = message;
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) { super(context, "runtime_logs.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE logs (_id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, "
                    + "level TEXT NOT NULL, tag TEXT NOT NULL, process TEXT NOT NULL, pid INTEGER NOT NULL, "
                    + "message TEXT NOT NULL)");
            db.execSQL("CREATE INDEX logs_time ON logs(time)");
            db.execSQL("CREATE TABLE state (_id INTEGER PRIMARY KEY, cleared_before INTEGER NOT NULL)");
            db.execSQL("INSERT INTO state VALUES (1, 0)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    }
}
