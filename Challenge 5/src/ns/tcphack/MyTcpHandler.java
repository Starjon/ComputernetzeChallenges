package ns.tcphack;

import java.nio.ByteBuffer;

class MyTcpHandler extends TcpHandler {
    
    private static final int[] SOURCE_ADRESS = new int[] {0x20, 0x01, 0x4c, 0xf0, 0x02, 0x2f, 0x06,
            0x30, 0x60, 0xe3, 0x00, 0xa3, 0xd7, 0xa8, 0xfc, 0xe2};
    private static final int[] DESTINATION_ADRESS = new int[] {0x20, 0x01, 0x06, 0x7c, 0x25, 0x64,
            0xa1, 0x70, 0x02, 0x04, 0x23, 0xff, 0xfe, 0xde, 0x4b, 0x2c};
    
    private static final short SOURCE_PORT = 25566;
    private static final short DESTINATION_PORT = 7710;
    private static final short TCP_SIZE = 16;
    
    private static int MAX_SIZE = 1 << 16;
    
    private int lastReceivedSeqNr;
    
    private ByteBuffer received;
    
    public static void main(String[] args) {
        new MyTcpHandler();
    }
    
    public static byte[] convertToBytes(short s) {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.putShort(s);
        return buffer.array();
    }
    
    public static byte[] convertToBytes(int i) {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.putInt(i);
        return buffer.array();
    }
    
    public MyTcpHandler() {
        super();
        
        this.received = ByteBuffer.allocate(MAX_SIZE);
        
        boolean done = false;
        
        // array of bytes in which we're going to build our packet:
        int[] txpkt = new int[TCP_SIZE + 40]; // 40 bytes long for now, may need to expand this
                                              // later
        
        int index = 0;
        txpkt[index++] = 0x60; // first byte of the IPv6 header contains version number in upper
                               // nibble
        // Fill rest of first header row.
        txpkt[index++] = 0;
        txpkt[index++] = 0;
        txpkt[index++] = 0;
        
        // Payload length
        txpkt[index++] = TCP_SIZE;
        // Protocol
        txpkt[index++] = 0xfd; // special TCP
        // Hop limit
        txpkt[index++] = 0x40;
        
        // Source adress
        for (int i = 0; i < SOURCE_ADRESS.length; i++) {
            txpkt[index++] = SOURCE_ADRESS[i];
        }
        // Destination adress
        for (int i = 0; i < DESTINATION_ADRESS.length; i++) {
            txpkt[index++] = DESTINATION_ADRESS[i];
        }
        
        byte[] tcpPkt = getTcpPacket();
        if (tcpPkt.length != TCP_SIZE) {
            throw new IndexOutOfBoundsException(
                    "tcpPkt length " + tcpPkt.length + ", expected " + TCP_SIZE);
        }
        for (int i = 0; i < tcpPkt.length; i++) {
            txpkt[index++] = tcpPkt[i];
        }
        
        sendData(txpkt); // send the packet
        
        while (!done) {
            // check for reception of a packet, but wait at most 500 ms:
            int[] rxpkt = receiveData(500);
            if (rxpkt.length == 0) {
                // nothing has been received yet
                System.out.println("Nothing...");
                continue;
            }
            
            // something has been received
            int len = rxpkt.length;
            
            // print the received bytes:
            int i;
            System.out.print("Received " + len + " bytes: ");
            for (i = 0; i < len; i++) {
                System.out.print(rxpkt[i] + " ");
            }
            System.out.println("");
        }
    }
    
    private byte[] getTcpPacket() {
        byte[] tcpPkt = new byte[TCP_SIZE];
        int index = 0;
        
        byte[] srcPort = convertToBytes(SOURCE_PORT);
        for (int i = 0; i < srcPort.length; i++, index++) {
            tcpPkt[index] = srcPort[i];
        }
        
        byte[] destPort = convertToBytes(DESTINATION_PORT);
        for (int i = 0; i < destPort.length; i++, index++) {
            tcpPkt[index] = destPort[i];
        }
        
        return tcpPkt;
    }
}
