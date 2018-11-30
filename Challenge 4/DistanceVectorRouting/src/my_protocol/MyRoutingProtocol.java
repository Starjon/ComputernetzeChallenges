package my_protocol;

import framework.DataTable;
import framework.IRoutingProtocol;
import framework.LinkLayer;
import framework.Packet;

import java.util.HashMap;
import java.util.Map;

/**
 * Copyright University of Twente, 2013-2018
 *
 * This file may only be distributed unmodified.
 * In particular, a correct solution to the challenge must NOT be posted
 * in public places, to preserve the learning effect for future students.
 */
public class MyRoutingProtocol implements IRoutingProtocol {
    private LinkLayer linkLayer;

    // You can use this data structure to store your routing table.
    private Map<Integer, MyRoute> myRoutingTable = new HashMap<>();


    @Override
    public void init(LinkLayer linkLayer) {
        this.linkLayer = linkLayer;
    }


    @Override
    public void tick(Packet[] packets) {
        // Get the address of this node
        int myAddress = this.linkLayer.getOwnAddress();

        System.out.println("tick; received " + packets.length + " packets");
        int i;

        this.myRoutingTable.clear();

        // first process the incoming packets; loop over them:
        for (i = 0; i < packets.length; i++) {
            int neighbour = packets[i].getSourceAddress();          // from whom is the packet?
            int linkcost = this.linkLayer.getLinkCost(neighbour);   // what's the link cost from/to this neighbour?
            DataTable dt = packets[i].getDataTable();                    // other data contained in the packet
            System.out.printf("received packet from %d with %d rows and %d columns of data%n", neighbour, dt.getNRows(), dt.getNColumns());

            for (int j=0; j< dt.getNRows();j++){
                int dest = dt.get(j,0);
                if(dest == this.linkLayer.getOwnAddress()){
                    continue;
                }
                if(dt.get(j,2) == this.linkLayer.getOwnAddress()){
                    continue;
                }
                MyRoute current = this.myRoutingTable.get(dest);
                if(current == null || current.cost > linkcost + dt.get(j,1) || current.nextHop == neighbour) {
                    MyRoute newEntry = new MyRoute(neighbour, linkcost + dt.get(j, 1));
                    this.myRoutingTable.put(dest, newEntry);
                }
            }


        }



        Packet pkt = new Packet(myAddress, 0, getTableToSend());
        this.linkLayer.transmit(pkt);

        /*
        Instead of using Packet with a DataTable you may also use Packet with
        a byte[] as data part, if really you want to send your own data structure yourself.
        Read the JavaDoc of Packet to see how you can do this.
        PLEASE NOTE! Although we provide this option we do not support it.
        */
    }

    private DataTable getTableToSend(){
        DataTable dt = new DataTable(3);
        dt.setRow(0,new Integer[]{this.linkLayer.getOwnAddress(),0,0});
        int i = 1;
        for(Map.Entry<Integer, MyRoute> entry: myRoutingTable.entrySet()){
            dt.setRow(i,new Integer[]{entry.getKey(),entry.getValue().cost,entry.getValue().nextHop});
            i++;
        }
        return dt;
    }

    public HashMap<Integer, Integer> getForwardingTable() {
        // This code extracts from your routing table the forwarding table.
        // The result of this method is send to the server to validate and score your protocol.

        // <Destination, NextHop>
        HashMap<Integer, Integer> ft = new HashMap<>();

        for (Map.Entry<Integer, MyRoute> entry : myRoutingTable.entrySet()) {
            ft.put(entry.getKey(), entry.getValue().nextHop);
        }

        ft.put(this.linkLayer.getOwnAddress(),this.linkLayer.getOwnAddress());

        return ft;
    }
}
