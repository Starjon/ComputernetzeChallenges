package ns.tcphack;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class TcpHackClient {
    
    public static final String CON_IP = "127.0.0.1";
    public static final int CON_PORT = 1234;
    
    private BlockingQueue<Byte[]> packetQueue = new LinkedBlockingQueue<>();
    
    private DataInputStream in;
    private DataOutputStream out;
    
    public TcpHackClient() {
        try {
            new Thread(new Communicator()).start();
            Thread.sleep(100); // Give the communicator a chance
        } catch (InterruptedException e) {
        }
    }
    
    public void send(int[] data) {
        if (this.out != null) {
            try {
                this.out.writeInt(data.length);
                byte[] box = new byte[data.length];
                for (int i = 0; i < box.length; i++) {
                    box[i] = (byte) data[i];
                }
                this.out.write(box);
                this.out.flush();
            } catch (IOException e) {
                System.err.println("Couldn't write socket: " + e.getMessage());
            }
        } else {
            System.err.println("Didn't write socket: not connected");
        }
    }
    
    public int[] dequeuePacket(long timeout) {
        Byte[] box;
        
        try {
            box = this.packetQueue.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return new int[0];
        }
        
        if (box == null) {
            return new int[0];
        }
        
        int[] unbox = new int[box.length];
        for (int i = 0; i < box.length; i++) {
            unbox[i] = box[i] & 0xff;
        }
        return unbox;
    }
    
    public boolean hasPackets() {
        return !this.packetQueue.isEmpty();
    }
    
    class Communicator implements Runnable {
        
        @Override
        public void run() {
            Socket clientSocket = null;
            try {
                clientSocket = new Socket(CON_IP, CON_PORT);
                TcpHackClient.this.out = new DataOutputStream(clientSocket.getOutputStream());
                TcpHackClient.this.in = new DataInputStream(clientSocket.getInputStream());
                
                while (true) {
                    int size = TcpHackClient.this.in.readInt();
                    byte[] data = new byte[size];
                    TcpHackClient.this.in.read(data, 0, size);
                    
                    Byte[] box = new Byte[size];
                    
                    for (int i = 0; i < size; i++) {
                        box[i] = data[i];
                    }
                    
                    TcpHackClient.this.packetQueue.offer(box);
                }
            } catch (IOException e) {
                System.err.println("Couldn't read socket: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (clientSocket != null) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                }
            }
            
            System.err.println("Communicator stopped!");
        }
    }
}
