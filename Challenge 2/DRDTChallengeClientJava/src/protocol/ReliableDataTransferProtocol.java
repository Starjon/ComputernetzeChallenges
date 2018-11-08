package protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ReliableDataTransferProtocol extends IRDTProtocol {
    
    static final int HEADER_SIZE = 1; // number of header bytes in each packet
    static final int DATA_SIZE = 200; // max. number of user data bytes in each packet
    
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
    
    public static Integer[] compress(Integer[] data) throws IOException {
        byte[] b = new byte[data.length];
        for (int i=0; i<b.length; i++) {
            b[i] = (byte) data[i].intValue();
        }
        b = compress(b);
        Integer[] result = new Integer[b.length];
        for (int i = 0; i < b.length; i++) {
            result[i] = 0xFF & b[i];
        }
        return result;
    }
    
    public static byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        
        deflater.finish();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer); // returns the generated code... index
            outputStream.write(buffer, 0, count);
        }
        
        outputStream.close();
        byte[] output = outputStream.toByteArray();
        return output;
    }
    
    public static Integer[] decompress(Integer[] data) throws IOException, DataFormatException {
        byte[] b = new byte[data.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) data[i].intValue();
        }
        b = decompress(b);
        Integer[] result = new Integer[b.length];
        for (int i = 0; i < b.length; i++) {
            result[i] = 0xFF & b[i];
        }
        return result;
    }
    
    public static byte[] decompress(byte[] data) throws IOException, DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        byte[] output = outputStream.toByteArray();
        return output;
        
    }

}
