from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
java_dir = root / "app/src/main/java/com/lijinmu/arashi/tools"
ET.parse(root / "app/src/main/AndroidManifest.xml")
source = (java_dir / "LocalApkServer.java").read_text()
method = source[source.index("    private void respond("):source.index("    private synchronized void closeServer")]
harness = r'''
import java.io.*;
import javax.net.ssl.*;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public class HttpHarness {
  int generation=1;
  final java.util.concurrent.atomic.AtomicBoolean requestSeen=new java.util.concurrent.atomic.AtomicBoolean();
  void report(int current,String text,Throwable error) {
    if(error!=null) throw new AssertionError(text,error);
  }
  METHOD
  static byte[] request(String request, File file) throws Exception {
    try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
        Socket client=new Socket("127.0.0.1",server.getLocalPort())) {
      Thread thread=new Thread(() -> {
        try {new HttpHarness().respond(server.accept(),file,"/test.apk",1);}
        catch(Exception e){throw new RuntimeException(e);}
      });thread.start();
      client.setSoTimeout(2000);
      client.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      client.getOutputStream().flush();
      byte[] response=client.getInputStream().readAllBytes();thread.join();return response;
    }
  }
  static void check(String request, String status, String body, File file) throws Exception {
    String response=new String(request(request,file),StandardCharsets.US_ASCII);
    String[] parts=response.split("\r\n\r\n",2);
    if(!parts[0].startsWith("HTTP/1.1 "+status) || !parts[1].equals(body))
      throw new AssertionError(response);
  }
  public static void main(String[] args) throws Exception {
    File file=File.createTempFile("apk", ".apk");
    try {
      java.nio.file.Files.writeString(file.toPath(),"0123456789");
      check("GET /test.apk HTTP/1.1\r\n\r\n","200","0123456789",file);
      check("HEAD /test.apk HTTP/1.1\r\n\r\n","200","",file);
      check("GET /test.apk HTTP/1.1\r\nRange: bytes=2-5\r\n\r\n","206","2345",file);
      check("GET /test.apk HTTP/1.1\r\nRange: bytes=7-\r\n\r\n","206","789",file);
      check("GET /test.apk HTTP/1.1\r\nRange: bytes=-3\r\n\r\n","206","789",file);
      check("GET /test.apk HTTP/1.1\r\nRange: bytes=99-\r\n\r\n","416","",file);
      check("GET /test.apk HTTP/1.1\r\nRange: bytes=5-2\r\n\r\n","416","",file);
      check("GET /test.apk HTTP/1.1\r\nRange: bytes=0-1,4-5\r\n\r\n","416","",file);
      check("GET /wrong.apk HTTP/1.1\r\n\r\n","404","",file);
      check("POST /test.apk HTTP/1.1\r\n\r\n","404","",file);
      System.out.println("PASS: GET, HEAD, bounded/open/suffix ranges, invalid ranges, route and method rejection");
    } finally {file.delete();}
  }
}
'''
with tempfile.TemporaryDirectory() as directory:
    harness_path = Path(directory) / "HttpHarness.java"
    harness_path.write_text(harness.replace("METHOD",method))
    subprocess.run(["java", str(harness_path)],check=True)
ui=(root / "app/src/main/kotlin/com/lijinmu/arashi/ui/MiuixPages.kt").read_text()
assert "rememberLauncherForActivityResult" in ui and "useLabelAsPlaceholder = false" in ui
installer=(java_dir / "StoreLinkInstaller.java").read_text()
assert '? 100 :' in installer and 'https://www.mcmsf.com/upload/fwq/69/cover.png' in installer
print("PASS: manifest XML and shared installer defaults")
