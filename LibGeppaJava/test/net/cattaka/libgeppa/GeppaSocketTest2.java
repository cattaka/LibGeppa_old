
package net.cattaka.libgeppa;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class GeppaSocketTest2 {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCreateGeppaSocket_single() throws Exception {
        GeppaSocket geppaSocketA;
        GeppaSocket geppaSocketB;
        GeppaSetting setting = new GeppaSetting();
        {
            TestRawSocket rawSocketA = new TestRawSocket();
            TestRawSocket rawSocketB = rawSocketA.createOther();
            geppaSocketA = GeppaSocket.createGeppaSocket(rawSocketA, setting);
            geppaSocketB = GeppaSocket.createGeppaSocket(rawSocketB, setting);
        }
        {
            byte[] data = new byte[] {
                    1, 2, 3, 4, 5
            };
            byte[] readBuf = new byte[5];
            geppaSocketA.write(data);
            geppaSocketA.poll();
            geppaSocketB.poll();
            int r = geppaSocketB.read(readBuf);
            assertEquals(data.length, r);
            assertArrayEquals(data, readBuf);
        }
    }

    @Test
    public void testCreateGeppaSocket_multi() throws Exception {
        GeppaSocket geppaSocketA;
        GeppaSocket geppaSocketB;
        GeppaSetting setting = new GeppaSetting();
        {
            TestRawSocket rawSocketA = new TestRawSocket();
            TestRawSocket rawSocketB = rawSocketA.createOther();
            geppaSocketA = GeppaSocket.createGeppaSocket(rawSocketA, setting);
            geppaSocketB = GeppaSocket.createGeppaSocket(rawSocketB, setting);
        }
        {
            int n = Math.max(setting.getRecvFragmentsNum(), setting.getSendFragmentsNum()) * 2;
            int count = 0;
            for (int s = 0; s < n; s++) {
                byte[] data = new byte[setting.getMaxFragmentSize()];
                byte[] readBuf = new byte[setting.getMaxFragmentSize()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte)count++;
                }
                assertTrue(String.format("s=%d", s), geppaSocketA.write(data));
                geppaSocketA.poll(false);
                geppaSocketB.poll(false);
                int r = geppaSocketB.read(readBuf);
                assertEquals(data.length, r);
                assertArrayEquals(data, readBuf);
            }
        }
    }
}
