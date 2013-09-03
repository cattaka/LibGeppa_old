
package net.cattaka.libgeppa;

import java.io.IOException;
import java.util.Set;

import net.cattaka.libgeppa.bluetooth.BluetoothAdapterFactory;
import net.cattaka.libgeppa.bluetooth.IBluetoothAdapter;
import net.cattaka.libgeppa.bluetooth.IBluetoothDevice;
import net.cattaka.libgeppa.bluetooth.IBluetoothSocket;
import net.cattaka.libgeppa.data.IPacket;
import net.cattaka.libgeppa.data.IPacketFactory;
import net.cattaka.libgeppa.socket.BtRawSocket;
import net.cattaka.libgeppa.thread.ConnectionThread;
import net.cattaka.libgeppa.thread.ConnectionThread.IRawSocketPrepareTask;
import net.cattaka.libgeppa.thread.IConnectionThreadListener;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public abstract class GeppaService<T extends IPacket> extends AbsGeppaService<T> {
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

    private String mTargetDeviceName;

    private IBluetoothAdapter mBluetoothAdapter;

    // private IBluetoothAdapter mBluetoothAdapter;
    public GeppaService(String targetDeviceName, IPacketFactory<T> packetFactory)
            throws NullPointerException {
        super(packetFactory);
        if (targetDeviceName == null || packetFactory == null) {
            throw new NullPointerException();
        }
        mTargetDeviceName = targetDeviceName;
        mBluetoothAdapter = BluetoothAdapterFactory.getDefaultAdapter();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtConnReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBtConnReceiver);
    }

    @Override
    protected ConnectionThread<T> createConnectionThread(
            IConnectionThreadListener<T> connectionThreadListener) {
        if (mBluetoothAdapter.isEnabled()) {
            return new ConnectionThread<T>(mBluetoothPrepareTask, getPacketFactory(),
                    connectionThreadListener, true);
        } else {
            return null;
        }
    }
}
