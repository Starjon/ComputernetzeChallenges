package my_protocol;

import framework.IMACProtocol;
import framework.MediumState;
import framework.TransmissionInfo;
import framework.TransmissionType;

/**
 * A fairly trivial Medium Access Control scheme.
 *
 * @author Jaco ter Braak, University of Twente
 * @version 05-12-2013
 *
 * Copyright University of Twente, 2013-2018
 *
 * This file may only be distributed unmodified.
 * In particular, a correct solution to the challenge must NOT be posted
 * in public places, to preserve the learning effect for future students.
 */
public class MyProtocol implements IMACProtocol {

    private static final double SEND_AFTER_COLLISION_PROBABILITY = 0.3;
    private static final double SEND_AFTER_FINISHED_PROBABILITY = 0.6;
    private static final double SEND_AFTER_IDLE_PROBABILITY = 0.6;
    
    private boolean lastNonSilentWasSuccess = true;
    private boolean triedToSendLastTime = false;
    private int packagesSent = 0;
    
    @Override
    public TransmissionInfo TimeslotAvailable(MediumState previousMediumState,
            int controlInformation, int localQueueLength) {
        // No data to send, just be quiet
        if (localQueueLength == 0) {
            System.out.println("SLOT - No data to send.");
            this.lastNonSilentWasSuccess = previousMediumState == MediumState.Succes
                    || (previousMediumState == MediumState.Idle && this.lastNonSilentWasSuccess);
            this.packagesSent = 0;
            this.triedToSendLastTime = false;
            return new TransmissionInfo(TransmissionType.Silent, 0);
        }
        
        if (previousMediumState == MediumState.Collision
                || (previousMediumState == MediumState.Idle && !lastNonSilentWasSuccess)) {
            this.lastNonSilentWasSuccess = false;
            if (Math.random() < SEND_AFTER_COLLISION_PROBABILITY) {
                System.out.println(
                        "SLOT - After collision. Send data and hope for no further collision.");
                this.packagesSent = 1;
                this.triedToSendLastTime = true;
                return new TransmissionInfo(TransmissionType.Data,
                        continueToken(localQueueLength) ? 1 : 0);
            } else {
                System.out.println("SLOT - After collision. Wait to avoid further collision.");
                this.packagesSent = 0;
                this.triedToSendLastTime = false;
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
        }

        if (previousMediumState == MediumState.Succes) {
            this.lastNonSilentWasSuccess = true;
            if (this.triedToSendLastTime) {
                if (controlInformation == 1) {
                    this.packagesSent++;
                    this.triedToSendLastTime = true;
                    System.out.println("SLOT - After success. We have the token, send.");
                    return new TransmissionInfo(TransmissionType.Data,
                            continueToken(localQueueLength) ? 1 : 0);
                }
                // ggf warten bis silent oder information 0 war, bevor wieder schicken
                this.packagesSent = 0;
                this.triedToSendLastTime = false;
                System.out.println("SLOT - After success. We gave up the token. Wait.");
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
            
            // Another node has the token
            if (controlInformation == 1) {
                System.out.println("SLOT - After success. Another node has the token. Wait.");
                this.packagesSent = 0;
                this.triedToSendLastTime = false;
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
            
            if (Math.random() < SEND_AFTER_FINISHED_PROBABILITY) {
                System.out
                .println(
                        "SLOT - After success. Another node finished. Send data and hope for no collision.");
                this.triedToSendLastTime = true;
                this.packagesSent = 1;
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
                this.packagesSent = 1;
                return new TransmissionInfo(TransmissionType.Data,
                        continueToken(localQueueLength) ? 1 : 0);
            } else {
                System.out.println("SLOT - Idle. Wait to avoid collision.");
                this.packagesSent = 0;
                this.triedToSendLastTime = false;
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
        }

        throw new RuntimeException("Unknown MediumState " + previousMediumState + ".");

    }

    private boolean continueToken(int localQueueLength) {
//        return 3 * this.packagesSent < localQueueLength;
        return Math.random() /this.packagesSent * 2 <= 0.5;
    }

}
