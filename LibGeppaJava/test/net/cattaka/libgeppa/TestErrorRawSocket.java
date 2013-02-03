
package net.cattaka.libgeppa;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Random;

public class TestErrorRawSocket implements IRawSocket {
    static class PipedOutputStreamEx extends PipedOutputStream {
        private Random mRandom = new Random(1234567891234567891L);

        private boolean mEnableError = false;

        private int mErrorRate;

        private PipedOutputStreamEx(int errorRate) {
            super();
            mErrorRate = errorRate;
        }

        private PipedOutputStreamEx(PipedInputStream snk, int errorRate) throws IOException {
            super(snk);
            mErrorRate = errorRate;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) {
                write(b[i]);
            }
        }

        @Override
        public void write(int b) throws IOException {
            if (mEnableError && mRandom.nextInt(mErrorRate) == 0) {
                super.write(0xFF ^ b);
            } else {
                super.write(b);
            }
        }

        @Override
        public void write(byte[] bs) throws IOException {
            write(bs, 0, bs.length);
        }

        public void setEnableError(boolean enableError) {
            mEnableError = enableError;
        }

    }

    private PipedInputStream mInputStream;

    private PipedOutputStreamEx mOutputStream;

    private PipedInputStream mOuterInputStream;

    private PipedOutputStreamEx mOuterOutputStream;

    public TestErrorRawSocket(int errorRate) throws IOException {
        mInputStream = new PipedInputStream();
        mOutputStream = new PipedOutputStreamEx(errorRate);
        mOuterInputStream = new PipedInputStream(mOutputStream);
        mOuterOutputStream = new PipedOutputStreamEx(mInputStream, errorRate);
    }

    private TestErrorRawSocket(PipedInputStream inputStream, PipedOutputStreamEx outputStream,
            PipedInputStream outerInputStream, PipedOutputStreamEx outerOutputStream) {
        super();
        mInputStream = inputStream;
        mOutputStream = outputStream;
        mOuterInputStream = outerInputStream;
        mOuterOutputStream = outerOutputStream;
    }

    public TestErrorRawSocket createOther() {
        return new TestErrorRawSocket(mOuterInputStream, mOuterOutputStream, mInputStream,
                mOutputStream);
    }

    public void setEnableError(boolean enableError) {
        mOutputStream.setEnableError(enableError);
        mOuterOutputStream.setEnableError(enableError);
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

    @Override
    public boolean isConnected() {
        return true;
    }

    public PipedInputStream getOuterInputStream() {
        return mOuterInputStream;
    }

    public PipedOutputStream getOuterOutputStream() {
        return mOuterOutputStream;
    }
}
