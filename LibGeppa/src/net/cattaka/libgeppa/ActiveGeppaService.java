
package net.cattaka.libgeppa;

import java.util.Map;

import net.cattaka.libgeppa.adapter.DummyDeviceAdapter;
import net.cattaka.libgeppa.adapter.IDeviceAdapter;
import net.cattaka.libgeppa.adapter.IDeviceAdapterListener;
import net.cattaka.libgeppa.adapter.LocalDeviceAdapter;
import net.cattaka.libgeppa.adapter.RemoteDeviceAdapter;
import net.cattaka.libgeppa.data.BaudRate;
import net.cattaka.libgeppa.data.DeviceEventCode;
import net.cattaka.libgeppa.data.DeviceInfo;
import net.cattaka.libgeppa.data.DeviceInfo.DeviceType;
import net.cattaka.libgeppa.data.DeviceState;
import net.cattaka.libgeppa.data.IPacket;
import net.cattaka.libgeppa.data.IPacketFactory;
import net.cattaka.libgeppa.data.PacketWrapper;
import net.cattaka.libgeppa.util.AidlUtil;
import net.cattaka.libgeppa.util.AidlUtil.CallFunction;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

public abstract class ActiveGeppaService<T extends IPacket> extends Service {
    private String ACTION_USB_PERMISSION;

    protected static final String EXTRA_USB_DEVICE_KEY = "usbDevicekey";

    private static final int EVENT_REGISTER_CONNECTION_LISTENER = 1;

    private static final int EVENT_UNREGISTER_CONNECTION_LISTENER = 2;

    private static final int EVENT_CONNECT = 3;

    private static final int EVENT_DISCONNECT = 4;

    private static final int EVENT_SEND_PACKET = 6;

    private static Handler sHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            Object objs[] = (Object[])msg.obj;
            ActiveGeppaService<?> target = (ActiveGeppaService<?>)objs[0];
            target.handleMessage(msg);
        };
    };

    private void handleMessage(android.os.Message msg) {
        Object objs[] = (Object[])msg.obj;
        IDeviceAdapter<T> mcThread = mDeviceAdapter;
        switch (msg.what) {
            case EVENT_REGISTER_CONNECTION_LISTENER: {
                IActiveGeppaServiceListener listener = (IActiveGeppaServiceListener)objs[2];
                try {
                    DeviceState state = (mcThread != null) ? mcThread.getDeviceState() : null;
                    DeviceInfo deviceInfo = pickDeviceInfo(mcThread);
                    listener.onDeviceStateChanged(state, DeviceEventCode.ON_REGISTER, deviceInfo);

                    mServiceListeners.append((Integer)objs[1], listener);
                } catch (RemoteException e) {
                    // Ignore
                    Log.w(Constants.TAG, e.getMessage(), e);
                }
                break;
            }
            case EVENT_UNREGISTER_CONNECTION_LISTENER: {
                mServiceListeners.remove((Integer)objs[1]);
                break;
            }
            case EVENT_CONNECT: {
                connect((DeviceInfo)objs[1]);
                break;
            }
            case EVENT_DISCONNECT: {
                disconnect();
                break;
            }

            default: {
                if (mcThread != null) {
                    switch (msg.what) {
                        case EVENT_SEND_PACKET: {
                            @SuppressWarnings("unchecked")
                            T packet = (T)objs[1];
                            mcThread.sendPacket(packet);
                            break;
                        }
                    }
                }
                break;
            }
        }
    };

    private ActiveGeppaService<T> me = this;

    private IPacketFactory<T> mPacketFactory;

    private IDeviceAdapter<T> mDeviceAdapter;

    private int mNextConnectionListenerSeq = 1;

    private SparseArray<IActiveGeppaServiceListener> mServiceListeners;

    private BaudRate mBaudRate = BaudRate.BAUD115200;

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                String itemKey = intent.getStringExtra(EXTRA_USB_DEVICE_KEY);
                if (itemKey != null) {
                    DeviceInfo deviceInfo = DeviceInfo.createUsb(itemKey, false);
                    connect(deviceInfo);
                }
            }
        }
    };

    private IBinder mBinder = new IActiveGeppaService.Stub() {
        @Override
        public int registerServiceListener(IActiveGeppaServiceListener listener)
                throws RemoteException {
            int seq = mNextConnectionListenerSeq++;
            sHandler.obtainMessage(EVENT_REGISTER_CONNECTION_LISTENER, new Object[] {
                    me, seq, listener
            }).sendToTarget();
            return seq;
        }

        @Override
        public void unregisterServiceListener(int seq) throws RemoteException {
            sHandler.obtainMessage(EVENT_UNREGISTER_CONNECTION_LISTENER, new Object[] {
                    me, seq
            }).sendToTarget();
        }

        public void connect(DeviceInfo deviceInfo) throws RemoteException {
            sHandler.obtainMessage(EVENT_CONNECT, new Object[] {
                    me, deviceInfo
            }).sendToTarget();
        };

        public void disconnect() throws RemoteException {
            sHandler.obtainMessage(EVENT_DISCONNECT, new Object[] {
                me
            }).sendToTarget();
        };

        @Override
        public DeviceInfo getCurrentDeviceInfo() throws RemoteException {
            return me.getCurrentDeviceInfo();
        }

        @Override
        public boolean sendPacket(PacketWrapper packet) throws RemoteException {
            if (mDeviceAdapter != null) {
                sHandler.obtainMessage(EVENT_SEND_PACKET, new Object[] {
                        me, packet.getPacket()
                }).sendToTarget();
                return true;
            } else {
                return false;
            }
        }
    };

    private IDeviceAdapterListener<T> mDeviceAdapterListener = new IDeviceAdapterListener<T>() {
        public void onReceivePacket(final T packet) {
            AidlUtil.callMethods(mServiceListeners,
                    new CallFunction<IActiveGeppaServiceListener>() {
                        public boolean run(IActiveGeppaServiceListener item) throws RemoteException {
                            item.onReceivePacket(new PacketWrapper(packet));
                            return true;
                        };
                    });
        };

        @Override
        public void onDeviceStateChanged(final DeviceState state, final DeviceEventCode code,
                final DeviceInfo deviceInfo) {
            AidlUtil.callMethods(mServiceListeners,
                    new CallFunction<IActiveGeppaServiceListener>() {
                        public boolean run(IActiveGeppaServiceListener item) throws RemoteException {

                            item.onDeviceStateChanged(state, code, getCurrentDeviceInfo());
                            return true;
                        };
                    });
            handleConnectedNotification(state == DeviceState.CONNECTED, getCurrentDeviceInfo());
        }
    };

    public ActiveGeppaService(IPacketFactory<T> packetFactory) {
        mPacketFactory = packetFactory;
        mServiceListeners = new SparseArray<IActiveGeppaServiceListener>();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (mDeviceAdapter == null) {
            stopSelf();
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ACTION_USB_PERMISSION = getPackageName() + ".action_permission";

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        unregisterReceiver(mUsbReceiver);
    }

    private void connect(DeviceInfo deviceInfo) {
        disconnect();

        if (deviceInfo.getDeviceType() == DeviceType.TCP) {
            mDeviceAdapter = new RemoteDeviceAdapter<T>(mDeviceAdapterListener, mPacketFactory,
                    true, deviceInfo.getTcpHostName(), deviceInfo.getTcpPort());
            try {
                mDeviceAdapter.startAdapter();
            } catch (InterruptedException e) {
                // Impossible
                throw new RuntimeException(e);
            }
        } else if (deviceInfo.getDeviceType() == DeviceType.USB) {
            UsbManager usbManager = (UsbManager)getSystemService(USB_SERVICE);
            Map<String, UsbDevice> devices = usbManager.getDeviceList();
            UsbDevice usbDevice = devices.get(deviceInfo.getUsbDeviceKey());
            if (usbDevice != null && usbManager.hasPermission(usbDevice)) {
                // If service already has permission, it start thread.
                mDeviceAdapter = new LocalDeviceAdapter<T>(mDeviceAdapterListener, mPacketFactory,
                        true, usbManager, usbDevice, mBaudRate);
                try {
                    mDeviceAdapter.startAdapter();
                } catch (InterruptedException e) {
                    // Impossible
                    throw new RuntimeException(e);
                }
            } else if (usbDevice != null) {
                // Request
                Intent intent = new Intent(ACTION_USB_PERMISSION);
                intent.putExtra(EXTRA_USB_DEVICE_KEY, deviceInfo.getUsbDeviceKey());
                PendingIntent pIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
                usbManager.requestPermission(usbDevice, pIntent);
            } else {
                AidlUtil.callMethods(mServiceListeners,
                        new CallFunction<IActiveGeppaServiceListener>() {
                            public boolean run(IActiveGeppaServiceListener item)
                                    throws RemoteException {
                                item.onDeviceStateChanged(DeviceState.CLOSED,
                                        DeviceEventCode.NO_DEVICE, null);
                                return true;
                            };
                        });
            }
        } else {
            mDeviceAdapter = new DummyDeviceAdapter<T>(mDeviceAdapterListener, sHandler);
            try {
                mDeviceAdapter.startAdapter();
            } catch (InterruptedException e) {
                // Impossible
                throw new RuntimeException(e);
            }
        }
    }

    private void disconnect() {
        if (mDeviceAdapter != null) {
            try {
                mDeviceAdapter.stopAdapter();
                mDeviceAdapter = null;
            } catch (InterruptedException e) {
                // Impossible
                throw new RuntimeException(e);
            }
        }
    }

    public BaudRate getBaudRate() {
        return mBaudRate;
    }

    public void setBaudRate(BaudRate baudRate) {
        mBaudRate = baudRate;
    }

    abstract protected void handleConnectedNotification(boolean connected, DeviceInfo deviceInfo);

    private DeviceInfo getCurrentDeviceInfo() {
        return pickDeviceInfo(mDeviceAdapter);
    }

    private DeviceInfo pickDeviceInfo(IDeviceAdapter<T> adapter) {
        if (adapter != null) {
            return adapter.getDeviceInfo();
        } else {
            return null;
        }
    }
}
