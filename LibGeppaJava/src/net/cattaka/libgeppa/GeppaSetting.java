
package net.cattaka.libgeppa;

public class GeppaSetting {
    /** Default maximum size of dataframe(bytes) */
    public static final int DEFAULT_MAX_FRAME_SIZE = 0x20;

    /** Default maximum number of send fragments */
    public static final int DEFAULT_MAX_SEND_FRAGMENTS_NUM = 0x08;

    /** Default maximum number of recv fragments */
    public static final int DEFAULT_MAX_RECV_FRAGMENTS_NUM = 0x08;

    public int maxFrameSize = DEFAULT_MAX_FRAME_SIZE;

    public int sendFragmentsNum = DEFAULT_MAX_SEND_FRAGMENTS_NUM;

    public int recvFragmentsNum = DEFAULT_MAX_RECV_FRAGMENTS_NUM;

    public int getMaxFragmentSize() {
        return maxFrameSize - Constants.SIZE_FRAME_EXTRA;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public void setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    public int getSendFragmentsNum() {
        return sendFragmentsNum;
    }

    public void setSendFragmentsNum(int sendFragmentsNum) {
        this.sendFragmentsNum = sendFragmentsNum;
    }

    public int getRecvFragmentsNum() {
        return recvFragmentsNum;
    }

    public void setRecvFragmentsNum(int recvFragmentsNum) {
        this.recvFragmentsNum = recvFragmentsNum;
    }
}
