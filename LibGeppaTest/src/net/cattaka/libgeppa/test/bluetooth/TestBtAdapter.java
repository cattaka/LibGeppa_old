
package net.cattaka.libgeppa.test.bluetooth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.cattaka.libgeppa.bluetooth.IBluetoothAdapter;
import net.cattaka.libgeppa.bluetooth.IBluetoothDevice;

public class TestBtAdapter implements IBluetoothAdapter {
    private boolean mEnabled = true;

    private HashMap<String, TestBtDevice> mDeviceMap;

    public TestBtAdapter() {
        mDeviceMap = new HashMap<String, TestBtDevice>();
    }

    public TestBtDevice putDevice(String name, String address, boolean available, boolean loopSocket) {
        TestBtDevice device = new TestBtDevice(name, address, available, loopSocket);
        mDeviceMap.put(address, device);
        return device;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public Set<IBluetoothDevice> getBondedDevices() {
        Set<IBluetoothDevice> devices = new HashSet<IBluetoothDevice>();
        devices.addAll(mDeviceMap.values());
        return devices;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public TestBtDevice getDevice(String address) {
        return mDeviceMap.get(address);
    }
}
