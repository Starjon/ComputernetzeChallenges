package ns.tcphack;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.stream.Collectors;

class MyTcpHandler extends TcpHandler {
    
    private static final int[] SOURCE_ADRESS = new int[] {0x20, 0x01, 0x4c, 0xf0, 0x02, 0x2f, 0x06,
            0x30, 0x60, 0xe3, 0x00, 0xa3, 0xd7, 0xa8, 0xfc, 0xe2};
    private static final int[] DESTINATION_ADRESS = new int[] {0x20, 0x01, 0x06, 0x7c, 0x25, 0x64,
            0xa1, 0x70, 0x02, 0x04, 0x23, 0xff, 0xfe, 0xde, 0x4b, 0x2c};
    
    private static final short SOURCE_PORT = 25575;
    private static final short DESTINATION_PORT = 7711;
    
    private static int MAX_SIZE = 1 << 16;
    
    private int nextOwnSeqNr;
    private int lastRecievedForeignSeqNr;
    
    private ByteBuffer received;
    
    public static void main(String[] args) {
        new MyTcpHandler();
    }
    
    public static Charset charset() {
        return Charset.forName("ISO-8859-1");
    }
    
    public static int[] convertToBytes(short val) {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.putShort(val);
        byte[] bytes = buffer.array();
        int[] result = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = 0xFF & bytes[i];
        }
        return result;
    }
    
    public static int[] convertToBytes(int val) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(val);
        byte[] bytes = buffer.array();
        int[] result = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = 0xFF & bytes[i];
        }
        return result;
    }
    
    public static int convertFromBytes(int[] bytes, int startIndex) {
        System.out.println(bytes[startIndex] + " " + bytes[startIndex + 1] + " "
                + bytes[startIndex + 2] + " " + bytes[startIndex + 3]);
        return bytes[startIndex] << 24 | bytes[startIndex + 1] << 16 | bytes[startIndex + 2] << 8
                | bytes[startIndex + 3];
    }
    
    public MyTcpHandler() {
        super();
        
        this.received = ByteBuffer.allocate(MAX_SIZE);
        
        boolean done = false;
        
        int[] synPkt = getIpPacket(getTcpSynPacket());
        System.out.println(synPkt.length);
        System.out.println(Arrays.stream(synPkt).mapToObj(i -> Integer.toHexString(i))
                .map(s -> s.length() == 0 ? "00" : s.length() == 1 ? ("0" + s) : s)
                .collect(Collectors.joining()));
        sendData(synPkt); // send the packet
        
        int[] synAckPkt;
        while ((synAckPkt = receiveData(500)).length == 0) {
            // wait
        }
        System.out.println("Received synAckPkt: " + Arrays.toString(synAckPkt));
        this.lastRecievedForeignSeqNr = convertFromBytes(synAckPkt, 44);
        System.out.println(this.lastRecievedForeignSeqNr);
        
        int getRequestPkt[] = getIpPacket(getTcpGetRequest(416096));
        sendData(getRequestPkt);
        
        while (!done) {
            // check for reception of a packet, but wait at most 500 ms:
            int[] rxpkt = receiveData(500);
            if (rxpkt.length == 0) {
                // nothing has been received yet
                System.out.println("Nothing...");
                continue;
            }
            
            // something has been received
            // int len = rxpkt.length;
            
            // print the received bytes:
            // int i;
            // System.out.print("Received " + len + " bytes: ");
            // for (i = 0; i < len; i++) {
            // System.out.print(rxpkt[i] + " ");
            // }
            // System.out.println("");
            
            if (rxpkt.length <= 60) {
                continue;
            }
            
            String tcpMsg = "tcpMsg: ";
            ByteBuffer buffer = ByteBuffer.allocate(rxpkt.length - 60);
            for (int j = 60; j < rxpkt.length; j++) {
                buffer.put((byte) rxpkt[j]);
            }
            tcpMsg += new String(buffer.array(), charset());
            System.out.println(tcpMsg);
            
            sendData(getIpPacket(getTcpAckPacket(rxpkt)));
        }
    }
    
    private int[] getIpPacket(int[] tcpPkt) {
        // array of bytes in which we're going to build our packet:
        int[] txpkt = new int[tcpPkt.length + 40]; // 40 bytes long for now, may need to expand this
        // later
        
        int index = 0;
        txpkt[index++] = 0x60; // first byte of the IPv6 header contains version number in upper
                               // nibble
        // Fill rest of first header row.
        txpkt[index++] = 0;
        txpkt[index++] = 0;
        txpkt[index++] = 0;
        
        // Payload length
        txpkt[index++] = 0;
        txpkt[index++] = tcpPkt.length;
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
        
        for (int i = 0; i < tcpPkt.length; i++) {
            txpkt[index++] = tcpPkt[i];
        }
        
        return txpkt;
    }
    
    private int[] getTcpBasePacket(int size) {
        int[] tcpPkt = new int[size];
        int index = 0;
        
        int[] srcPort = convertToBytes(SOURCE_PORT);
        for (int i = 0; i < srcPort.length; i++, index++) {
            tcpPkt[index] = srcPort[i];
        }
        
        int[] destPort = convertToBytes(DESTINATION_PORT);
        for (int i = 0; i < destPort.length; i++, index++) {
            tcpPkt[index] = destPort[i];
        }
        
        index += 8;
        
        // Header length && unused (teilweise)
        tcpPkt[index++] = 0x50;
        // Rest von unused und 6 flag bits
        tcpPkt[index++] = 0b00000000;
        // Advertised recevie window
        tcpPkt[index++] = 0xA0;
        tcpPkt[index++] = 0xFF;
        // Checksum
        tcpPkt[index++] = 0;
        tcpPkt[index++] = 0;
        // Urgent pointer
        tcpPkt[index++] = 0;
        tcpPkt[index++] = 0;
        
        return tcpPkt;
    }
    
    private void activateSyn(int[] tcpPkt) {
        tcpPkt[13] |= 0b00000010;
    }
    
    private void activateAck(int[] tcpPkt) {
        tcpPkt[13] |= 0b00010000;
    }
    
    private int[] getTcpSynPacket() {
        int[] tcpPkt = getTcpBasePacket(20);
        int index = 4;
        
        int[] seqNr = convertToBytes(this.nextOwnSeqNr++);
        for (int i = 0; i < seqNr.length; i++) {
            tcpPkt[index++] = seqNr[i];
        }
        
        int[] ackNr = convertToBytes(0);
        for (int i = 0; i < ackNr.length; i++) {
            tcpPkt[index++] = ackNr[i];
        }
        
        activateSyn(tcpPkt);
        
        return tcpPkt;
    }
    
    private int[] getTcpAckPacket(int[] rxpkt) {
        int[] tcpPkt = getTcpBasePacket(20);
        int index = 4;
        
        int[] seqNr = convertToBytes(this.nextOwnSeqNr);
        for (int i = 0; i < seqNr.length; i++) {
            tcpPkt[index++] = seqNr[i];
        }
        
        this.lastRecievedForeignSeqNr = convertFromBytes(rxpkt, 44);
        int[] ackNr = convertToBytes(this.lastRecievedForeignSeqNr + rxpkt.length - 60);
        for (int i = 0; i < ackNr.length; i++) {
            tcpPkt[index++] = ackNr[i];
        }
        
        System.out.println("sending ACK packet: " + this.lastRecievedForeignSeqNr + " + "
                + (rxpkt.length - 60));
        
        activateAck(tcpPkt);
        
        return tcpPkt;
    }
    
    private int[] getTcpGetRequest(int matNr) {
        String request = "GET /" + matNr + " HTTP/1.0\r\n\r\n"; // \nHost:
        // [2001:67c:2564:a170:204:23ff:fede:4b2c]:7710
        byte[] requestBytes = charset().encode(request).array();
        
        int[] tcpPkt = getTcpBasePacket(20 + requestBytes.length);
        int index = 4;
        
        int[] seqNr = convertToBytes(this.nextOwnSeqNr);
        this.nextOwnSeqNr += requestBytes.length;
        for (int i = 0; i < seqNr.length; i++) {
            tcpPkt[index++] = seqNr[i];
        }
        
        int[] ackNr = convertToBytes(this.lastRecievedForeignSeqNr + 1);
        for (int i = 0; i < ackNr.length; i++) {
            tcpPkt[index++] = ackNr[i];
        }
        
        index = 20;
        for (int i = 0; i < requestBytes.length; i++) {
            tcpPkt[index++] = 0xFF & requestBytes[i];
        }
        
        activateAck(tcpPkt);
        
        return tcpPkt;
    }
}
