package dev.yide.powerbridge;

/** Observed GROWTAC P715 sample; payload semantics remain unknown. */
public final class P715Sample {
    private P715Sample() {}
    public static final String NAME = "P715 49688";
    public static byte[] manufacturerData() {
        return new byte[]{0x6b,0x00,0x18,(byte)0xc2,0x05,0x4c,0x01,0x01,0x01,0x06,(byte)0x83};
    }
}
