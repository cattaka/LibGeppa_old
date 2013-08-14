
package net.cattaka.libgeppa;

import net.cattaka.libgeppa.data.ConnectionCode;
import net.cattaka.libgeppa.data.ConnectionState;
import net.cattaka.libgeppa.data.IPacket;
import net.cattaka.libgeppa.data.IPacketFactory;
import net.cattaka.libgeppa.data.PacketWrapper;
import net.cattaka.libgeppa.thread.ConnectionThread;
import net.cattaka.libgeppa.thread.IConnectionThreadListener;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.SparseArray;
import android.util.SparseIntArray;

public abstract class AbsGeppaService<T extends IPacket> extends Service {
    private IConnectionThreadListener<T> mConnectionThreadListener = new IConnectionThreadListener<T>() {
        @Override
        public void onReceive(T packet) {
            onReceivePacket(packet);
        };

        public void onConnectionStateChanged(ConnectionState state, ConnectionCode code) {
            if (state == ConnectionState.CLOSED) {
                stopConnectionThread();
                if (mBindCount == 0) {
                    stopSelf();
                }
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
        @Override
        public boolean sendPacket(PacketWrapper packet) throws RemoteException {
            return me.sendPacket(packet);
        }

        @Override
        public boolean isConnected() throws RemoteException {
            return me.isConnected();
        }

        @Override
        public ConnectionState getConnectionState() throws RemoteException {
            return me.getConnectionState();
        }

        @Override
        public int registerGeppaServiceListener(IGeppaServiceListener listner)
                throws RemoteException {
            return me.registerGeppaServiceListener(listner);
        }

        @Override
        public void unregisterGeppaServiceListener(int seq) throws RemoteException {
            me.unregisterGeppaServiceListener(seq);
        }
    };

    private AbsGeppaService<T> me = this;

    //
    // private IBluetoothAdapter mBluetoothAdapter;

    private ConnectionThread<T> mConnectionThread;

    private ConnectionState mLastConnectionState = ConnectionState.UNKNOWN;

    private IPacketFactory<T> mPacketFactory;

    private int mListenerSeq;

    private SparseArray<IGeppaServiceListener> mListenerMap;

    private boolean destroyed;

    private int mBindCount = 0;

    public AbsGeppaService(IPacketFactory<T> packetFactory) throws NullPointerException {
        if (packetFactory == null) {
            throw new NullPointerException();
        }
        // mBluetoothAdapter = BluetoothAdapterFactory.getDefaultAdapter();
        mPacketFactory = packetFactory;
        mListenerSeq = 1;
        mListenerMap = new SparseArray<IGeppaServiceListener>();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
    }

    @Override
    public IBinder onBind(Intent paramIntent) {
        mBindCount++;
        startConnectionThread();
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        mBindCount++;
        startConnectionThread();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mBindCount--;
        if (mBindCount == 0 && mConnectionThread == null) {
            stopSelf();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // unregisterReceiver(mBtConnReceiver);

        stopConnectionThread();
        destroyed = true;
    }

    abstract protected ConnectionThread<T> createConnectionThread();

    protected void startConnectionThread() {
        if (!destroyed && mConnectionThread == null) {
            mConnectionThread = createConnectionThread();
            if (mConnectionThread != null) {
                try {
                    me.onConnectionStateChanged(ConnectionState.INITIAL);
                    mConnectionThread.startThread(mConnectionThreadListener);
                } catch (InterruptedException e) {
                    // Do not interrupt to main thread.
                    throw new RuntimeException("Do not interrupt to main thread!");
                }
            } else {
                // BluetoothAdapter is disabled.
            }
        }
    }

    protected void stopConnectionThread() {
        if (mConnectionThread != null) {
            try {
                mConnectionThread.stopThread();
            } catch (InterruptedException e) {
                // Do not interrupt to main thread.
                throw new RuntimeException("Do not interrupt to main thread!");
            }
            mConnectionThread = null;
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

    public IPacketFactory<T> getPacketFactory() {
        return mPacketFactory;
    }

    /** for binder */
    @SuppressWarnings("unchecked")
    protected boolean sendPacket(PacketWrapper packet) {
        if (mConnectionThread != null) {
            return mConnectionThread.sendPacket((T)packet.getPacket());
        }
        return false;
    }

    /** for binder */
    protected boolean isConnected() {
        return (mLastConnectionState == ConnectionState.CONNECTED);
    }

    /** for binder */
    protected ConnectionState getConnectionState() {
        return mLastConnectionState;
    }

    /** for binder */
    protected int registerGeppaServiceListener(IGeppaServiceListener listner) {
        mListenerMap.put(mListenerSeq, listner);
        return mListenerSeq++;
    }

    /** for binder */
    protected void unregisterGeppaServiceListener(int seq) {
        mListenerMap.remove(seq);
    }
}
