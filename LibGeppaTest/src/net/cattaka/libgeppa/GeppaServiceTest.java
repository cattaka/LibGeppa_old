
package net.cattaka.libgeppa;

import java.util.ArrayList;
import java.util.List;

import net.cattaka.libgeppa.bluetooth.BluetoothAdapterFactory;
import net.cattaka.libgeppa.data.ConnectionState;
import net.cattaka.libgeppa.data.PacketWrapper;
import net.cattaka.libgeppa.test.TestGeppaService;
import net.cattaka.libgeppa.test.bluetooth.TestBtAdapterFactory;
import net.cattaka.libgeppa.test.bluetooth.TestBtDevice;
import net.cattaka.libgeppa.test.data.MyPacket;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.test.ServiceTestCase;

public class GeppaServiceTest extends ServiceTestCase<TestGeppaService> {
    private TestBtAdapterFactory mBtAdapterFactory;

    static class GeppaServiceListenerImpl extends IPassiveGeppaServiceListener.Stub {
        private List<ConnectionState> status = new ArrayList<ConnectionState>();

        private List<MyPacket> packets = new ArrayList<MyPacket>();

        @Override
        public void onConnectionStateChanged(ConnectionState state) throws RemoteException {
            status.add(state);
        }

        @Override
        public void onReceivePacket(PacketWrapper packetWrapper) throws RemoteException {
            packets.add((MyPacket)packetWrapper.getPacket());
        }
    }

    public GeppaServiceTest() {
        super(TestGeppaService.class);
    }

    protected void setUp() throws Exception {
        super.setUp();

        mBtAdapterFactory = new TestBtAdapterFactory();
        BluetoothAdapterFactory.replaceFactory(mBtAdapterFactory);
    }

    public void testStartStopService_noDevice() throws Exception {
        Intent intent = new Intent();
        IBinder iBinder = bindService(intent);
        IPassiveGeppaService service = IPassiveGeppaService.Stub.asInterface(iBinder);
        for (int i = 0; i < 10; i++) {
            if (!service.isConnected()) {
                break;
            }
            Thread.sleep(100);
        }
        assertFalse(service.isConnected());
    }

    public void testStartStopService_deviceUnavailable() throws Exception {
        mBtAdapterFactory.getAdapter().putDevice(TestGeppaService.TARGET_DEVICE, "1", false, false);

        Intent intent = new Intent();
        IBinder iBinder = bindService(intent);
        IPassiveGeppaService service = IPassiveGeppaService.Stub.asInterface(iBinder);
        {
            Thread.sleep(200);
            assertFalse(service.isConnected());
        }
    }

    public void testStartStopService_withDevice() throws Exception {
        mBtAdapterFactory.getAdapter().putDevice(TestGeppaService.TARGET_DEVICE, "1", true, false);

        Intent intent = new Intent();
        IBinder iBinder = bindService(intent);
        IPassiveGeppaService service = IPassiveGeppaService.Stub.asInterface(iBinder);
        for (int i = 0; i < 10; i++) {
            if (service.isConnected()) {
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(service.isConnected());
    }

    public void testDisconnect() throws Exception {
        TestBtDevice device = mBtAdapterFactory.getAdapter().putDevice(
                TestGeppaService.TARGET_DEVICE, "1", true, false);

        Intent intent = new Intent();
        IBinder iBinder = bindService(intent);
        IPassiveGeppaService service = IPassiveGeppaService.Stub.asInterface(iBinder);
        { // Waiting connection is established.
            while (!service.isConnected()) {
                Thread.sleep(100);
            }
        }
        GeppaServiceListenerImpl listener;
        int listenerSeq;
        {
            listener = new GeppaServiceListenerImpl();
            listenerSeq = service.registerGeppaServiceListener(listener);
        }
        {
            device.getSocket().setAvailable(false);
            device.getSocket().close();
            Thread.sleep(100);
        }
        {
            assertEquals(4, listener.status.size());
            assertEquals(ConnectionState.CLOSED, listener.status.get(0));
            assertEquals(ConnectionState.INITIAL, listener.status.get(1));
            assertEquals(ConnectionState.CONNECTING, listener.status.get(2));
            assertEquals(ConnectionState.CLOSED, listener.status.get(3));
        }
        service.unregisterGeppaServiceListener(listenerSeq);
    }

    public void testSendPacket() throws Exception {
        mBtAdapterFactory.getAdapter().putDevice(TestGeppaService.TARGET_DEVICE, "1", true, true);

        Intent intent = new Intent();
        IBinder iBinder = bindService(intent);
        IPassiveGeppaService service = IPassiveGeppaService.Stub.asInterface(iBinder);
        GeppaServiceListenerImpl listener = new GeppaServiceListenerImpl();
        int listenerSeq = service.registerGeppaServiceListener(listener);

        {
            MyPacket packet = new MyPacket((byte)1, (byte)2, new byte[] {
                    3, 4
            });
            PacketWrapper packetWrapper = new PacketWrapper(packet);
            service.sendPacket(packetWrapper);
        }
        { // Waiting for packet is arrive.
            for (int i = 0; i < 10; i++) {
                if (listener.packets.size() == 1) {
                    break;
                }
                Thread.sleep(100);
            }
        }
        {
            assertEquals(1, listener.packets.size());
            MyPacket packet = listener.packets.get(0);
            assertEquals(1, packet.getOpCode());
            assertEquals(2, packet.getDataLen());
            assertEquals(3, packet.getData()[0]);
            assertEquals(4, packet.getData()[1]);
        }

        service.unregisterGeppaServiceListener(listenerSeq);
    }
}
