package dev.yide.powerbridge;
import java.text.SimpleDateFormat;
import java.util.*;
/** Main-looper UI snapshot. */
public final class ProbeState {
    public static volatile boolean active, scanning, inputLinked, fresh;
    public static volatile int watts, cadence=-1, sent, packets, headConnections, headSubscriptions;
    public static volatile String inputStatus="等待开始", deviceName="AD350 功率计", headStatus="等待开始";
    public static volatile XdsMeasurement latest;
    public static volatile int battery=-1,rssi=-999,sensorStatus;
    public static volatile long batteryAt=-1,rssiAt=-1;
    public static volatile String manufacturer="",model="",firmware="",hardware="",inventory="";
    public static void clearSensorData(){latest=null;battery=-1;rssi=-999;batteryAt=rssiAt=-1;sensorStatus=0;manufacturer=model=firmware=hardware=inventory="";}
    public static volatile List<Nearby> devices=Collections.emptyList();
    public static final class Nearby {
        public final String address,name;
        public final int rssi;
        public final boolean xds;
        public Nearby(String address,String name,int rssi,boolean xds) { this.address=address; this.name=name; this.rssi=rssi; this.xds=xds; }
    }
    private static final ArrayDeque<String> events=new ArrayDeque<>();
    public static synchronized void log(String message) {
        android.util.Log.i("PowerBridgeProbe",message);
        events.addFirst(new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+"  "+message);
        while(events.size()>100) events.removeLast();
    }
    public static synchronized String logs() { StringBuilder b=new StringBuilder(); for(String e:events)b.append(e).append('\n'); return b.toString(); }
}
