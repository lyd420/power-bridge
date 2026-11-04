package dev.yide.powerbridge;

/** Main-looper data cache. Reconstructs CPS crank events from measured RPM, not native crank timestamps. */
public final class BridgeData {
    public static final long EXPIRY_MS=3000;
    private XdsMeasurement sample;
    private long received, cursor, revolutions, eventTicks;
    private double fraction;
    public void update(XdsMeasurement value,long now) {
        if (value==null) return;
        if (isFresh(now)) advance(now); else fraction=0;
        sample=value; received=cursor=now;
    }
    public boolean isFresh(long now) { return sample!=null && now>=received && now-received<EXPIRY_MS; }
    public int watts() { return sample==null ? 0 : sample.watts; }
    public int cadence() { return sample==null ? -1 : sample.cadence; }
    public void invalidate() { sample=null; fraction=0; }
    private void advance(long now) {
        if (sample.cadence>0 && now>cursor) {
            double total=fraction+(now-cursor)*sample.cadence/60000.0;
            long whole=(long)Math.floor(total+1e-9);
            fraction=Math.max(0,total-whole);
            if (whole>0) {
                revolutions+=whole;
                double eventMs=now-fraction*60000.0/sample.cadence;
                eventTicks=(long)Math.floor(eventMs*1024.0/1000.0+1e-7);
            }
        } else if (sample.cadence<=0) fraction=0;
        cursor=now;
    }
    public byte[] packet(long now) {
        if (!isFresh(now)) return null;
        advance(now);
        int w=sample.watts;
        if (sample.cadence<0) return new byte[]{0,0,(byte)w,(byte)(w>>8)};
        return new byte[]{32,0,(byte)w,(byte)(w>>8),(byte)revolutions,(byte)(revolutions>>8),(byte)eventTicks,(byte)(eventTicks>>8)};
    }
}
