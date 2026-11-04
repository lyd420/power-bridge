package dev.yide.powerbridge;

/** Actual XDS capture: ATT81 is followed by measurement notifications ~100ms later. */
public final class StartupWindow {
    private long deadline=Long.MAX_VALUE;
    private boolean receiving;
    public void onWriteComplete(int status,long now){
        if(receiving)return;
        deadline=now+(status==0?8000:status==129?5000:0);
    }
    public void onData(){receiving=true;deadline=Long.MAX_VALUE;}
    public boolean expired(long now){return !receiving&&now>=deadline;}
}
