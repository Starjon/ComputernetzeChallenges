package protocol;

import client.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReliableDataTransferProtocol extends IRDTProtocol {
    
    private static final int HEADER_SIZE = 1; // number of header bytes in each packet
    private static final int DATA_SIZE = 8; // max. number of user data bytes in each packet
    
    private static final int MAX_PACKETS = 8;

    private static final int TIMEOUT_MS = 5000;

    private boolean[] acknowledgements;
    private boolean transmissionFinished;
    private int[] sequenceIdToPacketId = new int[MAX_PACKETS];
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

    private void sendPacket(Integer[] packet) {
        this.acknowledgements[this.nextPacketId] = false;
        setHeader(packet);
        registerTimeout(this.nextPacketId, packet);

        getNetworkLayer().sendPacket(packet);
        System.out.println("Sent one packet with header=" + packet[0]);

        this.nextPacketId++;
    }

    private void setHeader(Integer[] packet) {
        packet[0] = this.nextPacketId % MAX_PACKETS;
        sequenceIdToPacketId[this.nextPacketId % MAX_PACKETS] = this.nextPacketId;
    }
    

    private void registerTimeout(int packetId, Integer[] packet) {
        client.Utils.Timeout.SetTimeout(TIMEOUT_MS, this, new Object[] {packetId, packet});
    }

    @Override
    public void sender() {
        System.out.println("Sending...");

        List<Integer[]> packets = splitIntoPackets(Utils.getFileContents(getFileID()));
        this.acknowledgements = new boolean[packets.size()];

        Integer[] sizePacket = new Integer[1];
        sizePacket[0] = Utils.getFileContents(getFileID()).length;
        getNetworkLayer().sendPacket(sizePacket);

        //TODO: RESEND SIZE UPON SPECIAL TIMEOUT
        boolean sizeReceived = false;
        while (!sizeReceived){
            Integer[] ackPacket = getNetworkLayer().receivePacket();
            if (ackPacket != null){sizeReceived = true;}
            try {
                Thread.sleep(10);
            }catch (InterruptedException e){
                throw new RuntimeException("Size packet not delivered");
            }
        }
        System.out.println("Size delivered");

        while (!transmissionFinished) {
            while (Utils.modulo(
                    this.nextPacketId % MAX_PACKETS - this.lastAcknowledged % MAX_PACKETS,
                    MAX_PACKETS) <= MAX_PACKETS / 2) {
                if (this.nextPacketId < packets.size()) {
                    sendPacket(packets.get(this.nextPacketId));
                }
            }
            try {
                Thread.sleep(10);
                Integer[] ackPacket = getNetworkLayer().receivePacket();
                if (ackPacket != null) {
                    System.out.println("Received ack for packet " + ackPacket[0].toString());
                    this.acknowledgements[sequenceIdToPacketId[ackPacket[0]]] = true;
                    if (sequenceIdToPacketId[ackPacket[0]] == packets.size() - 1){
                        this.transmissionFinished = true;
                    }else if ((this.lastAcknowledged + 1) % MAX_PACKETS == ackPacket[0]) {
                        this.lastAcknowledged++;
                        while(lastAcknowledged < packets.size() - 1 &&
                                this.acknowledgements[lastAcknowledged + 1]){
                            this.lastAcknowledged++;
                        }
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        
        // schedule a timer for 1000 ms into the future, just to show how that works:
//        Utils.Timeout.SetTimeout(1000, this, 28);
        
        // and loop and sleep; you may use this loop to check for incoming acks...
//        boolean stop = false;
//        while (!stop) {
//            try {
//                Thread.sleep(10);
//            } catch (InterruptedException e) {
//                stop = true;
//            }
//        }
    }



    private int calculatePos(int sequenceNumber){
        if (sequenceNumber < (this.receivedUpTo % MAX_PACKETS)){
            return (this.receivedUpTo - (this.receivedUpTo % MAX_PACKETS)) + MAX_PACKETS + sequenceNumber;
        }else {
            return (this.receivedUpTo - (this.receivedUpTo % MAX_PACKETS)) + sequenceNumber;
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

        Integer[] sizePacket = new Integer[1];
        while (this.sizeBytes < 0){
            sizePacket = getNetworkLayer().receivePacket();
            if (sizePacket != null){
                this.sizeBytes = sizePacket[0];
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        getNetworkLayer().sendPacket(sizePacket);

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

                System.arraycopy(packet, HEADER_SIZE, fileContents, pos * DATA_SIZE, datalen);
                this.received[pos] = true;

                updateReceivedUpTo(pos);

                Integer[] ackPacket = {packet[0]};
                System.out.println("sending ack for packet " + packet[0]);
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
        Object[] data = (Object[]) tag;
        Integer packetId = (Integer) data[0];
        Integer[] packet = (Integer[]) data[1];
        System.out.println("Received timeout for packet " + data[0].toString());
        if (this.acknowledgements[packetId]) {
            return;
        }
        
        registerTimeout(packetId, packet);
        getNetworkLayer().sendPacket(packet);
        System.out.println("Sent one packet with header=" + packet[0]);
    }
}
