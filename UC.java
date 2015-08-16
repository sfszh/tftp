import java.net.*;

public class UC {

    public static void main(String [] args) {
	if (args.length < 1) {
	    System.err.println("usage: java UC host ...");
	    System.exit(1);
	}
	String server = args[0];
	try {
	    DatagramSocket cli = new DatagramSocket(); // no specific port
	    byte [] buf;
	    for (int i=1 ; i<args.length ; i++) {
		String s = args[i];
		buf = s.getBytes();
		DatagramPacket pkt = new DatagramPacket(buf, buf.length);
		pkt.setPort(3069);
		pkt.setAddress(InetAddress.getByName(server));
		cli.setSoTimeout(500);
		int retry = 0;
		while(retry < 3) {
		    try {
		        cli.send(pkt);
			cli.receive(pkt);
			buf = pkt.getData();
			String t = new String(buf);
			System.out.println("port="+pkt.getPort()+" s="+s+" t="+t);
			break;
		    } catch (SocketTimeoutException e) {
			System.out.println("timeout");
			retry++;
			continue;
		    }
		}
	    }
	} catch (Exception e) {
	    System.out.println(e);
	}
    }
}
