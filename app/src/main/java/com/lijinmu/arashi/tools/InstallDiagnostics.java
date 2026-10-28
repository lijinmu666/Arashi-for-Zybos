package com.lijinmu.arashi.tools;
import android.content.Context;
import android.util.Log;
public final class InstallDiagnostics {
    public static final String PREFS="store_install_diagnostics";
    private InstallDiagnostics() {}
    public static synchronized void record(Context context,String message,Throwable error) {
        if(error==null) Log.i("Arashi-StoreInstall",message); else Log.e("Arashi-StoreInstall",message,error);
        String detail=error==null ? message : message+"\n"+error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage());
        String time=new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date());
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String next=prefs.getString("history","")+"\n"+time+" "+detail;
        if(next.length()>10000)next=next.substring(next.length()-10000);
        prefs.edit().putString("history",next.trim()).apply();
    }
    public static synchronized void reset(Context context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove("history").apply();
    }
}
