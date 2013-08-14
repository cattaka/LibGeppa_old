
package net.cattaka.libgeppa;

import net.cattaka.libgeppa.data.IPacket;
import net.cattaka.libgeppa.data.IPacketFactory;
import net.cattaka.libgeppa.socket.AdkRawSocket;
import net.cattaka.libgeppa.thread.ConnectionThread;
import net.cattaka.libgeppa.thread.ConnectionThread.IRawSocketPrepareTask;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public abstract class AdkGeppaService<T extends IPacket> extends AbsGeppaService<T> {
    protected String ACTION_USB_PERMISSION;

    protected static final String EXTRA_USB_DEVICE_ID = "usbDeviceId";

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(Constants.TAG, intent.toString());
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {

                startConnectionThread();
            } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory)intent
                        .getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                // FIXME
                startConnectionThread();
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory)intent
                        .getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                stopConnectionThread();
            }
        }
    };

    private class AdkPrepareTask implements IRawSocketPrepareTask {
        private UsbAccessory mAccessory;

        public AdkPrepareTask(UsbAccessory accessory) {
            super();
            mAccessory = accessory;
        }

        @Override
        public IRawSocket prepareRawSocket() {
            UsbManager usbManager = (UsbManager)getSystemService(USB_SERVICE);

            ParcelFileDescriptor pfd = usbManager.openAccessory(mAccessory);
            return new AdkRawSocket(pfd, pfd.toString());
        }
    };

    // private IBluetoothAdapter mBluetoothAdapter;
    public AdkGeppaService(IPacketFactory<T> packetFactory) throws NullPointerException {
        super(packetFactory);
        if (packetFactory == null) {
            throw new NullPointerException();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ACTION_USB_PERMISSION = getPackageName() + ".action_permission";
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
    }

    protected ConnectionThread<T> createConnectionThread() {
        UsbManager usbManager = (UsbManager)getSystemService(USB_SERVICE);
        UsbAccessory[] accs = usbManager.getAccessoryList();
        UsbAccessory acc = (accs != null && accs.length > 0) ? accs[0] : null;

        if (acc != null && usbManager.hasPermission(acc)) {
            return new ConnectionThread<T>(new AdkPrepareTask(acc), getPacketFactory(), true);
        } else if (acc != null) {
            // Request
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            intent.putExtra(EXTRA_USB_DEVICE_ID, acc.getModel());
            PendingIntent pIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            usbManager.requestPermission(acc, pIntent);

            return null;
        } else {
            return null;
        }
    }
}
