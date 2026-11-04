import dev.yide.powerbridge.XdsMeasurement;
public class XdsMeasurementTest {
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        check(XdsMeasurement.parse(null)==null,"null");
        check(XdsMeasurement.parse(new byte[]{1})==null,"truncated watts");
        XdsMeasurement m=XdsMeasurement.parse(new byte[]{(byte)250,0,120,0,(byte)130,0,90,0,0,0,0});
        check(m.watts==250 && m.cadence==90,"XDS has no CPS flags prefix");
        check(m.leftWatts==120 && m.rightWatts==130 && m.angle==0,"extended fields");
        check(m.leftPercent()==48,"consistent reference balance");
        check(XdsMeasurement.parse(new byte[]{0,0}).leftWatts==null,"missing side fields are unknown");
        check(XdsMeasurement.parse(new byte[]{0,0,0,0,0,0,0,0,103,1,0}).angle==359,"angle LE");
        check(XdsMeasurement.parse(new byte[]{0,0,0,0,0,0,0,0,104,1,0}).angle==null,"invalid angle not wrapped");
        check(XdsMeasurement.parse(new byte[]{10,0,-1,-1,11,0}).leftPercent()==null,"negative side must not form balance");
        check(XdsMeasurement.parse(new byte[]{20,0,9,0,10,0}).leftPercent()==null,"inconsistent total must not form balance");
        check(XdsMeasurement.parse(new byte[]{1,0,0,0,1,0}).leftPercent()==null,"low power ratio is unstable");
        check(XdsMeasurement.parse(new byte[]{0,0,0,0,0,0}).leftPercent()==null,"stationary does not fake50/50");
        check(XdsMeasurement.parse(new byte[]{44,1}).watts==300,"little endian");
        check(XdsMeasurement.parse(new byte[]{44,1}).cadence==-1,"absent cadence");
        check(XdsMeasurement.parse(new byte[]{1,0,0,0,0,0,-1,-1}).cadence==0,"negative cadence");
        check(XdsMeasurement.parse(new byte[]{-1,127}).watts==32767,"largest CPS watts");
        check(XdsMeasurement.parse(new byte[]{0,-128})==null,"unsigned overflow must not become negative");
        check(XdsMeasurement.parse(new byte[]{1,0,0,0,0,0,90,0,0,0,2}).error==2,"sensor error");
        System.out.println("PASS: XDS private packet decoding");
    }
}
