package com.lijinmu.arashi.tcp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

public final class TcpHistoryStore {
    public static final String TIME = "time";
    public static final String ERROR_TYPE = "error_type";
    public static final String ERROR_CODE = "error_code";
    public static final String SOURCE = "source";
    public static final String DETAILS = "details";
    public static final String STACK = "stack";
    public static final String PROCESS = "process";
    public static final String PID = "pid";
    private static Helper helper;

    private TcpHistoryStore() {}

    public static synchronized long append(Context context, Bundle data) {
        ContentValues values = new ContentValues();
        values.put(TIME, data.getLong(TIME, System.currentTimeMillis()));
        values.put(ERROR_TYPE, data.getInt(ERROR_TYPE, -1));
        values.put(ERROR_CODE, text(data.getString(ERROR_CODE), 128));
        values.put(SOURCE, text(data.getString(SOURCE), 256));
        values.put(DETAILS, text(data.getString(DETAILS), 12000));
        values.put(STACK, text(data.getString(STACK), 12000));
        values.put(PROCESS, text(data.getString(PROCESS), 256));
        values.put(PID, data.getInt(PID, -1));
        return database(context).insertOrThrow("records", null, values);
    }

    private static String text(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }

    public static synchronized List<Record> read(Context context, long beforeId, int limit) {
        ArrayList<Record> records = new ArrayList<>();
        String where = beforeId == Long.MAX_VALUE ? null : "_id < ?";
        String[] args = where == null ? null : new String[] { Long.toString(beforeId) };
        try (Cursor cursor = database(context).query("records",
                new String[] { "_id", TIME, ERROR_TYPE, ERROR_CODE, SOURCE, DETAILS, STACK, PROCESS, PID },
                where, args, null, null, "_id DESC", Integer.toString(Math.max(1, Math.min(limit, 101))))) {
            while (cursor.moveToNext()) {
                records.add(new Record(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5),
                        cursor.getString(6), cursor.getString(7), cursor.getInt(8)));
            }
        }
        return records;
    }

    public static synchronized int clear(Context context) {
        return database(context).delete("records", null, null);
    }

    private static SQLiteDatabase database(Context context) {
        if (helper == null) helper = new Helper(context.getApplicationContext());
        return helper.getWritableDatabase();
    }

    public static final class Record {
        public final long id, time;
        public final int errorType, pid;
        public final String errorCode, source, details, stack, process;
        private Record(long id, long time, int errorType, String errorCode, String source,
                String details, String stack, String process, int pid) {
            this.id = id; this.time = time; this.errorType = errorType;
            this.errorCode = errorCode; this.source = source; this.details = details;
            this.stack = stack; this.process = process; this.pid = pid;
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) { super(context, "tcp_login_history.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE records (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + TIME + " INTEGER NOT NULL, " + ERROR_TYPE + " INTEGER NOT NULL, "
                    + ERROR_CODE + " TEXT, " + SOURCE + " TEXT, " + DETAILS + " TEXT, "
                    + STACK + " TEXT, " + PROCESS + " TEXT, " + PID + " INTEGER NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    }
}
