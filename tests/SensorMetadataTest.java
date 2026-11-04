import dev.yide.powerbridge.SensorMetadata;
public class SensorMetadataTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  check(SensorMetadata.battery(new byte[]{0})==0,"zero is real battery");
  check(SensorMetadata.battery(new byte[]{100})==100,"full");
  check(SensorMetadata.battery(new byte[]{-1})==-1,"unknown sentinel not255%");
  check(SensorMetadata.battery(new byte[]{101})==-1,"invalid percentage");
  check(SensorMetadata.battery(null)==-1&&SensorMetadata.battery(new byte[]{1,2})==-1,"malformed");
  check(SensorMetadata.text(new byte[]{65,0,66,10}).equals("AB"),"control bytes stripped");
  check(SensorMetadata.text(null).isEmpty(),"missing text");
  check(SensorMetadata.fresh(0,119999,120000)&&!SensorMetadata.fresh(0,120000,120000),"metadata expiry");
  check(!SensorMetadata.fresh(-1,0,1000)&&!SensorMetadata.fresh(10,9,1000),"unknown or clock regression");
  System.out.println("PASS: battery validation, metadata text and expiry");
 }
}
