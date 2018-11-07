package protocol;

import client.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReliableDataTransferProtocol extends IRDTProtocol {
    
    private static final int HEADER_SIZE = 1; // number of header bytes in each packet
    private static final int DATA_SIZE = 128; // max. number of user data bytes in each packet
    
    private static final int MAX_PACKETS = 256;
    
    private static final int TIMEOUT_MS = 1000;
    
    private boolean[] acknowlegdements;
    private int lastAcknowledged = -1;
    private int nextPacketId;
    
    private List<Integer[]> splitIntoPackets(Integer[] data) {
        List<Integer[]> result = new ArrayList<>(data.length / DATA_SIZE + HEADER_SIZE);
        
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
        this.acknowlegdements[this.nextPacketId] = false;
        setHeader(packet);
        registerTimeout(this.nextPacketId, packet);
        
        getNetworkLayer().sendPacket(packet);
        System.out.println("Sent one packet with header=" + packet[0]);
        
        this.nextPacketId++;
    }
    
    private void setHeader(Integer[] packet) {
        packet[0] = this.nextPacketId % MAX_PACKETS;
    }
    
    private void registerTimeout(int packetId, Integer[] packet) {
        client.Utils.Timeout.SetTimeout(TIMEOUT_MS, this, new Object[] {packetId, packet});
    }
    
    @Override
    public void sender() {
        System.out.println("Sending...");
        
        List<Integer[]> packets = splitIntoPackets(Utils.getFileContents(getFileID()));
        this.acknowlegdements = new boolean[packets.size()];
        
        while (Utils.modulo(
                this.nextPacketId % MAX_PACKETS - this.lastAcknowledged,
                MAX_PACKETS) >= MAX_PACKETS / 2) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        
        
        // schedule a timer for 1000 ms into the future, just to show how that works:
        Utils.Timeout.SetTimeout(1000, this, 28);
        
        // and loop and sleep; you may use this loop to check for incoming acks...
        boolean stop = false;
        while (!stop) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                stop = true;
            }
        }
    }
    
    @Override
    public void receiver() {
        System.out.println("Receiving...");
        
        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most
        // efficient)
        // is to reallocate the array every time we find out there's more data
        Integer[] fileContents = new Integer[0];
        
        // loop until we are done receiving the file
        boolean stop = false;
        while (!stop) {
            
            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();
            
            // if we indeed received a packet
            if (packet != null) {
                
                // tell the user
                System.out.println(
                        "Received packet, length=" + packet.length + "  first byte=" + packet[0]);
                
                // append the packet's data part (excluding the header) to the fileContents array,
                // first making it larger
                int oldlength = fileContents.length;
                int datalen = packet.length - HEADER_SIZE;
                fileContents = Arrays.copyOf(fileContents, oldlength + datalen);
                System.arraycopy(packet, HEADER_SIZE, fileContents, oldlength, datalen);
                
                // and let's just hope the file is now complete
                stop = true;
                
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
        
        if (this.acknowlegdements[packetId]) {
            return;
        }
        
        registerTimeout(packetId, packet);
        getNetworkLayer().sendPacket(packet);
        System.out.println("Sent one packet with header=" + packet[0]);
    }
}
