
package net.cattaka.libgeppa.net;

import jp.ksksue.driver.serial.FTDriver;
import net.cattaka.libgeppa.IRawSocket;
import net.cattaka.libgeppa.data.BaudRate;
import net.cattaka.libgeppa.thread.ConnectionThread.IRawSocketPrepareTask;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

public class FtDriverSocketPrepareTask implements IRawSocketPrepareTask {
    private UsbManager mUsbManager;

    private UsbDevice mUsbDevice;

    private BaudRate mBaudRate;

    public FtDriverSocketPrepareTask(UsbManager usbManager, UsbDevice usbDevice, BaudRate baudRate) {
        super();
        mUsbManager = usbManager;
        mUsbDevice = usbDevice;
        mBaudRate = baudRate;
    }

    @Override
    public IRawSocket prepareRawSocket() {
        FTDriver mFtDriver = new FTDriver(mUsbManager);
        mFtDriver.setRxTimeout(1000); // Is this effective?.
        if (mFtDriver.begin(mBaudRate.getBaud(), mUsbDevice)) {
            return new FtDriverSocket(mFtDriver);
        } else {
            return null;
        }
    }

}
