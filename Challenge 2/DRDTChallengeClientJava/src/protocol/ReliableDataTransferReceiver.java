package protocol;

import client.Utils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static protocol.ReliableDataTransferProtocol.HEADER_SIZE;
import static protocol.ReliableDataTransferProtocol.HEADER_IDS;
import static protocol.ReliableDataTransferProtocol.SIZE_PACKET_HEADER;

public class ReliableDataTransferReceiver {
    
    private ReliableDataTransferProtocol master;
    
    private List<Integer[]> bufferedPackets;
    private List<Integer> fileContents;
    private int amountOfPackets = -1;
    private int receivedUpTo = -1;
    private boolean[] received;
    
    public ReliableDataTransferReceiver(ReliableDataTransferProtocol master) {
        this.master = master;
    }
    
    public void receive() {
        System.out.println("Receiving...");
        
        this.bufferedPackets = new ArrayList<>();
        this.fileContents = new ArrayList<>();
        
        this.checkForPackets();
        while (this.receivedUpTo < this.amountOfPackets || this.amountOfPackets < 0) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            this.checkForPackets();
        }
        
        // write to the output file
        Utils.setFileContents(this.fileContents.toArray(new Integer[0]), this.master.getFileID());
    }
    
    private void checkForPackets() {
        List<Integer[]> packets = new ArrayList<>();
        for (Integer[] packet =
                this.master.getNetworkLayer().receivePacket(); packet != null; packet =
                this.master.getNetworkLayer().receivePacket()) {
            packets.add(packet);
        }
        
        for (Integer[] packet: packets) {
            if (packet[0] == SIZE_PACKET_HEADER) {
                readSizePacket(packet);
            } else if (this.amountOfPackets < 0) {
                this.bufferedPackets.add(packet);
            } else {
                readPacket(packet);
            }
        }
    }
    
    private void readSizePacket(Integer[] packet) {
        sendAcknowledgement(SIZE_PACKET_HEADER);
        
        if (this.amountOfPackets >= 0) {
            return;
        }
        
        byte[] bytes = new byte[packet.length - HEADER_SIZE];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) packet[i + HEADER_SIZE].intValue();
        }
        
        this.amountOfPackets = new BigInteger(bytes).intValue();
        this.received = new boolean[this.amountOfPackets];
        
        for (Integer[] other: this.bufferedPackets) {
            readPacket(other);
        }
    }
    
    private void readPacket(Integer[] packet) {
        int header = packet[0];
        // tell the user
        System.out.println("Received packet, length=" + packet.length + "  header byte=" + header);
        
        // append the packet's data part (excluding the header) to the fileContents array,
        // first making it larger
        int pos = calculatePos(header);
        
        for (int i = HEADER_SIZE; i < packet.length; i++) {
            this.fileContents.add(packet[i]);
        }
        this.received[pos] = true;
        
        updateReceivedUpTo(pos);
        sendAcknowledgement(header);
    }
    
    private void sendAcknowledgement(int header) {
        this.master.getNetworkLayer().sendPacket(new Integer[] {header});
    }
    
    private int calculatePos(int sequenceNumber) {
        if (sequenceNumber < (this.receivedUpTo % HEADER_IDS)) {
            return (this.receivedUpTo - (this.receivedUpTo % HEADER_IDS)) + HEADER_IDS
                    + sequenceNumber;
        } else {
            return (this.receivedUpTo - (this.receivedUpTo % HEADER_IDS)) + sequenceNumber;
        }
    }
    
    private void updateReceivedUpTo(int pos) {
        if (pos == this.receivedUpTo + 1) {
            this.receivedUpTo++;
            while (this.receivedUpTo < this.amountOfPackets - 1
                    && this.received[this.receivedUpTo + 1]) {
                this.receivedUpTo++;
            }
        }
    }
    
}
