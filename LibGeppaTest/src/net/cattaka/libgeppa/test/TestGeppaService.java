
package net.cattaka.libgeppa.test;

import net.cattaka.libgeppa.GeppaService;
import net.cattaka.libgeppa.test.data.MyPacket;
import net.cattaka.libgeppa.test.data.MyPacketFactory;

public class TestGeppaService extends GeppaService<MyPacket> {
    public static final String TARGET_DEVICE = "TestDevice";

    public TestGeppaService() {
        super("TestDevice", new MyPacketFactory());
    }
}
