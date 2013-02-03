
package net.cattaka.libgeppa.test.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import net.cattaka.libgeppa.bluetooth.IBluetoothSocket;

public class TestBtSocket implements IBluetoothSocket {
    private PipedInputStream mInputStream;

    private PipedOutputStream mOutputStream;

    private PipedInputStream mOuterInputStream;

    private PipedOutputStream mOuterOutputStream;

    private boolean mAvailable;

    public TestBtSocket(boolean available, boolean loopSocket) throws IOException {
        mAvailable = available;
        if (loopSocket) {
            mOutputStream = new PipedOutputStream();
            mInputStream = new PipedInputStream(mOutputStream) {
                @Override
                public void close() throws IOException {
                    super.close();
                    mOutputStream.close();
                }
            };
        } else {
            mOuterInputStream = new PipedInputStream();
            mOuterOutputStream = new PipedOutputStream();
            mInputStream = new PipedInputStream(mOuterOutputStream) {
                @Override
                public synchronized void close() throws IOException {
                    super.close();
                    mOuterOutputStream.close();
                }
            };
            mOutputStream = new PipedOutputStream(mOuterInputStream) {
                @Override
                public void close() throws IOException {
                    super.close();
                    mOuterInputStream.close();
                }
            };
        }
    }

    private TestBtSocket(PipedInputStream inputStream, PipedOutputStream outputStream,
            PipedInputStream outerInputStream, PipedOutputStream outerOutputStream) {
        super();
        mInputStream = inputStream;
        mOutputStream = outputStream;
        mOuterInputStream = outerInputStream;
        mOuterOutputStream = outerOutputStream;
    }

    public TestBtSocket createOther() {
        return new TestBtSocket(mOuterInputStream, mOuterOutputStream, mInputStream, mOutputStream);
    }

    @Override
    public void connect() throws IOException {
        if (!mAvailable) {
            throw new IOException("Unavailable.");
        }
    }

    @Override
    public void close() throws IOException {
        mInputStream.close();
        mOutputStream.close();
    }

    @Override
    public InputStream getInputStream() {
        return mInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return mOutputStream;
    }

    public PipedInputStream getOuterInputStream() {
        return mOuterInputStream;
    }

    public PipedOutputStream getOuterOutputStream() {
        return mOuterOutputStream;
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    public void setAvailable(boolean available) {
        mAvailable = available;
    }

}
