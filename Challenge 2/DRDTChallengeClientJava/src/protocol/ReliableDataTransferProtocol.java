package protocol;

public class ReliableDataTransferProtocol extends IRDTProtocol {
    
    static final int HEADER_SIZE = 1; // number of header bytes in each packet
    static final int DATA_SIZE = 255; // max. number of user data bytes in each packet
    
    static final int HEADER_IDS = 255;
    static final int SIZE_PACKET_HEADER = 255;
    
    private ReliableDataTransferSender sender;
    private ReliableDataTransferReceiver receiver;
    
    public ReliableDataTransferProtocol() {
        this.sender = new ReliableDataTransferSender(this);
        this.receiver = new ReliableDataTransferReceiver(this);
    }
    
    @Override
    public void sender() {
        this.sender.send();
    }
    
    @Override
    public void receiver() {
        this.receiver.receive();
    }
    
    @Override
    public void TimeoutElapsed(Object tag) {
        this.sender.TimeoutElapsed(tag);
    }
}
