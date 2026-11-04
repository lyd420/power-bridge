package dev.yide.powerbridge;

/** Notification-only CCCD handling. -1: reject, 0: disable, 1: notify. */
public final class NotificationConfig {
    private NotificationConfig() {}
    public static int action(byte[] value, boolean allowCombined) {
        if(value==null || value.length!=2 || value[1]!=0)return -1;
        if(value[0]==0)return 0;
        // C606 writes both CCCD bits to the notify-only 2A63 in observed P715 mode.
        // Accept that exact interoperability exception; continue sending notifications.
        if(value[0]==1 || (allowCombined && value[0]==3))return 1;
        return -1;
    }
}
