package my_protocol;

/**
 * Simple object which describes a route entry in the forwarding table.
 * Can be extended to include additional data.
 *
 * Copyright University of Twente, 2013-2018
 *
 * This file may only be distributed unmodified.
 * In particular, a correct solution to the challenge must NOT be posted
 * in public places, to preserve the learning effect for future students.
 */
public class MyRoute {
    public final int nextHop;
    public final int cost;

    public MyRoute(int nextHop, int cost){
        this.nextHop = nextHop;
        this.cost = cost;
    }
}
