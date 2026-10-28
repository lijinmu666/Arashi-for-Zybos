package com.lijinmu.arashi.tools;

import android.app.*;
import android.content.*;
import android.os.*;
import android.security.keystore.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import org.json.JSONObject;

/** Temporary loopback-only HTTPS source for the app store. Certificate is self-signed. */
public final class LocalApkServer extends Service {
    private static final String CHANNEL="local_apk_install";
    private volatile SSLServerSocket server;
    private final ExecutorService clients=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4));
    private final Handler handler=new Handler(Looper.getMainLooper());
    private volatile int generation;
    private void notifyStatus(String text) {
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(
            new NotificationChannel(CHANNEL,"本地 APK 安装",NotificationManager.IMPORTANCE_LOW));
        Intent stop=new Intent(this,LocalApkServer.class).setAction("stop");
        PendingIntent pending=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this,CHANNEL) : new Notification.Builder(this);
        startForeground(1042,builder.setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("本地 APK 安装").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止服务",pending).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent==null || "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        notifyStatus("正在启动本机 HTTPS 服务");
        int current=++generation;
        closeServer();
        String path=intent.getStringExtra("path"), request=intent.getStringExtra("request");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopSelf,10*60*1000L);
        new Thread(() -> serve(current,path,request),"LocalApkHttps").start();
        return START_NOT_STICKY;
    }
    private SSLContext tls() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        String alias="arashi-local-apk";
        if (!store.containsAlias(alias)) {
            KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=127.0.0.1"))
                .setCertificateSerialNumber(java.math.BigInteger.ONE)
                .setCertificateNotBefore(new Date(0))
                .setCertificateNotAfter(new Date(4102444800000L)).build());
            generator.generateKeyPair();
        }
        final PrivateKey key=(PrivateKey)store.getKey(alias,null);
        final java.security.cert.X509Certificate cert=(java.security.cert.X509Certificate)store.getCertificate(alias);
        X509KeyManager manager=new X509KeyManager() {
            public String[] getClientAliases(String t,java.security.Principal[] i){return null;}
            public String chooseClientAlias(String[] t,java.security.Principal[] i,Socket s){return null;}
            public String[] getServerAliases(String t,java.security.Principal[] i){return "RSA".equals(t)?new String[]{alias}:null;}
            public String chooseServerAlias(String t,java.security.Principal[] i,Socket s){return "RSA".equals(t)?alias:null;}
            public java.security.cert.X509Certificate[] getCertificateChain(String a){return new java.security.cert.X509Certificate[]{cert};}
            public PrivateKey getPrivateKey(String a){return key;}
        };
        SSLContext context=SSLContext.getInstance("TLS"); context.init(new KeyManager[]{manager},null,null); return context;
    }
    private void serve(int current,String path,String request) {
        SSLServerSocket listener=null;
        try {
            File file=new File(path);
            if (!file.isFile() || !file.getCanonicalPath().startsWith(getCacheDir().getCanonicalPath()+"/store-"))
                throw new IOException("APK 缓存不存在，请重新选择");
            String token=UUID.randomUUID().toString();
            listener=(SSLServerSocket)tls().getServerSocketFactory().createServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
            listener.setEnabledProtocols(new String[]{"TLSv1.2"});
            synchronized(this) {
                if(current!=generation) {listener.close();return;}
                server=listener;
            }
            JSONObject json=new JSONObject(request);
            json.getJSONObject("data").put("apkUrl","https://127.0.0.1:"+listener.getLocalPort()+"/"+token+".apk");
            Intent broadcast=new Intent("com.zuoyebang.iot.push")
                .setComponent(new ComponentName("com.zuoyebang.iot.pad.appstore","com.zuoyebang.iot.pad.push.PushMessageReceiver"))
                .putExtra("response",json.toString());
            sendBroadcast(broadcast);
            handler.post(() -> {if(current==generation) notifyStatus("安装广播已发送，HTTPS 服务保留 10 分钟；自签名证书可能被商店拒绝");});
            while(current==generation) {
                Socket socket=listener.accept();
                socket.setSoTimeout(15000);
                try { clients.execute(() -> respond(socket,file,"/"+token+".apk",current)); }
                catch(RejectedExecutionException e) {socket.close();}
            }
        } catch(Exception e) {
            if(current==generation) handler.post(() -> {if(current==generation) notifyStatus("服务异常："+e.getMessage()+"；请停止服务后重试");});
        } finally {try {if(listener!=null) listener.close();}catch(IOException ignored){}}
    }
    private void respond(Socket socket,File file,String route,int current) {
        try(Socket connection=socket) {
            BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII));
            String first=reader.readLine();
            if(first==null || first.length()>8192) return;
            String[] parts=first.split(" ");
            if(parts.length<2) return;
            String range=null,line;
            for(int n=0;n<100;n++) {
                line=reader.readLine();
                if(line==null || line.isEmpty()) break;
                if(line.length()>8192) return;
                if(line.toLowerCase(Locale.ROOT).startsWith("range:")) range=line.substring(6).trim();
            }
            OutputStream out=connection.getOutputStream();
            if(!route.equals(parts[1]) || !("GET".equals(parts[0]) || "HEAD".equals(parts[0]))) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); return;
            }
            long length=file.length(),start=0,end=length-1;
            boolean partial=range!=null;
            if(partial) {
                try {
                    if(!range.matches("bytes=\\d*-\\d*")) throw new IllegalArgumentException();
                    String[] span=range.substring(6).split("-",-1);
                    if(span[0].isEmpty()) {long suffix=Long.parseLong(span[1]);if(suffix<=0)throw new IllegalArgumentException();start=Math.max(0,length-suffix);}
                    else {start=Long.parseLong(span[0]);if(!span[1].isEmpty())end=Math.min(end,Long.parseLong(span[1]));}
                    if(start>end || start>=length)throw new IllegalArgumentException();
                } catch(Exception e) {
                    out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */"+length+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));return;
                }
            }
            long remaining=end-start+1;
            String headers="HTTP/1.1 "+(partial?"206 Partial Content":"200 OK")+"\r\nContent-Type: application/vnd.android.package-archive\r\nAccept-Ranges: bytes\r\nContent-Length: "+remaining+"\r\n"+(partial?"Content-Range: bytes "+start+"-"+end+"/"+length+"\r\n":"")+"Connection: close\r\n\r\n";
            out.write(headers.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            if("HEAD".equals(parts[0]))return;
            try(RandomAccessFile input=new RandomAccessFile(file,"r")) {
                input.seek(start);byte[] buffer=new byte[65536];
                while(remaining>0 && current==generation) {int count=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0)break;out.write(buffer,0,count);remaining-=count;}
            }
            out.flush();
        }catch(Exception ignored) { /* TLS rejection or a cancelled download; another request may retry. */ }
    }
    private synchronized void closeServer(){try {if(server!=null)server.close();}catch(IOException ignored){} server=null;}
    @Override public void onDestroy(){generation++;closeServer();clients.shutdownNow();handler.removeCallbacksAndMessages(null);stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
