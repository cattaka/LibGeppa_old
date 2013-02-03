package net.cattaka.libgeppa;

import net.cattaka.libgeppa.data.PacketWrapper;
import net.cattaka.libgeppa.IGeppaServiceListener;
import net.cattaka.libgeppa.data.ConnectionState;

interface IGeppaService {
    boolean isConnected();
    ConnectionState getConnectionState();
    boolean sendPacket(in PacketWrapper packet);
    int registerGeppaServiceListener(in IGeppaServiceListener listner);
    void unregisterGeppaServiceListener(in int seq);
}
