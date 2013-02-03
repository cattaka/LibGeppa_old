
package net.cattaka.libgeppa.thread;

import static net.cattaka.libgeppa.test.data.MyPacketFactory.ETX;
import static net.cattaka.libgeppa.test.data.MyPacketFactory.PACKET_TYPE_DATA;
import static net.cattaka.libgeppa.test.data.MyPacketFactory.STX;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import junit.framework.TestCase;
import net.cattaka.libgeppa.test.data.MyPacket;
import net.cattaka.libgeppa.test.data.MyPacketFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class ReceiveThreadTest extends TestCase {
    static final int EVENT_RECEIVE = 1;

    static final int EVENT_ERROR = 2;

    static class MyHandler extends Handler {
        List<MyPacket> mPackets = new ArrayList<MyPacket>();

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RECEIVE: {
                    mPackets.add((MyPacket)msg.obj);
                    break;
                }
                case EVENT_ERROR: {
                    break;
                }
            }

        }
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testReceive() throws Exception {
        class MyThread extends Thread {
            MyHandler mHandler;

            Semaphore mSemaphore = new Semaphore(1);

            public MyThread() throws InterruptedException {
                mSemaphore.acquire();
            }

            public void run() {
                Looper.prepare();
                mHandler = new MyHandler();
                mSemaphore.release();
                Looper.loop();
            };

            public void waitForCreate() throws InterruptedException {
                mSemaphore.acquire();
            }
        }
        ReceiveThread<MyPacket> receiveThread;
        PipedOutputStream out;
        MyThread thread;
        MyPacketFactory packetFactory;
        {
            out = new PipedOutputStream();
            InputStream inputStream = new PipedInputStream(out);
            packetFactory = new MyPacketFactory();
            thread = new MyThread();
            thread.start();
            thread.waitForCreate();
            receiveThread = new ReceiveThread<MyPacket>(EVENT_RECEIVE, EVENT_ERROR,
                    thread.mHandler, inputStream, packetFactory);
            receiveThread.startThread("test");
        }
        {
            out.write(new byte[] {
                    STX, PACKET_TYPE_DATA, 1, 0x03, 0x00, 5, 1, 2, 3, ETX,//
                    STX, PACKET_TYPE_DATA, 1, 0x02, 0x00, 4, 4, 5, ETX,
            });
            Thread.sleep(100);
            assertEquals(2, thread.mHandler.mPackets.size());
            {
                MyPacket packet = thread.mHandler.mPackets.get(0);
                assertEquals(1, packet.getOpCode());
                assertEquals(3, packet.getDataLen());
                assertEquals(1, packet.getData()[0]);
                assertEquals(2, packet.getData()[1]);
                assertEquals(3, packet.getData()[2]);
            }
            {
                MyPacket packet = thread.mHandler.mPackets.get(1);
                assertEquals(1, packet.getOpCode());
                assertEquals(2, packet.getDataLen());
                assertEquals(4, packet.getData()[0]);
                assertEquals(5, packet.getData()[1]);
            }
        }
        {
            {
                thread.mHandler.mPackets.clear();
                MyPacket packet = new MyPacket((byte)3, (byte)5, new byte[] {
                        1, 2, 3, 4, 5
                });
                packetFactory.writePacket(out, packet);
            }
            Thread.sleep(100);
            assertEquals(1, thread.mHandler.mPackets.size());
            {
                MyPacket packet = thread.mHandler.mPackets.get(0);
                assertEquals(3, packet.getOpCode());
                assertEquals(5, packet.getDataLen());
                assertEquals(1, packet.getData()[0]);
                assertEquals(2, packet.getData()[1]);
                assertEquals(3, packet.getData()[2]);
                assertEquals(4, packet.getData()[3]);
                assertEquals(5, packet.getData()[4]);
            }
        }
    }

    public void testStopThread() throws Exception {
        class MyThread extends Thread {
            MyHandler mHandler;

            Semaphore mSemaphore = new Semaphore(1);

            public MyThread() throws InterruptedException {
                mSemaphore.acquire();
            }

            public void run() {
                Looper.prepare();
                mHandler = new MyHandler();
                mSemaphore.release();
                Looper.loop();
            };

            public void waitForCreate() throws InterruptedException {
                mSemaphore.acquire();
            }
        }
        ReceiveThread<MyPacket> receiveThread;
        PipedOutputStream out;
        MyThread thread;
        MyPacketFactory packetFactory;
        {
            out = new PipedOutputStream();
            InputStream inputStream = new PipedInputStream(out);
            packetFactory = new MyPacketFactory();
            thread = new MyThread();
            thread.start();
            thread.waitForCreate();
            receiveThread = new ReceiveThread<MyPacket>(EVENT_RECEIVE, EVENT_ERROR,
                    thread.mHandler, inputStream, packetFactory);
            receiveThread.startThread("test");
        }
        receiveThread.stopThread();
    }
}
