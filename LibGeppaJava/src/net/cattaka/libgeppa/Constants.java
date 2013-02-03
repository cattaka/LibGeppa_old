
package net.cattaka.libgeppa;

public class Constants {
    public static final byte STX = 0x02;

    public static final byte ETX = 0x03;

    public static final byte FRAMETYPE_ACK = 0x01;

    public static final byte FRAMETYPE_DATA = 0x02;

    public static final byte FRAMETYPE_REQ_SEND = 0x03;

    public static final byte ACKFLAG_RECV = 1 << 0;

    public static final byte ACKFLAG_SEND = 1 << 1;

    public static final int SIZE_FRAME_EXTRA = 7;
}
