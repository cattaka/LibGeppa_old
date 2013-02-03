
package net.cattaka.libgeppa;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class TestRawSocket implements IRawSocket {
    private PipedInputStream mInputStream;

    private PipedOutputStream mOutputStream;

    private PipedInputStream mOuterInputStream;

    private PipedOutputStream mOuterOutputStream;

    public TestRawSocket() throws IOException {
        mInputStream = new PipedInputStream();
        mOutputStream = new PipedOutputStream();
        mOuterInputStream = new PipedInputStream(mOutputStream);
        mOuterOutputStream = new PipedOutputStream(mInputStream);
    }

    private TestRawSocket(PipedInputStream inputStream, PipedOutputStream outputStream,
            PipedInputStream outerInputStream, PipedOutputStream outerOutputStream) {
        super();
        mInputStream = inputStream;
        mOutputStream = outputStream;
        mOuterInputStream = outerInputStream;
        mOuterOutputStream = outerOutputStream;
    }

    public TestRawSocket createOther() {
        return new TestRawSocket(mOuterInputStream, mOuterOutputStream, mInputStream, mOutputStream);
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
