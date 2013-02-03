
package net.cattaka.libgeppa;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class GeppaFragmentTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testWriteFrame1() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GeppaFragment fragment = new GeppaFragment(10);
        fragment.setLength(0);

        GeppaSocket.writeFrame(bout, fragment);

        assertEquals(0, bout.toByteArray().length);
    }

    @Test
    public void testWriteFrame2() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GeppaFragment fragment = new GeppaFragment(10);
        fragment.writeData(new byte[] {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9
        });
        GeppaSocket.writeFrame(bout, fragment);

        assertArrayEquals(new byte[] {
                0, 0, 1, 2, 3, 4, 5, 6, 7, 8
        }, bout.toByteArray());
    }
}
