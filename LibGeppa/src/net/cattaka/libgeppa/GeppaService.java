
package net.cattaka.libgeppa;

import java.io.IOException;
import java.util.Set;

import net.cattaka.libgeppa.bluetooth.BluetoothAdapterFactory;
import net.cattaka.libgeppa.bluetooth.IBluetoothAdapter;
import net.cattaka.libgeppa.bluetooth.IBluetoothDevice;
import net.cattaka.libgeppa.bluetooth.IBluetoothSocket;
import net.cattaka.libgeppa.data.ConnectionCode;
import net.cattaka.libgeppa.data.ConnectionState;
import net.cattaka.libgeppa.data.IPacket;
import net.cattaka.libgeppa.data.IPacketFactory;
import net.cattaka.libgeppa.data.PacketWrapper;
import net.cattaka.libgeppa.socket.BtRawSocket;
import net.cattaka.libgeppa.thread.ConnectionThread;
import net.cattaka.libgeppa.thread.ConnectionThread.IRawSocketPrepareTask;
import net.cattaka.libgeppa.thread.IConnectionThreadListener;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.SparseArray;
import android.util.SparseIntArray;

public abstract class GeppaService<T extends IPacket> extends Service {

    private final BroadcastReceiver mBtConnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                // When Bluetooth adapter turned on, it starts ConnectionThread.
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF);
                if (state == BluetoothAdapter.STATE_ON) {
                    startConnectionThread();
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                // When Bluetooth device connected, it starts Connection thread.
                BluetoothDevice device = (BluetoothDevice)intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mTargetDeviceName.equals(device.getName())) {
                    startConnectionThread();
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                // none
            }
        }
    };

    private IConnectionThreadListener<T> mConnectionThreadListener = new IConnectionThreadListener<T>() {
        @Override
        public void onReceive(T packet) {
            onReceivePacket(packet);
        };

        public void onConnectionStateChanged(ConnectionState state, ConnectionCode code) {
            if (state == ConnectionState.CLOSED) {
                mConnectionThread = null;
            }

            me.onConnectionStateChanged(state);
            if (code == ConnectionCode.DISCONNECTED) {
                // If other devices are alive, It will restart.
                // otherwise ConnectionThread stop.
                startConnectionThread();
            }
        }
    };

    private IGeppaService.Stub mBinder = new IGeppaService.Stub() {
        @SuppressWarnings("unchecked")
        @Override
        public boolean sendPacket(PacketWrapper packet) throws RemoteException {
            if (mConnectionThread != null) {
                mConnectionThread.sendPacket((T)packet.getPacket());
            }
            return false;
        }

        @Override
        public boolean isConnected() throws RemoteException {
            return (mLastConnectionState == ConnectionState.CONNECTED);
        }

        @Override
        public ConnectionState getConnectionState() throws RemoteException {
            return mLastConnectionState;
        }

        @Override
        public int registerGeppaServiceListener(IGeppaServiceListener listner)
                throws RemoteException {
            mListenerMap.put(mListenerSeq, listner);
            return mListenerSeq++;
        }

        @Override
        public void unregisterGeppaServiceListener(int seq) throws RemoteException {
            mListenerMap.remove(seq);
        }
    };

    private IRawSocketPrepareTask mBluetoothPrepareTask = new IRawSocketPrepareTask() {
        @Override
        public IRawSocket prepareRawSocket() {
            Set<IBluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
            BtRawSocket rawSocket = null;
            for (IBluetoothDevice device : devices) {
                if (!mTargetDeviceName.equals(device.getName())) {
                    continue;
                }
                try {
                    IBluetoothSocket socket = device
                            .createRfcommSocketToServiceRecord(Constants.SPP_UUID);
                    socket.connect();
                    rawSocket = new BtRawSocket(socket, Constants.OUTPUT_BUF_SIZE,
                            device.getAddress());
                    break;
                } catch (IOException e) {
                    // ignore
                    // Log.d(Constants.TAG_DEBUG, e.getMessage(),
                    // e);
                }
            }
            return rawSocket;
        }
    };

    private GeppaService<T> me = this;

    private IBluetoothAdapter mBluetoothAdapter;

    private String mTargetDeviceName;

    private ConnectionThread<T> mConnectionThread;

    private ConnectionState mLastConnectionState = ConnectionState.UNKNOWN;

    private IPacketFactory<T> mPacketFactory;

    private int mListenerSeq;

    private SparseArray<IGeppaServiceListener> mListenerMap;

    private boolean destroyed;

    public GeppaService(String targetDeviceName, IPacketFactory<T> packetFactory)
            throws NullPointerException {
        if (targetDeviceName == null || packetFactory == null) {
            throw new NullPointerException();
        }
        mBluetoothAdapter = BluetoothAdapterFactory.getDefaultAdapter();
        mTargetDeviceName = targetDeviceName;
        mPacketFactory = packetFactory;
        mListenerSeq = 1;
        mListenerMap = new SparseArray<IGeppaServiceListener>();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtConnReceiver, filter);
    }

    @Override
    public IBinder onBind(Intent paramIntent) {
        startConnectionThread();
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        startConnectionThread();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBtConnReceiver);

        if (mConnectionThread != null) {
            try {
                mConnectionThread.stopThread();
            } catch (InterruptedException e) {
                // Do not interrupt to main thread.
                throw new RuntimeException("Do not interrupt to main thread!");
            }
            mConnectionThread = null;
        }
        destroyed = true;
    }

    private void startConnectionThread() {
        if (!destroyed && mConnectionThread == null) {
            if (mBluetoothAdapter.isEnabled()) {
                mConnectionThread = new ConnectionThread<T>(mBluetoothPrepareTask, mPacketFactory,
                        mConnectionThreadListener, true);
                try {
                    me.onConnectionStateChanged(ConnectionState.INITIAL);
                    mConnectionThread.startThread();
                } catch (InterruptedException e) {
                    // Do not interrupt to main thread.
                    throw new RuntimeException("Do not interrupt to main thread!");
                }
            } else {
                // BluetoothAdapter is disabled.
            }
        }
    }

    protected void onReceivePacket(T packet) {
        SparseIntArray errors = new SparseIntArray();
        PacketWrapper packetWrapper = new PacketWrapper(packet);
        for (int i = 0; i < mListenerMap.size(); i++) {
            int key = mListenerMap.keyAt(i);
            IGeppaServiceListener listner = mListenerMap.valueAt(i);
            try {
                listner.onReceivePacket(packetWrapper);
            } catch (RemoteException e) {
                errors.put(errors.size(), key);
            }
        }
        for (int i = 0; i < errors.size(); i++) {
            int key = errors.keyAt(i);
            mListenerMap.remove(key);
        }
    };

    protected void onConnectionStateChanged(ConnectionState state) {
        mLastConnectionState = state;

        SparseIntArray errors = new SparseIntArray();
        for (int i = 0; i < mListenerMap.size(); i++) {
            int key = mListenerMap.keyAt(i);
            IGeppaServiceListener listner = mListenerMap.valueAt(i);
            try {
                listner.onConnectionStateChanged(state);
            } catch (RemoteException e) {
                errors.put(errors.size(), key);
            }
        }
        for (int i = 0; i < errors.size(); i++) {
            int key = errors.keyAt(i);
            mListenerMap.remove(key);
        }
    }

}
