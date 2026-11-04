import dev.yide.powerbridge.StartupWindow;
public class StartupWindowTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        StartupWindow w=new StartupWindow();
        w.onWriteComplete(129,0);
        check(!w.expired(0),"observed ATT81 must not disconnect immediately");
        check(!w.expired(100),"keep subscription for observed packet 100ms later");
        w.onData();check(!w.expired(6000),"valid measurement keeps streaming after ATT81");
        w.onWriteComplete(129,7000);check(!w.expired(20000),"late write error cannot tear down a working stream");
        w=new StartupWindow();w.onWriteComplete(129,0);
        check(!w.expired(4999)&&w.expired(5000),"no data still has bounded grace period");
        w=new StartupWindow();w.onWriteComplete(3,0);check(w.expired(0),"other write errors remain fatal");
        w=new StartupWindow();w.onWriteComplete(0,0);check(!w.expired(100)&&w.expired(8000),"successful writes still require real measurements");
        System.out.println("PASS: captured ATT81 -> measurement event sequence and bounded startup");
    }
}
