
package net.cattaka.libgeppa.adapter;

import net.cattaka.libgeppa.data.BaudRate;
import net.cattaka.libgeppa.data.DeviceInfo;
import net.cattaka.libgeppa.data.IPacket;
import net.cattaka.libgeppa.data.IPacketFactory;
import net.cattaka.libgeppa.net.FtDriverSocketPrepareTask;
import net.cattaka.libgeppa.thread.ConnectionThread.IRawSocketPrepareTask;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

public class LocalDeviceAdapter<T extends IPacket> extends AbsConnectionAdapter<T> {
    private UsbManager mUsbManager;

    private UsbDevice mUsbDevice;

    private BaudRate mBaudRate;

    public LocalDeviceAdapter(IDeviceAdapterListener<T> listener, IPacketFactory<T> packetFactory,
            boolean useMainLooperForListener, UsbManager usbManager, UsbDevice usbDevice,
            BaudRate baudRate) {
        super(listener, packetFactory, useMainLooperForListener);
        mUsbManager = usbManager;
        mUsbDevice = usbDevice;
        mBaudRate = baudRate;
    }

    @Override
    protected IRawSocketPrepareTask createRawSocketPrepareTask() {
        return new FtDriverSocketPrepareTask(mUsbManager, mUsbDevice, mBaudRate);
    }

    public UsbDevice getUsbDevice() {
        return mUsbDevice;
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return DeviceInfo.createUsb(mUsbDevice.getDeviceName(), false);
    }
}
