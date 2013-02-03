
package net.cattaka.libgeppa.test.bluetooth;

import java.io.IOException;
import java.util.UUID;

import net.cattaka.libgeppa.bluetooth.IBluetoothDevice;
import net.cattaka.libgeppa.bluetooth.IBluetoothSocket;

public class TestBtDevice implements IBluetoothDevice {
    private String mName;

    private String mAddress;

    private TestBtSocket mSocket;

    public TestBtDevice(String name, String address, boolean available, boolean loopSocket) {
        super();
        this.mName = name;
        this.mAddress = address;

        try {
            mSocket = new TestBtSocket(available, loopSocket);
        } catch (IOException e) {
            // impossible
            throw new RuntimeException();
        }
    }

    @Override
    public String getAddress() {
        return mAddress;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public IBluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        return mSocket;
    }

    public TestBtSocket getSocket() {
        return mSocket;
    }
}
