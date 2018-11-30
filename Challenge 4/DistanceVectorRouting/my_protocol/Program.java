package my_protocol;

import framework.IRoutingProtocol;
import framework.LinkLayer;
import framework.RoutingChallengeClient;
import framework.RoutingChallengeClient.SimulationState;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

/**
 * Entry point of the program. Starts the client and links the used MAC
 * protocol.
 *
 * @author Jaco ter Braak & Frans van Dijk, University of Twente
 * @version 10-03-2018
 */
public class Program {
    // Change to your group authentication token
    private static String groupToken = "get-your-token-from-the-website";

    // Change to your protocol implementation
    private static Class<? extends IRoutingProtocol> protocolImpl = MyRoutingProtocol.class;

    // Challenge server address
    private static String serverAddress = "networkingchallenges.ewi.utwente.nl";

    // Challenge server port
    private static int serverPort = 8005;


    // *                                                          *
    // **                                                        **
    // ***             DO NOT EDIT BELOW THIS LINE!             ***
    // ****                                                    ****
    // ************************************************************
    // ************************************************************

    public static void main(String[] args) {
        try {
            // Initialize communication with the emulation server
            RoutingChallengeClient client = new RoutingChallengeClient(serverAddress, serverPort, groupToken);

            LinkLayer linkLayer = new LinkLayer(client);

            // Wait for cue to start simulation
            System.out.println("[FRAMEWORK] Press Enter to start the simulation...");
            System.out.println("[FRAMEWORK] (Simulation will also be started automatically if another client in the group issues the start command)");

            boolean startCommand = false;
            InputStream inputStream = new BufferedInputStream(System.in);
            while (!client.IsSimulationRunning() && client.getSimulationState() != SimulationState.Finished) {
                if (!startCommand && inputStream.available() > 0) {
                    client.RequestStart();
                    startCommand = true;
                }
                Thread.sleep(10);
            }

            System.out.println("[FRAMEWORK] Simulation started!");

            // Run the client and the protocol
            while (client.getSimulationState() != SimulationState.Finished) {

                // Wait until we start running a test
                while (client.getSimulationState() != SimulationState.TestRunning) {
                    if (client.getSimulationState() == SimulationState.Finished)
                        break;
                    Thread.sleep(1);
                }

                if (client.getSimulationState() != SimulationState.Finished) {

                    System.out.printf("[FRAMEWORK] Running test %02d...%n", client.getTestID());

                    // Create a new instance of the protocol
                    IRoutingProtocol protocol = createProtocol();
                    protocol.init(linkLayer);

                    // Pass the protocol to the challenge client
                    client.setRoutingProtocolAndTock(protocol);

                    while (client.getSimulationState() == SimulationState.TestRunning) {
                        //wait until finished
                        Thread.sleep(10);
                    }

                    System.out.println("[FRAMEWORK] Test completed.");

                    while (client.getSimulationState() == SimulationState.TestComplete) {
                        Thread.sleep(10);
                    }
                }
            }

            System.out.println("[FRAMEWORK] Simulation finished! Check your performance on the server web interface.");

        } catch (IOException e) {
            System.out.print("[FRAMEWORK] Could not start the client because: ");
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("[FRAMEWORK] Operation interrupted");
            e.printStackTrace();
        }
    }


    private static IRoutingProtocol createProtocol() {
        try {
            return (IRoutingProtocol) protocolImpl.getConstructor(new Class[0])
                    .newInstance(new Object[0]);
        } catch (InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }
}
