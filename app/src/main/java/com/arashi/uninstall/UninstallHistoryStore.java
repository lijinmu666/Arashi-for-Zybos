package com.arashi.uninstall;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public final class UninstallHistoryStore {
    public static final String TIME = "time";
    public static final String CALLER_UID = "caller_uid";
    public static final String CALLER_PACKAGE = "caller_package";
    public static final String CALLER_NAME = "caller_name";
    public static final String TARGET_PACKAGE = "target_package";
    public static final String TARGET_NAME = "target_name";
    public static final String ALLOWED = "allowed";
    public static final String REASON = "reason";

    private static final String TABLE = "records";
    private static Helper helper;

    private UninstallHistoryStore() {
    }

    public static synchronized void append(Context context, Bundle data) {
        if (data == null) return;
        ContentValues values = new ContentValues();
        values.put(TIME, data.getLong(TIME, System.currentTimeMillis()));
        values.put(CALLER_UID, data.getInt(CALLER_UID, -1));
        values.put(CALLER_PACKAGE, data.getString(CALLER_PACKAGE));
        values.put(CALLER_NAME, data.getString(CALLER_NAME));
        values.put(TARGET_PACKAGE, data.getString(TARGET_PACKAGE));
        values.put(TARGET_NAME, data.getString(TARGET_NAME));
        values.put(ALLOWED, data.getBoolean(ALLOWED, false) ? 1 : 0);
        values.put(REASON, data.getString(REASON));
        database(context).insertOrThrow(TABLE, null, values);
    }

    public static synchronized List<Record> read(Context context, long beforeId, int limit) {
        ArrayList<Record> records = new ArrayList<>();
        String selection = beforeId == Long.MAX_VALUE ? null : "_id < ?";
        String[] args = selection == null ? null : new String[] { Long.toString(beforeId) };
        try (Cursor cursor = database(context).query(TABLE,
                new String[] { "_id", TIME, CALLER_UID, CALLER_PACKAGE, CALLER_NAME,
                        TARGET_PACKAGE, TARGET_NAME, ALLOWED, REASON },
                selection, args, null, null, "_id DESC",
                Integer.toString(Math.max(1, Math.min(limit, 101))))) {
            while (cursor.moveToNext()) {
                records.add(new Record(
                        cursor.getLong(0), cursor.getLong(1), cursor.getInt(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5),
                        cursor.getString(6), cursor.getInt(7) != 0, cursor.getString(8)));
            }
        }
        return records;
    }

    public static synchronized int clear(Context context) {
        return database(context).delete(TABLE, null, null);
    }

    private static SQLiteDatabase database(Context context) {
        if (helper == null) {
            helper = new Helper(context.getApplicationContext());
        }
        return helper.getWritableDatabase();
    }

    public static final class Record {
        public final long id;
        public final long time;
        public final int callerUid;
        public final String callerPackage;
        public final String callerName;
        public final String targetPackage;
        public final String targetName;
        public final boolean allowed;
        public final String reason;

        private Record(long id, long time, int callerUid, String callerPackage,
                String callerName, String targetPackage, String targetName,
                boolean allowed, String reason) {
            this.id = id;
            this.time = time;
            this.callerUid = callerUid;
            this.callerPackage = callerPackage;
            this.callerName = callerName;
            this.targetPackage = targetPackage;
            this.targetName = targetName;
            this.allowed = allowed;
            this.reason = reason;
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        private Helper(Context context) {
            super(context, "uninstall_history.db", null, 1);
        }

        @Override public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE + " ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + TIME + " INTEGER NOT NULL, "
                    + CALLER_UID + " INTEGER NOT NULL, "
                    + CALLER_PACKAGE + " TEXT, "
                    + CALLER_NAME + " TEXT, "
                    + TARGET_PACKAGE + " TEXT, "
                    + TARGET_NAME + " TEXT, "
                    + ALLOWED + " INTEGER NOT NULL, "
                    + REASON + " TEXT)");
        }

        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        }
    }
}
