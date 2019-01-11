package ns.tcphack;

abstract class TcpHandler {
	private TcpHackClient client;

	public TcpHandler() {
		client = new TcpHackClient();
	}

	protected void sendData(int[] data) {
		client.send(data);
	}

	protected int[] receiveData(long timeout) {
		return client.dequeuePacket(timeout);
	}
}
