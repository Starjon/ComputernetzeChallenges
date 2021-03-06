package my_protocol;

import framework.IMACProtocol;
import framework.MediumState;
import framework.TransmissionInfo;
import framework.TransmissionType;

/**
 * A improved Medium Access Control scheme.
 *
 * @author Jonas Becker, Daniel Beckmann
 * @version 22-11-2018
 *
 */
public class MyProtocol implements IMACProtocol {
    
    private static final double SEND_AFTER_COLLISION_PROBABILITY = 0.25;
    private static final double SEND_AFTER_FINISHED_PROBABILITY = 0.45;
    private static final double SEND_AFTER_IDLE_PROBABILITY = 0.55;

    private boolean lastNonSilentWasSuccess = true;
    private boolean triedToSendLastTime = false;
    private int packetsSent = 0;

    @Override
    public TransmissionInfo TimeslotAvailable(MediumState previousMediumState,
            int controlInformation, int localQueueLength) {
        // No data to send, just be quiet
        if (localQueueLength == 0) {
            System.out.println("SLOT - No data to send.");
            this.lastNonSilentWasSuccess = previousMediumState == MediumState.Succes
                    || (previousMediumState == MediumState.Idle && this.lastNonSilentWasSuccess);
            this.packetsSent = 0;
            this.triedToSendLastTime = false;
            return new TransmissionInfo(TransmissionType.Silent, 0);
        }

        if (previousMediumState == MediumState.Collision
                || (previousMediumState == MediumState.Idle && !lastNonSilentWasSuccess)) {
            this.lastNonSilentWasSuccess = false;
            if (Math.random() < SEND_AFTER_COLLISION_PROBABILITY) {
                System.out.println(
                        "SLOT - After collision. Send data and hope for no further collision.");
                this.packetsSent = 1;
                this.triedToSendLastTime = true;
                return new TransmissionInfo(TransmissionType.Data,
                        continueToken(localQueueLength) ? 1 : 0);
            } else {
                System.out.println("SLOT - After collision. Wait to avoid further collision.");
                this.packetsSent = 0;
                this.triedToSendLastTime = false;
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
        }
        
        if (previousMediumState == MediumState.Succes) {
            this.lastNonSilentWasSuccess = true;
            if (this.triedToSendLastTime) {
                if (controlInformation == 1) {
                    this.packetsSent++;
                    this.triedToSendLastTime = true;
                    System.out.println("SLOT - After success. We have the token, send.");
                    return new TransmissionInfo(TransmissionType.Data,
                            continueToken(localQueueLength) ? 1 : 0);
                }
                this.packetsSent = 0;
                this.triedToSendLastTime = false;
                System.out.println("SLOT - After success. We gave up the token. Wait.");
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }

            // Another node has the token
            if (controlInformation == 1) {
                System.out.println("SLOT - After success. Another node has the token. Wait.");
                this.packetsSent = 0;
                this.triedToSendLastTime = false;
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }

            if (Math.random() < SEND_AFTER_FINISHED_PROBABILITY) {
                System.out
                .println(
                        "SLOT - After success. Another node finished. Send data and hope for no collision.");
                this.triedToSendLastTime = true;
                this.packetsSent = 1;
                return new TransmissionInfo(TransmissionType.Data,
                        continueToken(localQueueLength) ? 1 : 0);
            } else {
                System.out.println(
                        "SLOT - After success. Another node finished. Wait to avoid collision.");
                this.triedToSendLastTime = false;
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
        }

        if (previousMediumState == MediumState.Idle) {
            if (Math.random() < SEND_AFTER_IDLE_PROBABILITY) {
                System.out.println(
                        "SLOT - Idle. Send data and hope for no collision.");
                this.triedToSendLastTime = true;
                this.packetsSent = 1;
                return new TransmissionInfo(TransmissionType.Data,
                        continueToken(localQueueLength) ? 1 : 0);
            } else {
                System.out.println("SLOT - Idle. Wait to avoid collision.");
                this.packetsSent = 0;
                this.triedToSendLastTime = false;
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
        }
        
        throw new RuntimeException("Unknown MediumState " + previousMediumState + ".");
        
    }
    
    private boolean continueToken(int localQueueLength) {
        return localQueueLength > 1 && Math.random() >= 0.05 * this.packetsSent;
    }
    
}
