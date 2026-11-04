import dev.yide.powerbridge.NotificationConfig;
public class NotificationConfigTest {
    private static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    public static void main(String[] args) {
        check(NotificationConfig.action(new byte[]{1,0},false)==1,"ordinary notify subscription");
        check(NotificationConfig.action(new byte[]{0,0},false)==0,"unsubscribe");
        check(NotificationConfig.action(new byte[]{3,0},true)==1,"C606 observed 03 00 must start notifications in P715 compatibility mode");
        check(NotificationConfig.action(new byte[]{3,0},false)==-1,"combined write remains opt-in");
        check(NotificationConfig.action(new byte[]{2,0},true)==-1,"indication-only unsupported");
        check(NotificationConfig.action(null,true)==-1 && NotificationConfig.action(new byte[]{1},true)==-1 && NotificationConfig.action(new byte[]{1,0,0},true)==-1,"invalid lengths");
        check(NotificationConfig.action(new byte[]{1,1},true)==-1 && NotificationConfig.action(new byte[]{7,0},true)==-1,"reserved bits rejected");
        check(NotificationConfig.action(new byte[]{0,0},true)==0 && NotificationConfig.action(new byte[]{1,0},true)==1,"ordinary operations in compatibility mode");
        System.out.println("PASS: 8 CCCD subscription and compatibility regression checks");
    }
}
