
package net.cattaka.libgeppa;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class GeppaSocket {
    enum ReadState {
        UNKNOWN, STX, DATA, ETX, CHECKSUM1, CHECKSUM2
    }

    private IRawSocket mRawSocket;

    private GeppaSetting mSetting;

    private GeppaFragment[] mSendFragments;

    private GeppaFragment[] mRecvFragments;

    /** Temporally buffer to write. */
    private byte[] mWriteBuf;

    /** Temporally buffer to write. */
    private byte[] mReadBuf;

    /** Temporally index for mReadBuf. */
    private int mReadBufTail;

    /** Temporally length for mReadBuf. */
    private int mReadDataLen;

    /** Temporally checksum for mReadBuf. */
    private int mReadBufChecksum;

    /** The state for mReadBuf */
    private ReadState mReadState = ReadState.UNKNOWN;

    /** The head seq of mSendFragments. */
    private int mSendSeqHead;

    /** The seq of mSendFragments. This separates sent and unsent. */
    private int mSendSeqMid;

    /** The tail Seq of mSendFragments. */
    private int mSendSeqTail;

    /** The head seq of mRecvFragments. */
    private int mRecvSeqHead;

    /** The seq of mSendFragments. This separates read and unread. */
    private int mRecvSeqMid;

    /** The tail Seq of mHeadFragments. */
    private int mRecvSeqTail;

    /** This is the receiced seq sent by remote client. */
    private int mRecvRecvSeq;

    /** This is the send seq sent by remote client. */
    private int mRecvSendSeq;

    private int mLastSendSeq = -1;

    private int mLastRecvSeq = -1;

    public GeppaSocket(IRawSocket rawSocket, GeppaSetting setting) {
        super();
        mRawSocket = rawSocket;
        mSetting = setting;

        mSendFragments = new GeppaFragment[setting.getSendFragmentsNum()];
        for (int i = 0; i < mSendFragments.length; i++) {
            mSendFragments[i] = new GeppaFragment(setting.getMaxFragmentSize());
        }
        mRecvFragments = new GeppaFragment[setting.getRecvFragmentsNum()];
        for (int i = 0; i < mRecvFragments.length; i++) {
            mRecvFragments[i] = new GeppaFragment(setting.getMaxFragmentSize());
        }
        mWriteBuf = new byte[setting.getMaxFrameSize()];
        mReadBuf = new byte[setting.getMaxFrameSize()];
        mRecvRecvSeq = 0xFF;
        mRecvSendSeq = 0xFF;
        mSendSeqHead = 0;
        mSendSeqMid = 0;
        mSendSeqTail = 0;
        mRecvSeqHead = 0;
        mRecvSeqMid = 0;
        mRecvSeqTail = 0;
    }

    public static GeppaSocket createGeppaSocket(IRawSocket rawSocket, GeppaSetting setting) {
        if (setting.getMaxFrameSize() <= 0 || 64 < setting.getMaxFrameSize()) {
            throw new IllegalArgumentException("MaxFrameSize must be [1,64]");
        }
        if (setting.getRecvFragmentsNum() <= 0 || 64 < setting.getRecvFragmentsNum()) {
            throw new IllegalArgumentException("RecvFragmentsNum must be [1,64]");
        }
        if (setting.getSendFragmentsNum() <= 0 || 64 < setting.getSendFragmentsNum()) {
            throw new IllegalArgumentException("SendFragmentsNum must be [1,64]");
        }

        return new GeppaSocket(rawSocket, setting);
    }

    boolean updateRecvSeq(int recvSeq) {
        int nextTail = (recvSeq + 1) & 0xFF;
        { // check that fragments are free
            int seq = mRecvSeqTail;
            while (true) {
                GeppaFragment fragment = mRecvFragments[seq % mRecvFragments.length];
                if (fragment.getFrags().contains(FragmentFlag.READY)) {
                    return false;
                }
                if (seq == recvSeq) {
                    break;
                }
                seq = (seq + 1) & 0xFF;
            }
        }
        while (mRecvSeqTail != nextTail) {
            int i = mRecvSeqTail % mRecvFragments.length;
            mRecvFragments[i].clear();
            mRecvFragments[i].setSeq((byte)mRecvSeqTail);
            mRecvFragments[i].getFrags().add(FragmentFlag.LOST);
            mRecvSeqTail = (mRecvSeqTail + 1) & 0xFF;
        }
        return true;
    }

    boolean updateSendSeq(int sendSeq) {
        int nextTail = (sendSeq + 1) & 0xFF;
        { // check that fragments are free
            int seq = mRecvSeqTail;
            while (true) {
                GeppaFragment fragment = mSendFragments[seq % mSendFragments.length];
                if (fragment.getFrags().contains(FragmentFlag.READY)) {
                    return false;
                }
                if (seq == sendSeq) {
                    break;
                }
                seq = (seq + 1) & 0xFF;
            }
        }
        while (mSendSeqTail != nextTail) {
            int i = mSendSeqTail % mSendFragments.length;
            mSendFragments[i].clear();
            mSendFragments[i].setSeq((byte)mSendSeqTail);
            // mSendFragments[i].getFrags().remove(FragmentFlag.LOST);
            mSendSeqTail = (mSendSeqTail + 1) & 0xFF;
        }
        return true;
    }

    boolean handleRecvBuf() throws IOException {
        boolean error = false;
        InputStream in = mRawSocket.getInputStream();
        while (in.available() > 0) {
            byte r = (byte)in.read();
            switch (mReadState) {
                case UNKNOWN: {
                    if (r == Constants.STX) {
                        mReadState = ReadState.STX;
                    } else {
                        error = true;
                    }
                    break;
                }
                case STX: {
                    mReadDataLen = 0xFF & r;
                    mReadBufTail = 0;
                    if (mReadDataLen < mSetting.getMaxFrameSize()) {
                        mReadState = ReadState.DATA;
                    } else {
                        mReadState = ReadState.UNKNOWN;
                        error = true;
                    }
                    break;
                }
                case DATA: {
                    mReadBuf[mReadBufTail++] = r;
                    if (mReadBufTail >= mReadDataLen) {
                        mReadState = ReadState.CHECKSUM1;
                    }
                    break;
                }
                case CHECKSUM1: {
                    mReadBufChecksum = (0xFF & r);
                    mReadState = ReadState.CHECKSUM2;
                    break;
                }
                case CHECKSUM2: {
                    mReadBufChecksum += ((0xFF & r) << 8);
                    mReadState = ReadState.ETX;
                    break;
                }
                case ETX: {
                    if (mReadBufChecksum == calcChecksum(mReadBuf, mReadDataLen)) {
                        handleFrameContent(mReadBuf, mReadBufTail);
                    } else {
                        // checkSum mismatch
                        error = true;
                    }
                    mReadState = ReadState.UNKNOWN;
                    break;
                }
            }
        }
        return error;
    }

    public void poll() throws IOException {
        poll(false);
    }

    public void poll(boolean forceSendAck) throws IOException {
        boolean sendAck = forceSendAck;
        OutputStream out = mRawSocket.getOutputStream();

        if (handleRecvBuf()) {
            sendAck = true;
        }
        handleSendBuf();

        { // send Ack if needed
            handleAck(out, sendAck);
        }
    }

    void handleSendBuf() throws IOException {
        OutputStream out = mRawSocket.getOutputStream();
        { // Sending fragments again if REQ_SEND was received.
            for (GeppaFragment fragment : mSendFragments) {
                if (fragment.getFrags().contains(FragmentFlag.LOST)) {
                    writeFrame(out, fragment);
                    fragment.getFrags().remove(FragmentFlag.LOST);
                }
            }
        }
        while (true) {
            GeppaFragment fragment = mSendFragments[mSendSeqMid % mSendFragments.length];
            if (fragment.getFrags().contains(FragmentFlag.READY)) {
                fragment.getFrags().remove(FragmentFlag.READY);
                fragment.getFrags().add(FragmentFlag.SENT);
                writeFrame(out, fragment);
                mSendSeqMid = (mSendSeqMid + 1) & 0xFF;
            } else {
                break;
            }
        }
    }

    void handleAck(OutputStream out, boolean forceSendAck) throws IOException {
        { // send ACK if needed
            boolean needSendAck = forceSendAck;
            if (!needSendAck) {
                int count = 0;
                { // get lastRecvSeq and count
                    int nextSeq = mRecvSeqHead;
                    while (nextSeq != mRecvSeqTail) {
                        GeppaFragment fragment = mRecvFragments[nextSeq % mRecvFragments.length];
                        if (fragment.getFrags().contains(FragmentFlag.NONACKED)) {
                            count++;
                            nextSeq = (nextSeq + 1) & 0xFF;
                        } else {
                            break;
                        }
                    }
                }
                if (count >= mSetting.getRecvFragmentsNum() / 2) {
                    needSendAck = true;
                }
            }

            if (needSendAck) {
                { // send ACKFLAG_SEND
                    {
                        int nextSeq = mSendSeqHead;
                        while (nextSeq != mSendSeqTail) {
                            GeppaFragment fragment = mSendFragments[nextSeq % mSendFragments.length];
                            if (fragment.getFrags().contains(FragmentFlag.SENT)) {
                                mLastSendSeq = nextSeq;
                                nextSeq = (nextSeq + 1) & 0xFF;
                            } else {
                                break;
                            }
                        }
                    }
                }
                { // send ACKFLAG_RECV
                    { // get lastRecvSeq and count
                        int nextSeq = mRecvSeqHead;
                        while (nextSeq != mRecvSeqTail) {
                            GeppaFragment fragment = mRecvFragments[nextSeq % mRecvFragments.length];
                            if (fragment.getFrags().contains(FragmentFlag.NONACKED)) {
                                mLastRecvSeq = nextSeq;
                                nextSeq = (nextSeq + 1) & 0xFF;
                            } else {
                                break;
                            }
                        }
                    }
                    if (mLastRecvSeq != -1) {
                        { // remove NOACKED flag
                            int nextSeq = mRecvSeqHead;
                            while (nextSeq != mRecvSeqTail) {
                                GeppaFragment fragment = mRecvFragments[nextSeq
                                        % mRecvFragments.length];
                                if (fragment.getFrags().contains(FragmentFlag.NONACKED)) {
                                    fragment.getFrags().remove(FragmentFlag.NONACKED);
                                    nextSeq = (nextSeq + 1) & 0xFF;
                                } else {
                                    break;
                                }
                            }
                        }
                        mWriteBuf[0] = Constants.FRAMETYPE_ACK;
                        mWriteBuf[1] = Constants.ACKFLAG_RECV;
                        mWriteBuf[2] = (byte)(0xFF & mLastRecvSeq);
                        mWriteBuf[3] = (byte)(0xFF);
                        writeFrame(out, mWriteBuf, 4);
                    }
                }
                if (mLastSendSeq != -1 && mLastSendSeq != -1) {
                    mWriteBuf[0] = Constants.FRAMETYPE_ACK;
                    mWriteBuf[1] = 0;
                    if (mLastSendSeq != -1) {
                        mWriteBuf[1] |= Constants.ACKFLAG_SEND;
                    }
                    if (mLastRecvSeq != -1) {
                        mWriteBuf[1] |= Constants.ACKFLAG_RECV;
                    }
                    mWriteBuf[2] = (byte)(0xFF & mLastRecvSeq);
                    mWriteBuf[3] = (byte)(0xFF & mLastSendSeq);
                    writeFrame(out, mWriteBuf, 4);
                }
            }
        }
        { // handle received ACK, and checking difference
            int recvRecvSeqTail = (mRecvRecvSeq + 1) & 0xFF;
            if (recvRecvSeqTail != mRecvSeqTail) {
                int dist = (recvRecvSeqTail > mRecvSeqTail) ? (recvRecvSeqTail - mRecvSeqTail)
                        : (recvRecvSeqTail + 0x100 - mRecvSeqTail);
                if (dist <= mSetting.getRecvFragmentsNum()) {
                    updateRecvSeq(mRecvRecvSeq);
                }
            }
        }
        { // clear unused fragments
            { // clear unused fragments from mRecvFragments
                while (mRecvSeqHead != mRecvSeqMid) {
                    GeppaFragment fragment = mRecvFragments[mRecvSeqHead % mRecvFragments.length];
                    if (!fragment.getFrags().contains(FragmentFlag.NONACKED)
                            && !fragment.getFrags().contains(FragmentFlag.NONREAD)) {
                        fragment.clear();
                        mRecvSeqHead = (mRecvSeqHead + 1) & 0xFF;
                    } else {
                        break;
                    }
                }
            }
            { // clear unused fragments from mSendFragments
                int recvSendSeqTail = (mRecvSendSeq + 1) & 0xFF;
                if ((mSendSeqHead <= mSendSeqTail && mSendSeqHead <= recvSendSeqTail && recvSendSeqTail <= mSendSeqTail)
                        || (mSendSeqHead > mSendSeqTail && (recvSendSeqTail <= mSendSeqHead || mSendSeqTail <= recvSendSeqTail))) {
                    while (mSendSeqHead != recvSendSeqTail) {
                        int i = mSendSeqHead % mSendFragments.length;
                        mSendFragments[i].clear();
                        mSendSeqHead = (mSendSeqHead + 1) & 0xFF;
                    }
                }
            }
        }
        { // send REQ_SEND if needed
            int len = 1;
            int seq = mRecvSeqHead;
            while (seq != mRecvSeqTail) {
                int idx = seq % mRecvFragments.length;
                if (mRecvFragments[idx].getFrags().contains(FragmentFlag.LOST)) {
                    mWriteBuf[len++] = mRecvFragments[idx].getSeq();
                }
                seq = 0xFF & (seq + 1);
            }
            if (len > 1) {
                mWriteBuf[0] = Constants.FRAMETYPE_REQ_SEND;
                writeFrame(out, mWriteBuf, len);
            }
        }
    }

    private void handleFrameContent(byte[] frameContent, int len) {
        if (len == 0) {
            return;
        }

        byte frameType = frameContent[0];
        switch (frameType) {
            case Constants.FRAMETYPE_ACK: {
                if (len == 4) {
                    byte flags = frameContent[1];
                    if ((flags & Constants.ACKFLAG_RECV) != 0) {
                        mRecvSendSeq = 0xFF & frameContent[2];
                    }
                    if ((flags & Constants.ACKFLAG_SEND) != 0) {
                        mRecvRecvSeq = 0xFF & frameContent[3];
                    }
                }
                break;
            }
            case Constants.FRAMETYPE_DATA: {
                if (len >= 2) {
                    int recvSeq = 0xFF & frameContent[1];
                    if (updateRecvSeq(recvSeq)) {
                        GeppaFragment nextFragment = mRecvFragments[recvSeq % mRecvFragments.length];
                        if (!nextFragment.getFrags().contains(FragmentFlag.READY)) {
                            System.arraycopy(frameContent, 2, nextFragment.getData(), 0, len - 2);
                            nextFragment.setLength(len - 2);
                            nextFragment.getFrags().add(FragmentFlag.READY);
                            nextFragment.getFrags().add(FragmentFlag.NONACKED);
                            nextFragment.getFrags().add(FragmentFlag.NONREAD);
                            nextFragment.getFrags().remove(FragmentFlag.LOST);
                        } else {
                            // TODO
                        }
                    } else {
                        // There are no freespace in mRecvBuf.
                    }
                }
                break;
            }
            case Constants.FRAMETYPE_REQ_SEND: {
                if (len >= 2) {
                    for (int i = 1; i < len; i++) {
                        int sendSeq = 0xFF & frameContent[i];
                        GeppaFragment fragment = mSendFragments[sendSeq % mSendFragments.length];
                        fragment.getFrags().add(FragmentFlag.LOST);
                    }
                }
                break;
            }
        }

    }

    public int read(byte[] out) {
        GeppaFragment fragment = mRecvFragments[mRecvSeqMid % mRecvFragments.length];
        if (fragment.getFrags().contains(FragmentFlag.NONREAD)) {
            int n = fragment.getLength();
            fragment.getFrags().remove(FragmentFlag.NONREAD);
            System.arraycopy(fragment.getData(), 0, out, 0, fragment.getLength());
            mRecvSeqMid = 0xFF & (mRecvSeqMid + 1);

            return n;
        } else {
            return 0;
        }
    }

    public boolean write(byte[] data) {
        return write(data, 0, data.length);
    }

    public boolean write(byte[] data, int offset, int length) {
        { // Check num of empty mSendFragments
            int n = 1 + (length - 1) / mSetting.getMaxFragmentSize();
            for (int i = 0; i < n; i++) {
                GeppaFragment fragment = mSendFragments[(mSendSeqTail + i) % mSendFragments.length];
                if (fragment.getFrags().contains(FragmentFlag.READY)
                        || fragment.getFrags().contains(FragmentFlag.SENT)) {
                    return false;
                }
            }
        }

        while (length > mSetting.getMaxFragmentSize()) {
            GeppaFragment fragment = mSendFragments[mSendSeqTail % mSendFragments.length];
            System.arraycopy(data, offset, fragment.getData(), 0, mSetting.getMaxFragmentSize());
            fragment.setLength(mSetting.getMaxFragmentSize());
            fragment.getFrags().add(FragmentFlag.READY);
            fragment.setSeq((byte)mSendSeqTail);
            mSendSeqTail = (mSendSeqTail + 1) & 0xFF;
            offset += mSetting.getMaxFragmentSize();
            length -= mSetting.getMaxFragmentSize();
        }

        {
            GeppaFragment fragment = mSendFragments[mSendSeqTail % mSendFragments.length];
            System.arraycopy(data, offset, fragment.getData(), 0, length);
            fragment.setLength(length);
            fragment.getFrags().add(FragmentFlag.READY);
            fragment.setSeq((byte)mSendSeqTail);
            mSendSeqTail = (mSendSeqTail + 1) & 0xFF;
        }

        return true;
    }

    static void writeFrame(OutputStream out, byte[] buf, int len) throws IOException {
        int cs = calcChecksum(buf, len);
        out.write(Constants.STX);
        out.write((byte)len);
        out.write(buf, 0, len);
        out.write((byte)(0xFF & cs));
        out.write((byte)(0xFF & (cs >> 8)));
        out.write(Constants.ETX);
    }

    static void writeFrame(OutputStream out, GeppaFragment fragment) throws IOException {
        int cs = calcChecksum(fragment);
        out.write(Constants.STX);
        out.write((byte)(fragment.getLength() + 2));
        out.write(Constants.FRAMETYPE_DATA);
        out.write(fragment.getSeq());
        out.write(fragment.getData(), 0, fragment.getLength());
        out.write((byte)(0xFF & cs));
        out.write((byte)(0xFF & (cs >> 8)));
        out.write(Constants.ETX);
    }

    private static int calcChecksum(byte[] buf, int len) {
        int cs = 0;
        for (int i = 0; i < len; i++) {
            if (i % 2 == 0) {
                cs += (0xFF & buf[i]);
            } else {
                cs += (0xFF & buf[i]) << 8;
            }
        }
        return 0xFFFF - (cs & 0xFFFF);
    }

    private static int calcChecksum(GeppaFragment fragment) {
        int cs = 0xFF & Constants.FRAMETYPE_DATA;
        cs += (0xFF & fragment.getSeq()) << 8;
        for (int i = 0; i < fragment.getLength(); i++) {
            if (i % 2 == 0) {
                cs += (0xFF & fragment.getData()[i]);
            } else {
                cs += (0xFF & fragment.getData()[i]) << 8;
            }
        }
        return 0xFFFF - (cs & 0xFFFF);
    }
}
