
package net.cattaka.libgeppa.test.data;

import net.cattaka.libgeppa.data.IPacket;

public class MyPacket implements IPacket {
    private byte opCode;

    private int dataLen;

    private byte[] data;

    public MyPacket(byte opCode, int dataLen, byte[] data) {
        super();
        this.opCode = opCode;
        this.dataLen = dataLen;
        this.data = data;
    }

    public byte getOpCode() {
        return opCode;
    }

    public void setOpCode(byte opCode) {
        this.opCode = opCode;
    }

    public int getDataLen() {
        return dataLen;
    }

    public void setDataLen(int dataLen) {
        this.dataLen = dataLen;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
