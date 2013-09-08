
package net.cattaka.libgeppa.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jp.ksksue.driver.serial.FTDriver;
import net.cattaka.libgeppa.IRawSocket;
import net.cattaka.libgeppa.exception.NotImplementedException;

public class FtDriverSocket implements IRawSocket {
    private class InputStreamEx extends InputStream {
        private byte[] buf = new byte[64];

        private int bufReaded = 0;

        private int bufIdx = 0;

        private boolean closed = false;

        @Override
        public int read() throws IOException {
            while (mFtDriver.isConnected() && !closed) {
                if (bufIdx < bufReaded) {
                    return (0xFF) & buf[bufIdx++];
                } else {
                    bufIdx = 0;
                    bufReaded = mFtDriver.read(buf);
                }
            }
            return -1;
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            throw new NotImplementedException();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            throw new NotImplementedException();
        }

        @Override
        public void close() throws IOException {
            super.close();
            closed = true;
        }
    }

    private class OutputStreamEx extends OutputStream {
        private int mBufIdx;

        private byte[] mBuf = new byte[1 << 12];

        @Override
        public void write(int oneByte) throws IOException {
            mBuf[mBufIdx++] = (byte)oneByte;
            if (mBufIdx == mBuf.length) {
                flush();
            }
        }

        @Override
        public void flush() throws IOException {
            super.flush();
            mFtDriver.write(mBuf, mBufIdx);
            mBufIdx = 0;
        }
    }

    private FTDriver mFtDriver;

    private InputStreamEx mInputStream;

    private OutputStreamEx mOutputStream;

    public FtDriverSocket(FTDriver ftDriver) {
        super();
        mFtDriver = ftDriver;
        mInputStream = new InputStreamEx();
        mOutputStream = new OutputStreamEx();
    }

    @Override
    public boolean setup() {
        return true;
    }

    @Override
    public String getLabel() {
        return "FTDriver Socket";
    }

    @Override
    public InputStream getInputStream() {
        return mInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return mOutputStream;
    }

    @Override
    public void close() throws IOException {
        mFtDriver.end();
    }

    @Override
    public boolean isConnected() {
        return mFtDriver.isConnected();
    }

}
