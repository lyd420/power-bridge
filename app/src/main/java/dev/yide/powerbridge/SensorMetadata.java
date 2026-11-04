package dev.yide.powerbridge;
import java.nio.charset.StandardCharsets;
public final class SensorMetadata {
 private SensorMetadata(){}
 public static int battery(byte[] b){return b!=null&&b.length==1&&(b[0]&255)<=100?b[0]&255:-1;}
 public static String text(byte[] b){return b==null?"":new String(b,0,Math.min(b.length,96),StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}]","").trim();}
 public static boolean fresh(long timestamp,long now,long ttl){return timestamp>=0&&now>=timestamp&&now-timestamp<ttl;}
}
