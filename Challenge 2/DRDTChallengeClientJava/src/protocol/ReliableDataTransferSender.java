package protocol;

import client.Utils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static protocol.ReliableDataTransferProtocol.HEADER_SIZE;
import static protocol.ReliableDataTransferProtocol.DATA_SIZE;
import static protocol.ReliableDataTransferProtocol.HEADER_IDS;
import static protocol.ReliableDataTransferProtocol.SIZE_PACKET_HEADER;

public class ReliableDataTransferSender {
    
    private static final int TIMEOUT_MS = 1550; // 1500
    volatile int numTimeouts;
    
    private ReliableDataTransferProtocol master;
    
    private Integer[] fileContents;
    private List<Integer[]> packets;
    
    private Object lock = new Object();
    
    // Might be accessed from another thread, thus volatile.
    private volatile boolean sizePacketAcknowledged;
    private boolean transmissionFinished;
    
    private boolean[] acknowledgements;
    private int[] sequenceIdToPacketId = new int[ReliableDataTransferProtocol.HEADER_IDS];
    private int lastAcknowledged = -1;
    private int nextPacketId;
    
    public ReliableDataTransferSender(ReliableDataTransferProtocol master) {
        this.master = master;
    }
    
    public void send() {
        System.out.println("Sending...");
        
        this.fileContents = Utils.getFileContents(this.master.getFileID());
        this.packets = splitIntoPackets();
        System.out.println("Split file of size " + fileContents.length + " bytes into "
                + packets.size() + " packets.");
        sendSizePacket();
        this.acknowledgements = new boolean[this.packets.size()];
        
        boolean justChecked = false;
        while (this.nextPacketId < this.packets.size()) {
            while (this.nextPacketId < this.packets.size() && Utils.modulo(
                    this.nextPacketId % HEADER_IDS - this.lastAcknowledged % HEADER_IDS,
                    HEADER_IDS) < HEADER_IDS / 8) {
                justChecked = false;
                sendPacketForFirstTime(this.packets.get(this.nextPacketId));
            }
            if (justChecked) {
                justChecked = false;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                justChecked = true;
                this.checkForAcknowledgements();
            }
        }
        
        while (!this.transmissionFinished) {
            this.checkForAcknowledgements();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    private List<Integer[]> splitIntoPackets() {
        List<Integer[]> result = new ArrayList<>(this.fileContents.length / DATA_SIZE);
        
        int i = 0;
        while (i < this.fileContents.length) {
            int dataLength = Math.min(DATA_SIZE, this.fileContents.length - i);
            Integer[] packet = new Integer[HEADER_SIZE + dataLength];
            System.arraycopy(this.fileContents, i, packet, HEADER_SIZE, dataLength);
            result.add(packet);
            i += dataLength;
        }
        
        return result;
    }
    
    private void sendSizePacket() {
        int size = this.fileContents.length;
        byte[] asBytes = BigInteger.valueOf(size).toByteArray();
        if (asBytes.length > DATA_SIZE) {
            throw new IllegalArgumentException("File is too big to transfer.");
        }
        
        Integer[] sizePacket = new Integer[asBytes.length + HEADER_SIZE];
        sizePacket[0] = SIZE_PACKET_HEADER;
        for (int i = 0; i < asBytes.length; i++) {
            sizePacket[i + 1] = 0xFF & asBytes[i];
        }
        
        sendPacket(-1, sizePacket);
    }
    
    private void sendPacketForFirstTime(Integer[] packet) {
        this.acknowledgements[this.nextPacketId] = false;
        setHeader(packet);
        sendPacket(this.nextPacketId, packet);
        this.nextPacketId++;
    }
    
    private void sendPacket(int packetId, Integer[] packet) {
        registerTimeout(packetId, packet);
        this.master.getNetworkLayer().sendPacket(packet);
        System.out.println("Sent one packet with id=" + packetId + ", header=" + packet[0]
                + " and length=" + packet.length);
    }
    
    private void setHeader(Integer[] packet) {
        packet[0] = this.nextPacketId % HEADER_IDS;
        this.sequenceIdToPacketId[this.nextPacketId % HEADER_IDS] = this.nextPacketId;
    }
    
    
    private void registerTimeout(int packetId, Integer[] packet) {
        client.Utils.Timeout.SetTimeout(TIMEOUT_MS, this.master, new Object[] {packetId, packet});
    }
    
    private void checkForAcknowledgements() {
        List<Integer[]> packets = new ArrayList<>();
        for (Integer[] packet = this.master.getNetworkLayer().receivePacket(); packet != null; packet =
                this.master.getNetworkLayer().receivePacket()) {
            packets.add(packet);
        }
        
        synchronized (this.lock) {
            for (Integer[] packet: packets) {
                int packetHeader = packet[0];
                if (packetHeader == SIZE_PACKET_HEADER) {
                    this.sizePacketAcknowledged = true;
                    continue;
                }
                
                int packetId = this.sequenceIdToPacketId[packetHeader];
                this.acknowledgements[packetId] = true;
                
                System.out.println(
                        "Acknowledged packet with id=" + packetId + " and header=" + packetHeader);
                
                while (packetId - 1 == this.lastAcknowledged
                        && packetId < this.acknowledgements.length
                        && this.acknowledgements[packetId]) {
                    this.lastAcknowledged++;
                    packetId++;
                    System.out.println("Moved lastAcknowledged to " + lastAcknowledged);
                }
                
                if (this.lastAcknowledged + 1 == this.acknowledgements.length) {
                    this.transmissionFinished = true;
                }
            }
        }
    }
    
    public void TimeoutElapsed(Object tag) {
        Object[] data = (Object[]) tag;
        Integer packetId = (Integer) data[0];
        Integer[] packet = (Integer[]) data[1];
        
        if (packet[0] == SIZE_PACKET_HEADER) {
            if (this.sizePacketAcknowledged) {
                return;
            }
        } else {
            synchronized (this.lock) {
                if (this.acknowledgements[packetId]) {
                    return;
                }
            }
        }
        
        numTimeouts++;
        System.out.println(
                "Received timeout for packet with id=" + packetId + " and header=" + packet[0]);
        System.out.println("TIMEOUTS: " + numTimeouts);
        sendPacket(packetId, packet);
    }
}
