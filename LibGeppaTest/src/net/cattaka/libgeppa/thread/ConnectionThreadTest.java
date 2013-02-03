
package net.cattaka.libgeppa.thread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.cattaka.libgeppa.IRawSocket;
import net.cattaka.libgeppa.data.ConnectionCode;
import net.cattaka.libgeppa.data.ConnectionState;
import net.cattaka.libgeppa.test.data.MyPacket;
import net.cattaka.libgeppa.test.data.MyPacketFactory;
import net.cattaka.libgeppa.test.data.TestRawSocket;
import net.cattaka.libgeppa.thread.ConnectionThread.IRawSocketPrepareTask;

public class ConnectionThreadTest extends TestCase {
    static class ThreadListener implements IConnectionThreadListener<MyPacket> {
        private List<MyPacket> mPackets = new ArrayList<MyPacket>();

        private List<ConnectionState> mStatus = new ArrayList<ConnectionState>();

        @Override
        public void onReceive(MyPacket packet) {
            mPackets.add(packet);
        }

        @Override
        public void onConnectionStateChanged(ConnectionState state, ConnectionCode code) {
            mStatus.add(state);
        }
    }

    static class RawSocketPrepareTaskImpl implements IRawSocketPrepareTask {
        private TestRawSocket rawSocket;

        public RawSocketPrepareTaskImpl() throws IOException {
            rawSocket = new TestRawSocket();
        }

        public RawSocketPrepareTaskImpl(TestRawSocket rawSocket) {
            super();
            this.rawSocket = rawSocket;
        }

        @Override
        public IRawSocket prepareRawSocket() {
            return rawSocket;
        }
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * This tests ConnectionThread can be stoped by #stopThread.
     */
    public void testStopThread() throws Exception {
        RawSocketPrepareTaskImpl prepareTask = new RawSocketPrepareTaskImpl();
        MyPacketFactory packetFactory = new MyPacketFactory();
        ThreadListener listener = new ThreadListener();
        ConnectionThread<MyPacket> connectionThread;

        connectionThread = new ConnectionThread<MyPacket>(prepareTask, packetFactory, listener,
                true);
        connectionThread.startThread();

        connectionThread.stopThread();
    }

    /**
     * This creates two ConnectionThreads(from-to). Then this tests send packet.
     */
    public void testSendPacket() throws Exception {
        RawSocketPrepareTaskImpl prepareTask = new RawSocketPrepareTaskImpl();
        ConnectionThread<MyPacket> fromThread;
        ThreadListener fromListener;
        { // Create and prepare fromThread
            MyPacketFactory packetFactory = new MyPacketFactory();
            fromListener = new ThreadListener();

            fromThread = new ConnectionThread<MyPacket>(prepareTask, packetFactory, fromListener,
                    true);
            fromThread.startThread();
        }
        ConnectionThread<MyPacket> toThread;
        ThreadListener toListener;
        { // Create and prepare toThread
            RawSocketPrepareTaskImpl otherPrepareTask = new RawSocketPrepareTaskImpl(
                    prepareTask.rawSocket.createOther());
            MyPacketFactory packetFactory = new MyPacketFactory();
            toListener = new ThreadListener();

            toThread = new ConnectionThread<MyPacket>(otherPrepareTask, packetFactory, toListener,
                    true);
            toThread.startThread();
        }

        { // Testing to send packet.
            MyPacket packet = new MyPacket((byte)3, (byte)5, new byte[] {
                    1, 2, 3, 4, 5
            });
            fromThread.sendPacket(packet);
            Thread.sleep(100);
            assertEquals(1, toListener.mPackets.size());
        }
        { // Testing ConnectionThread can be finished.
            prepareTask.rawSocket.close();
            Thread.sleep(100);
            assertEquals(3, fromListener.mStatus.size());
            assertEquals(ConnectionState.CONNECTING, fromListener.mStatus.get(0));
            assertEquals(ConnectionState.CONNECTED, fromListener.mStatus.get(1));
            assertEquals(ConnectionState.CLOSED, fromListener.mStatus.get(2));
            assertEquals(3, toListener.mStatus.size());
            assertEquals(ConnectionState.CONNECTING, toListener.mStatus.get(0));
            assertEquals(ConnectionState.CONNECTED, toListener.mStatus.get(1));
            assertEquals(ConnectionState.CLOSED, toListener.mStatus.get(2));
        }
    }
}
