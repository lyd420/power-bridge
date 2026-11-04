import dev.yide.powerbridge.P715Sample;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public class P715SampleTest {
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args) {
        byte[] data=P715Sample.manufacturerData();
        check(Arrays.equals(data,new byte[]{0x6b,0,0x18,(byte)0xc2,5,0x4c,1,1,1,6,(byte)0x83}),"photographed payload");
        check(P715Sample.NAME.equals("P715 49688"),"photographed name");
        int embedded=(data[2]&255)|((data[3]&255)<<8);
        check(embedded==49688 && P715Sample.NAME.endsWith(Integer.toString(embedded)),"sample bytes match name number, without assuming semantics");
        int primary=3+4+4+data.length,scan=2+P715Sample.NAME.getBytes(StandardCharsets.UTF_8).length;
        check(primary==22 && scan==12 && primary<=31 && scan<=31 && primary+scan>31,"legacy packet budget");
        data[0]=0;check(P715Sample.manufacturerData()[0]==0x6b,"immutable sample");
        System.out.println("PASS: 5 P715 sample consistency and packet budget checks");
    }
}
