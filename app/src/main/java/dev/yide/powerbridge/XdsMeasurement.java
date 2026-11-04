package dev.yide.powerbridge;

/** Community XDS private format, NOT a standard CPS packet. */
public final class XdsMeasurement {
    public final int watts, cadence, error;
    public final Integer leftWatts,rightWatts,angle;
    private XdsMeasurement(int watts,int cadence,int error,Integer left,Integer right,Integer angle) { this.watts=watts; this.cadence=cadence; this.error=error;this.leftWatts=left;this.rightWatts=right;this.angle=angle; }
    /** Reference split; low power rounding makes percentages misleading. */
    public Integer leftPercent(){
        if(watts<10||leftWatts==null||rightWatts==null||leftWatts<0||rightWatts<0||leftWatts+rightWatts!=watts)return null;
        return (int)Math.round(leftWatts*100.0/watts);
    }
    private static int u16(byte[] b,int n) { return (b[n]&255)|((b[n+1]&255)<<8); }
    public static XdsMeasurement parse(byte[] b) {
        if (b==null || b.length<2) return null;
        int watts=u16(b,0);
        if (watts>Short.MAX_VALUE) return null;
        int cadence=b.length>=8 ? Math.max(0,(short)u16(b,6)) : -1;
        Integer angle=b.length>=10?u16(b,8):null;
        if(angle!=null&&angle>359)angle=null;
        return new XdsMeasurement(watts,cadence,b.length>=11 ? b[10]&255 : 0,b.length>=4?(int)(short)u16(b,2):null,b.length>=6?(int)(short)u16(b,4):null,angle);
    }
}
