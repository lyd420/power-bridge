import dev.yide.powerbridge.BridgeData;
import dev.yide.powerbridge.XdsMeasurement;
public class BridgeDataTest {
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    static XdsMeasurement sample(int w,int rpm) { return XdsMeasurement.parse(new byte[]{(byte)w,(byte)(w>>8),0,0,0,0,(byte)rpm,(byte)(rpm>>8)}); }
    static int u(byte[] p,int n) { return (p[n]&255)|((p[n+1]&255)<<8); }
    public static void main(String[] args) {
        BridgeData d=new BridgeData();
        check(d.packet(0)==null,"no input means no output");
        d.update(sample(250,90),0);
        check(d.isFresh(0) && d.watts()==250 && d.cadence()==90,"fresh first sample at zero clock");
        byte[] p=d.packet(1000);
        check(p.length==8 && u(p,0)==32 && u(p,2)==250,"CPS format");
        check(u(p,4)==1 && u(p,6)==682,"90rpm crank timing uses 1/1024s");
        d.update(sample(300,90),1000); p=d.packet(2000);
        check(u(p,4)==3 && u(p,6)==2048,"90rpm cumulative events");
        d.update(sample(0,0),2000); p=d.packet(4000);
        check(u(p,4)==3 && u(p,6)==2048 && u(p,2)==0,"zero cadence freezes crank events");
        check(d.packet(5000)==null,"expiry at three seconds");
        d.invalidate(); check(!d.isFresh(5000),"disconnect invalidates immediately");
        d.update(XdsMeasurement.parse(new byte[]{100,0}),6000);
        check(d.packet(6000).length==4 && d.cadence()==-1,"unknown cadence does not fabricate rpm");
        d.update(sample(250,60),10000); d.packet(11000); d.invalidate();
        d.update(sample(250,60),71000); p=d.packet(72000);
        check(u(p,4)==5,"no revolutions backfilled across disconnect");
        check(u(p,6)==8192,"crank event time wraps at 64 seconds");
        d.update(sample(250,60),80000); check(d.packet(79999)==null,"clock regression cannot be fresh");
        BridgeData dual = new BridgeData();
        check(dual.cadencePacket(0)==null,"CSC requires real input");
        dual.update(sample(250,90),0);
        byte[] power = dual.packet(2000);
        byte[] cadence = dual.cadencePacket(2000);
        check(cadence.length==5 && cadence[0]==2,"CSC advertises crank data only");
        check(u(cadence,1)==u(power,4) && u(cadence,3)==u(power,6),"CPS and CSC share the same crank events");
        dual.update(sample(0,0),2000);
        check(java.util.Arrays.equals(cadence,dual.cadencePacket(4000)),"stopping does not manufacture crank events");
        check(dual.cadencePacket(5000)==null,"CSC expires stale input");
        dual.update(XdsMeasurement.parse(new byte[]{100,0}),6000);
        check(dual.cadencePacket(6000)==null,"missing RPM does not create CSC data");
        System.out.println("PASS: real output, cadence timing, loss, freshness and rollover");
    }
}
