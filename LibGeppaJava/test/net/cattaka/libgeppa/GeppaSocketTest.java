
package net.cattaka.libgeppa;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class GeppaSocketTest {

    @Before
    public void setUp() throws Exception {
    }

    /**
     * Test to write empty frame.
     */
    @Test
    public void testWriteFrame1() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GeppaFragment fragment = new GeppaFragment(10);
        fragment.setLength(0);

        GeppaSocket.writeFrame(bout, fragment);

        assertArrayEquals(new byte[] {
                Constants.STX, 2, Constants.FRAMETYPE_DATA, 0, -3, -1, Constants.ETX
        }, bout.toByteArray());
    }

    /**
     * Test to write frame.
     */
    @Test
    public void testWriteFrame2() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GeppaFragment fragment = new GeppaFragment(10);
        fragment.writeData(new byte[] {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9
        });
        GeppaSocket.writeFrame(bout, fragment);

        assertArrayEquals(new byte[] {
                Constants.STX, 12, Constants.FRAMETYPE_DATA, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -23,
                -26, Constants.ETX,
        }, bout.toByteArray());
    }

    /**
     * Test constructor.
     */
    @Test
    public void testCreateGeppaSocket() throws Exception {
        IRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);
        assertNotNull(geppaSocket);
    }

    @Test
    public void testUpdateRecvSeq() throws Exception {
        IRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        setting.setRecvFragmentsNum(6);
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);

        assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));

        GeppaFragment[] gfs = TestUtil.pickGeppaFragment(geppaSocket, "mRecvFragments");

        {
            geppaSocket.updateRecvSeq(4);
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertEquals(2, gfs[2].getSeq());
            assertEquals(3, gfs[3].getSeq());
            assertEquals(4, gfs[4].getSeq());
            assertEquals(0, gfs[5].getSeq());

            assertTrue(gfs[0].getFrags().contains(FragmentFlag.LOST));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.LOST));
            assertTrue(gfs[2].getFrags().contains(FragmentFlag.LOST));
            assertTrue(gfs[3].getFrags().contains(FragmentFlag.LOST));
            assertTrue(gfs[4].getFrags().contains(FragmentFlag.LOST));
            assertFalse(gfs[5].getFrags().contains(FragmentFlag.LOST));
        }
        for (GeppaFragment gf : gfs) {
            gf.clear();
        }
        {
            geppaSocket.updateRecvSeq(7);
            assertEquals(6, gfs[0].getSeq());
            assertEquals(7, gfs[1].getSeq());
            assertEquals(0, gfs[2].getSeq());
            assertEquals(0, gfs[3].getSeq());
            assertEquals(0, gfs[4].getSeq());
            assertEquals(5, gfs[5].getSeq());

            assertTrue(gfs[0].getFrags().contains(FragmentFlag.LOST));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.LOST));
            assertFalse(gfs[2].getFrags().contains(FragmentFlag.LOST));
            assertFalse(gfs[3].getFrags().contains(FragmentFlag.LOST));
            assertFalse(gfs[4].getFrags().contains(FragmentFlag.LOST));
            assertTrue(gfs[5].getFrags().contains(FragmentFlag.LOST));
        }
    }

    @Test
    public void testUpdateSendSeq() throws Exception {
        IRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        setting.setSendFragmentsNum(6);
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);

        assertEquals(0, TestUtil.pickInt(geppaSocket, "mSendSeqTail"));

        GeppaFragment[] gfs = TestUtil.pickGeppaFragment(geppaSocket, "mSendFragments");

        {
            geppaSocket.updateSendSeq(4);
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertEquals(2, gfs[2].getSeq());
            assertEquals(3, gfs[3].getSeq());
            assertEquals(4, gfs[4].getSeq());
            assertEquals(0, gfs[5].getSeq());
        }
        for (GeppaFragment gf : gfs) {
            gf.clear();
        }
        {
            geppaSocket.updateSendSeq(7);
            assertEquals(6, gfs[0].getSeq());
            assertEquals(7, gfs[1].getSeq());
            assertEquals(0, gfs[2].getSeq());
            assertEquals(0, gfs[3].getSeq());
            assertEquals(0, gfs[4].getSeq());
            assertEquals(5, gfs[5].getSeq());
        }
    }

    /**
     * Check that when the seq were lost, the REQ_SEND frame are sent.
     */
    @Test
    public void testPoll_reqSend() throws Exception {
        TestRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        setting.setSendFragmentsNum(6);
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);

        { // Receive DATA seq=0 and seq=2, but seq=1 is missing
            byte[] testFrame1 = new byte[] {
                    Constants.STX, 7, Constants.FRAMETYPE_DATA, 0, 1, 2, 3, 4, 5, -12, -7,
                    Constants.ETX
            };
            byte[] testFrame2 = new byte[] {
                    Constants.STX, 7, Constants.FRAMETYPE_DATA, 2, 6, 7, 8, 9, 10, -27, -19,
                    Constants.ETX
            };
            byte[] testFrame3 = new byte[] {
                    Constants.STX, 7, Constants.FRAMETYPE_DATA, 4, 6, 7, 8, 9, 10, -27, -21,
                    Constants.ETX
            };
            rawSocket.getOuterOutputStream().write(testFrame1);
            rawSocket.getOuterOutputStream().write(testFrame2);
            rawSocket.getOuterOutputStream().write(testFrame3);
        }
        { // poll()
            geppaSocket.poll();
        }
        { // Check REQ_SEND was sended correctly.
            byte[] buf = new byte[8];
            assertEquals(8, rawSocket.getOuterInputStream().available());
            rawSocket.getOuterInputStream().read(buf);
            assertArrayEquals(new byte[] {
                    Constants.STX, 3, Constants.FRAMETYPE_REQ_SEND, 1, 3, -7, -2, Constants.ETX,
            }, buf);
        }
    }

    /**
     * Check that when the REQ_SEND frame were received, the frame are sent
     * again.
     */
    @Test
    public void testPoll_resend() throws Exception {
        TestRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        setting.setSendFragmentsNum(6);
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);

        {// Send DATA seq=0 and seq=1
            { // send seq=0
                byte seq = 0;
                geppaSocket.write(new byte[] {
                        1, 2, 3, 4, 5
                });
                geppaSocket.poll();
                assertEquals(12, rawSocket.getOuterInputStream().available());
                byte[] actual = new byte[12];
                rawSocket.getOuterInputStream().read(actual);
                assertArrayEquals(new byte[] {
                        Constants.STX, 7, Constants.FRAMETYPE_DATA, seq, 1, 2, 3, 4, 5, -12, -7,
                        Constants.ETX
                }, actual);
            }
            { // send seq=1
                byte seq = 1;
                geppaSocket.write(new byte[] {
                        6, 7, 8, 9, 10
                });
                geppaSocket.poll();
                assertEquals(12, rawSocket.getOuterInputStream().available());
                byte[] actual = new byte[12];
                rawSocket.getOuterInputStream().read(actual);
                assertArrayEquals(new byte[] {
                        Constants.STX, 7, Constants.FRAMETYPE_DATA, seq, 6, 7, 8, 9, 10, -27, -18,
                        Constants.ETX
                }, actual);
            }
        }
        { // send REQ_SEND of seq = 0 and check returned data frame.
            assertEquals(0, rawSocket.getOuterInputStream().available());
            rawSocket.getOuterOutputStream().write(new byte[] {
                    Constants.STX, 2, Constants.FRAMETYPE_REQ_SEND, 0, -4, -1, Constants.ETX
            });
            geppaSocket.poll();
            assertEquals(12, rawSocket.getOuterInputStream().available());
            byte[] actual = new byte[12];
            rawSocket.getOuterInputStream().read(actual);
            assertArrayEquals(new byte[] {
                    Constants.STX, 7, Constants.FRAMETYPE_DATA, 0, 1, 2, 3, 4, 5, -12, -7,
                    Constants.ETX
            }, actual);
        }
    }

    @Test
    public void testHandleSendBuf() throws IOException {
        TestRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        setting.setSendFragmentsNum(6);
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);

        {
            assertEquals(0, rawSocket.getOuterInputStream().available());
            geppaSocket.handleSendBuf();
            assertEquals(0, rawSocket.getOuterInputStream().available());
        }
        {
            byte[] actual = new byte[12];
            geppaSocket.write(new byte[] {
                    1, 2, 3, 4, 5
            });
            geppaSocket.handleSendBuf();
            assertEquals(12, rawSocket.getOuterInputStream().available());
            rawSocket.getOuterInputStream().read(actual);
            assertArrayEquals(new byte[] {
                    Constants.STX, 7, Constants.FRAMETYPE_DATA, 0, 1, 2, 3, 4, 5, -12, -7,
                    Constants.ETX
            }, actual);
        }
    }

    @Test
    public void testHandleRecvBuf() throws Exception {
        TestRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        setting.setMaxFrameSize(17);
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);

        GeppaFragment[] gfs = TestUtil.pickGeppaFragment(geppaSocket, "mRecvFragments");
        {
            byte[] testFrame = new byte[] {
                    Constants.STX, 7, Constants.FRAMETYPE_DATA, 0, 1, 2, 3, 4, 5, -12, -7,
                    Constants.ETX
            };
            rawSocket.getOuterOutputStream().write(testFrame);
            geppaSocket.handleRecvBuf();

            assertEquals(0, gfs[0].getSeq());
            assertEquals(5, gfs[0].getLength());
            assertFalse(gfs[0].getFrags().contains(FragmentFlag.LOST));
            assertArrayEquals(new byte[] {
                    1, 2, 3, 4, 5, 0, 0, 0, 0, 0
            }, gfs[0].getData());
        }
        {
            byte[] testFrame = new byte[] {
                    Constants.STX, 7, Constants.FRAMETYPE_DATA, 2, 6, 7, 8, 9, 10, -27, -19,
                    Constants.ETX
            };
            rawSocket.getOuterOutputStream().write(testFrame);
            geppaSocket.handleRecvBuf();

            assertEquals(0, gfs[0].getSeq());
            assertEquals(5, gfs[0].getLength());
            assertFalse(gfs[0].getFrags().contains(FragmentFlag.LOST));
            assertEquals(1, gfs[1].getSeq());
            assertEquals(0, gfs[1].getLength());
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.LOST));
            assertEquals(2, gfs[2].getSeq());
            assertEquals(5, gfs[2].getLength());
            assertFalse(gfs[2].getFrags().contains(FragmentFlag.LOST));

            assertArrayEquals(new byte[] {
                    1, 2, 3, 4, 5, 0, 0, 0, 0, 0
            }, gfs[0].getData());
            assertArrayEquals(new byte[] {
                    6, 7, 8, 9, 10, 0, 0, 0, 0, 0
            }, gfs[2].getData());
        }
    }

    @Test
    public void testWrite() throws Exception {
        TestRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        setting.setSendFragmentsNum(2);
        setting.setMaxFrameSize(8 + Constants.SIZE_FRAME_EXTRA);
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);
        GeppaFragment[] gfs = TestUtil.pickGeppaFragment(geppaSocket, "mSendFragments");
        { // fill mSendBuf
            assertTrue(geppaSocket.write(new byte[] {
                    0, 1, 2, 3, 4, 5, 6, 7
            }));
            assertTrue(geppaSocket.write(new byte[] {
                    8, 9
            }));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mSendSeqHead"));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mSendSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.SENT));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.SENT));
        }
        { // check it can not send because mSendBuf is full
            assertFalse(geppaSocket.write(new byte[] {
                    10, 11, 12, 13, 14, 15, 16, 17
            }));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mSendSeqHead"));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mSendSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.SENT));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.SENT));
        }
        {
            geppaSocket.poll();
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mSendSeqHead"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.SENT));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.SENT));
        }
        { // check again it can send because mSendBuf is full
            assertFalse(geppaSocket.write(new byte[] {
                    10, 11, 12, 13, 14, 15, 16, 17
            }));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mSendSeqHead"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.SENT));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.SENT));
        }
        { // send ACK of sendSeq 0-1 is received.
            rawSocket.getOuterOutputStream().write(
                    new byte[] {
                            Constants.STX, 4, Constants.FRAMETYPE_ACK, Constants.ACKFLAG_RECV, 1,
                            0, -3, -2, Constants.ETX
                    });
            geppaSocket.poll();
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqHead"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(0, gfs[1].getSeq());
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.SENT));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.SENT));
        }
        { // check again it become sendable because mSendBuf is empty
            assertTrue(geppaSocket.write(new byte[] {
                    10, 11, 12, 13, 14, 15, 16, 17
            }));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqHead"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqMid"));
            assertEquals(3, TestUtil.pickInt(geppaSocket, "mSendSeqTail"));
            assertEquals(2, gfs[0].getSeq());
            assertEquals(0, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.SENT));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.SENT));
        }
        {
            geppaSocket.poll();
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mSendSeqHead"));
            assertEquals(3, TestUtil.pickInt(geppaSocket, "mSendSeqMid"));
            assertEquals(3, TestUtil.pickInt(geppaSocket, "mSendSeqTail"));
            assertEquals(2, gfs[0].getSeq());
            assertEquals(0, gfs[1].getSeq());
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.SENT));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.SENT));
        }
    }

    @Test
    public void testRead() throws Exception {
        TestRawSocket rawSocket = new TestRawSocket();
        GeppaSetting setting = new GeppaSetting();
        setting.setRecvFragmentsNum(2);
        setting.setMaxFrameSize(8 + Constants.SIZE_FRAME_EXTRA);
        GeppaSocket geppaSocket = GeppaSocket.createGeppaSocket(rawSocket, setting);
        GeppaFragment[] gfs = TestUtil.pickGeppaFragment(geppaSocket, "mRecvFragments");
        { // send data, but it don't poll() yet.
            rawSocket.getOuterOutputStream().write(new byte[] {
                    Constants.STX, 3, Constants.FRAMETYPE_DATA, 0, 1, -4, -1, Constants.ETX
            });
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(0, gfs[1].getSeq());
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
        { // poll()
            geppaSocket.poll();
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(0, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
        { // send data and poll()
            rawSocket.getOuterOutputStream().write(new byte[] {
                    Constants.STX, 3, Constants.FRAMETYPE_DATA, 1, 2, -5, -2, Constants.ETX
            });
            geppaSocket.poll();
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
        { // send data and poll(), but it is disposed because mRecvFragments is
          // full.
            rawSocket.getOuterOutputStream().write(new byte[] {
                    Constants.STX, 3, Constants.FRAMETYPE_DATA, 2, 3, -6, -3, Constants.ETX
            });
            geppaSocket.poll();
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
        {
            byte[] bs = new byte[setting.getMaxFragmentSize()];
            int r = geppaSocket.read(bs);
            assertEquals(1, r);
            assertEquals(1, bs[0]);
            assertEquals(0, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
        { // send data and poll(), but it is disposed because mRecvFragments is
          // full.
            rawSocket.getOuterOutputStream().write(new byte[] {
                    Constants.STX, 3, Constants.FRAMETYPE_DATA, 2, 3, -6, -3, Constants.ETX
            });
            geppaSocket.poll();
            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
        { // send ACK
            rawSocket.getOuterOutputStream().write(
                    new byte[] {
                            Constants.STX, 4, Constants.FRAMETYPE_ACK, Constants.ACKFLAG_SEND, 0,
                            1, -2, -4, Constants.ETX
                    });
            geppaSocket.poll(true);
            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(2, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(0, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
        { // send data and poll() again
            rawSocket.getOuterOutputStream().write(new byte[] {
                    Constants.STX, 3, Constants.FRAMETYPE_DATA, 2, 3, -6, -3, Constants.ETX
            });
            geppaSocket.poll();
            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(3, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(2, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
        {
            {
                byte[] bs = new byte[setting.getMaxFragmentSize()];
                int r = geppaSocket.read(bs);
                assertEquals(1, r);
                assertEquals(2, bs[0]);
            }
            {
                byte[] bs = new byte[setting.getMaxFragmentSize()];
                int r = geppaSocket.read(bs);
                assertEquals(1, r);
                assertEquals(3, bs[0]);
            }
            {
                byte[] bs = new byte[setting.getMaxFragmentSize()];
                int r = geppaSocket.read(bs);
                assertEquals(0, r);
            }

            assertEquals(1, TestUtil.pickInt(geppaSocket, "mRecvSeqHead"));
            assertEquals(3, TestUtil.pickInt(geppaSocket, "mRecvSeqMid"));
            assertEquals(3, TestUtil.pickInt(geppaSocket, "mRecvSeqTail"));
            assertEquals(2, gfs[0].getSeq());
            assertEquals(1, gfs[1].getSeq());
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.READY));
            assertTrue(gfs[0].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(!gfs[0].getFrags().contains(FragmentFlag.NONREAD));
            assertTrue(gfs[1].getFrags().contains(FragmentFlag.READY));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.NONACKED));
            assertTrue(!gfs[1].getFrags().contains(FragmentFlag.NONREAD));
        }
    }
}
