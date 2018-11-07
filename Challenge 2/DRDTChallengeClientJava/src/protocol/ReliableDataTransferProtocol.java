package protocol;

import client.Utils;
import java.util.ArrayList;
import java.util.List;

public class ReliableDataTransferProtocol extends IRDTProtocol {
    
    private static final int HEADER_SIZE = 1; // number of header bytes in each packet
    private static final int DATA_SIZE = 128; // max. number of user data bytes in each packet
    
    private static final int HEADER_IDS = 256;

    private static final int TIMEOUT_MS = 1000;
    private static final Object SIZE_PACKET_TAG = new Object();

    private Integer[] fileContents;
    
    private Object lock = new Object();
    
    // Might be accessed from another thread, thus volatile.
    private volatile boolean sizePacketAcknowledged = false;
    
    private boolean[] acknowledgements;
    private boolean transmissionFinished;
    private int[] sequenceIdToPacketId = new int[HEADER_IDS];
    private int lastAcknowledged = -1;
    private int nextPacketId;
    
    private int sizeBytes = -1;
    private int amountOfPackets;
    private int receivedUpTo = -1;
    private boolean[] received;


    private List<Integer[]> splitIntoPackets(Integer[] data) {
        List<Integer[]> result = new ArrayList<>(data.length / DATA_SIZE);

        int i = 0;
        while (i < data.length) {
            int dataLength = Math.min(DATA_SIZE, data.length - i);
            Integer[] packet = new Integer[HEADER_SIZE + dataLength];
            System.arraycopy(data, i, packet, HEADER_SIZE, dataLength);
            result.add(packet);
            i += dataLength;
        }

        return result;
    }

    private void sendPacketForFirstTime(Integer[] packet) {
        this.acknowledgements[this.nextPacketId] = false;
        setHeader(packet);
        sendPacket(this.nextPacketId, packet);
        this.nextPacketId++;
    }
    
    private void sendPacket(int packetId, Integer[] packet) {
        registerTimeout(this.nextPacketId, packet);
        getNetworkLayer().sendPacket(packet);
        System.out.println("Sent one packet with id=" + packetId + " and header=" + packet[0]);
    }

    private void setHeader(Integer[] packet) {
        packet[0] = this.nextPacketId % HEADER_IDS;
        sequenceIdToPacketId[this.nextPacketId % HEADER_IDS] = this.nextPacketId;
    }
    

    private void registerTimeout(int packetId, Integer[] packet) {
        client.Utils.Timeout.SetTimeout(TIMEOUT_MS, this, new Object[] {packetId, packet});
    }
    
    private void checkForAcknowledgements() {
        List<Integer[]> packets = new ArrayList<>();
        for (Integer[] packet = getNetworkLayer().receivePacket(); packet != null; packet =
                getNetworkLayer().receivePacket()) {
            packets.add(packet);
        }
        
        synchronized (this.lock) {
            for (Integer[] packet : packets) {
                int packetHeaderId = packet[0];
                int packetId = sequenceIdToPacketId[packetHeaderId];
                this.acknowledgements[packetId] = true;
                
                while (packetId - 1 == this.lastAcknowledged && this.acknowledgements[packetId]
                        && lastAcknowledged + 1 < this.acknowledgements.length) {
                    lastAcknowledged++;
                    packetId++;
                }
                
                if (this.lastAcknowledged + 1 == this.acknowledgements.length) {
                    this.transmissionFinished = true; // notwendig?
                }
                
                // if (sequenceIdToPacketId[packetId] == packets.size() - 1) {
                // this.transmissionFinished = true; // sicher?
                // } else if ((this.lastAcknowledged + 1) % HEADER_IDS == packetId) {
                // this.lastAcknowledged++;
                // while (sequenceIdToPacketId[lastAcknowledged] < packets.size() - 1
                // && this.acknowledgements[sequenceIdToPacketId[lastAcknowledged] + 1]) {
                // this.lastAcknowledged++;
                // }
                // }
            }
        }
    }

    @Override
    public void sender() {
        System.out.println("Sending...");

        this.fileContents = Utils.getFileContents(getFileID());
        List<Integer[]> packets = splitIntoPackets(this.fileContents);
        this.acknowledgements = new boolean[packets.size()];

        Integer[] sizePacket = new Integer[1];
        sizePacket[0] = this.fileContents.length;
        getNetworkLayer().sendPacket(sizePacket);
        client.Utils.Timeout.SetTimeout(TIMEOUT_MS, this, SIZE_PACKET_TAG);
        while (getNetworkLayer().receivePacket() == null) {
            try {
                lock.wait(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        this.sizePacketAcknowledged = true;

        boolean justChecked = false;
        while (!transmissionFinished) {
            while (Utils.modulo(
                    this.nextPacketId % HEADER_IDS - this.lastAcknowledged % HEADER_IDS,
                    HEADER_IDS) < HEADER_IDS / 2) {
                justChecked = false;
                if (this.nextPacketId < packets.size()) {
                    sendPacketForFirstTime(packets.get(this.nextPacketId));
                }
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
    }
    


    private int calculatePos(int sequenceNumber){
        if (sequenceNumber < (this.receivedUpTo % HEADER_IDS)){
            return (this.receivedUpTo - (this.receivedUpTo % HEADER_IDS)) + HEADER_IDS + sequenceNumber;
        } else {
            return (this.receivedUpTo - (this.receivedUpTo % HEADER_IDS)) + sequenceNumber;
        }
    }

    private void updateReceivedUpTo(int pos){
        if (pos == this.receivedUpTo + 1){
            receivedUpTo++;
            while(receivedUpTo < amountOfPackets -1 && received[receivedUpTo+1]){
                receivedUpTo++;
            }
        }
    }
    
    @Override
    public void receiver() {
        System.out.println("Receiving...");
        
        while (this.sizeBytes < 0){
            Integer[] sizePacket = getNetworkLayer().receivePacket();
            if (sizePacket != null){
                this.sizeBytes = sizePacket[0];
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Integer[] fileContents = new Integer[sizeBytes];
        amountOfPackets = sizeBytes / DATA_SIZE + 1;
        this.received = new boolean[amountOfPackets];
        
        // loop until we are done receiving the file
        boolean stop = false;
        while (!stop) {
            
            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();
            
            // if we indeed received a packet
            if (packet != null) {
                
                // tell the user
                System.out.println(
                        "Received packet, length=" + packet.length + "  header byte=" + packet[0]);
                
                // append the packet's data part (excluding the header) to the fileContents array,
                // first making it larger
                int pos = calculatePos(packet[0]);
                int datalen = packet.length - HEADER_SIZE;

                System.arraycopy(packet, HEADER_SIZE, fileContents, pos, datalen);
                this.received[pos] = true;

                updateReceivedUpTo(pos);

                Integer[] ackPacket = {packet[0]};
                getNetworkLayer().sendPacket(ackPacket);

                if (receivedUpTo == amountOfPackets -1){
                    stop = true;
                }
                
            } else {
                // wait ~10ms (or however long the OS makes us wait) before trying again
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
        }
        
        // write to the output file
        Utils.setFileContents(fileContents, getFileID());
    }
    
    @Override
    public void TimeoutElapsed(Object tag) {
        if (tag == SIZE_PACKET_TAG && !this.sizePacketAcknowledged) {
            getNetworkLayer().sendPacket(new Integer[] {this.fileContents.length});
            return;
        }
        
        Object[] data = (Object[]) tag;
        Integer packetId = sequenceIdToPacketId[(Integer) data[0]];
        Integer[] packet = (Integer[]) data[1];
        
        synchronized (this.lock) {
            if (this.acknowledgements[packetId]) {
                return;
            }
        }
        
        sendPacket(packetId, packet);
    }
}
