
package net.cattaka.libgeppa.test.bluetooth;

import net.cattaka.libgeppa.bluetooth.BluetoothAdapterFactory;

public class TestBtAdapterFactory extends BluetoothAdapterFactory {
    private TestBtAdapter mAdapeter;

    public TestBtAdapterFactory() {
        mAdapeter = new TestBtAdapter();
    }

    @Override
    public TestBtAdapter getAdapter() {
        return mAdapeter;
    }
}
