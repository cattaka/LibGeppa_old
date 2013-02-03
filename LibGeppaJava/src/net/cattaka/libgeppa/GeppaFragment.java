
package net.cattaka.libgeppa;

import java.util.EnumSet;

public class GeppaFragment {
    private EnumSet<FragmentFlag> mFrags = EnumSet.noneOf(FragmentFlag.class);

    private byte mSeq;

    private int mLength;

    private byte[] mData;

    public GeppaFragment(int size) {
        mData = new byte[size];
    }

    public void clear() {
        mFrags.clear();
        mSeq = 0;
        mLength = 0;
    }

    public void writeData(byte[] src) {
        System.arraycopy(src, 0, mData, 0, src.length);
        mLength = src.length;
    }

    public void writeData(byte[] src, int offset, int length) {
        System.arraycopy(src, offset, mData, 0, length);
        mLength = length;
    }

    public EnumSet<FragmentFlag> getFrags() {
        return mFrags;
    }

    public byte getSeq() {
        return mSeq;
    }

    public void setSeq(byte seq) {
        mSeq = seq;
    }

    public int getLength() {
        return mLength;
    }

    public void setLength(int length) {
        mLength = length;
    }

    public byte[] getData() {
        return mData;
    }
}
