
import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.util.*;

public class ClientTFTP {

	final static int DEFAULT_PORT = 6969;

	public static void main(String[] args) {
		// error checking
		String usage = "Guide: java client server [read or write] file mode ";
		if (args.length < 3) {
			System.err.println(usage);
			System.exit(1);
		} else if (!args[1].equals("read") && !args[1].equals("write")) {
			System.err.println(usage);
			System.exit(1);
		} else if (args[2].contains("/")) {
			System.err.println("Filename cannot contain '/'!");
			System.exit(1);
		} else if (args[2].charAt(0) == '.') {
			System.err.println("Filename cannot start with '.'!");
			System.exit(1);
		}

		final String dir = System.getProperty("user.dir");
		System.out.println("current dir = " + dir);

		if (args[1].equals("read") || args[1].equals("write")) {
			// initialize some variables
			int block = 0;
			DataOutputStream fileOut = null;
			DataInputStream fileData = null;
			byte[] bay = new byte[516];
			String server = args[0];
			String request = args[1];
			String fileName = args[2];

			try {
				if (request.equals("read")) {
					
					// make sure file doesn't already exist
					if (new File(fileName).exists()) {
						System.out.println("File already exists " + fileName + ", Try to update...");
						Path path = Paths.get("./" + fileName);
						Files.delete(path);
						// System.exit(1);
					} else {
						System.out.println("Downloading file " + fileName);
					}
					// prepare the RRQ data to send to server
					// populates bay with [01][fileName][0][octet][0]
					ClientTFTP.prepareDataRRQ(bay, fileName, "octet");

					// first block receiver plans to receive is 1
					block = 1;
					// create generic socket
					DatagramSocket cliSkt = new DatagramSocket(); // no specific
																// port

					InetAddress ipAddr = InetAddress.getByName(server);
					int port = DEFAULT_PORT;
					DatagramPacket pkt = new DatagramPacket(bay, bay.length, ipAddr, port);

					// send the packet
					if(!sendWithTimeout(cliSkt, pkt, request, block)){
						disconnect();
						//System.exit(1);
					}

					// port for server has changed -- get the new port
					port = pkt.getPort();
					ipAddr = pkt.getAddress();

					// open up a stream for writing
					fileOut = new DataOutputStream(new FileOutputStream(fileName));
					// write the first packet into file
					if (pkt.getLength() - 4 > 0) {
						fileOut.write(bay, 4, pkt.getLength() - 4);
					}

					// create first ACK with block number 1
					byte[] ackbuf = PacketTFTP.createAck(1); // { 0, 4, 0, 1 };
					DatagramPacket ackPkt = new DatagramPacket(ackbuf, ackbuf.length, ipAddr, port);
					// send the first ACK
					UtilTFTP.puts("received packet: " + 1 + " size: " + pkt.getLength());
					cliSkt.send(ackPkt);

					//use receiver instead.
					if (pkt.getLength() - 4 == 512) {
						ReceiverTFTP receiver = new ReceiverTFTP(ipAddr, port, fileOut, cliSkt, 2);
						receiver.receive();
					}else{
						UtilTFTP.puts("transfer ends here");
					}

				} else if (request.equals("write")) { // open up a stream for
														// reading
					// open up stream on file
					fileData = new DataInputStream(new FileInputStream(fileName));

					// prepare WRQ packet data
					// populates bay with [02[fileName][0][octet][0]
					ClientTFTP.prepareDataWRQ(bay, fileName, "octet");

					// first block receiver plans to see is 0
					block = 0;

					// create generic socket
					DatagramSocket cli = new DatagramSocket(); // no specific
																// port

					// get information for packet
					int port = DEFAULT_PORT;
					InetAddress address = InetAddress.getByName(server);
					DatagramPacket pkt = new DatagramPacket(bay, bay.length, address, port);

					// send the packet
					if(!ClientTFTP.sendWithTimeout(cli, pkt, request, block)){
						disconnect();
					}

					// port for server has changed -- get the new port
					port = pkt.getPort();
					address = pkt.getAddress();

					// TFTPClient now takes on the role of sender
					SenderTFTP sender = new SenderTFTP(address, port, fileData, cli);
					sender.send();
				}

			} catch (Exception e) {
				System.out.println(e);
			}
		}

	}

	public static void prepareDataRRQ(byte[] bay, String fileName, String mode) {
		int index;
		// set first two bytes of outgoing packet to 01 to indicicate RRQ
		bay[0] = 0;
		bay[1] = 1;

		/*
		 * populate next free bytes in bay with the ``fileName" followed by null
		 * character
		 */
		for (index = 0; index < fileName.length(); index++) {
			bay[index + 2] = (byte) fileName.charAt(index);
		}
		index += 2;
		bay[index++] = 0; // bay is now [02][filename][0]

		/*
		 * populate next free bytes in bay with ``octet" followed by null
		 * character
		 */
		for (int i = 0; i < mode.length(); i++) {
			bay[index++] = (byte) mode.charAt(i);
		}
		bay[index] = 0; // bay is now [02][filename][0][octet][0]
	}

	public static void prepareDataWRQ(byte[] bay, String fileName, String mode) {
		int index;
		// set first two bytes of outgoing packet to 02 to indicicate WRQ
		bay[0] = 0;
		bay[1] = 2;

		/*
		 * populate next free bytes in bay with the ``fileName" followed by null
		 * character
		 */
		for (index = 0; index < fileName.length(); index++) {
			bay[index + 2] = (byte) fileName.charAt(index);
		}
		index += 2;
		bay[index++] = 0; // bay is now [02][filename][0]

		/*
		 * populate next free bytes in bay with ``octet" followed by null
		 * character
		 */
		for (int i = 0; i < mode.length(); i++) {
			bay[index++] = (byte) mode.charAt(i);
		}
		bay[index] = 0; // bay is now [02][filename][0][octet][0]
	}

	public static boolean incorrectBlockRRQ(byte[] bay, int block) {
		return bay[0] != 0 || bay[1] != 3 || bay[2] != 0 || (int) bay[3] != block;
	}

	public static boolean incorrectBlockWRQ(byte[] bay, int block) {
		return bay[0] != 0 || bay[1] != 4 || bay[2] != 0 || (int) bay[3] != block;
	}

	private static boolean sendWithTimeout(DatagramSocket cli, DatagramPacket pkt, String request, int block)
			throws Exception {
		int retry = 0;
		byte[] bay = new byte[516];

		while (retry < 3) {
			try {
				cli.setSoTimeout(500);
				cli.send(pkt);
				// pkt = new DatagramPacket(bay, bay.length);
				cli.receive(pkt);
				bay = pkt.getData();

				// check for error opcode
				if (bay[0] == 0 && bay[1] == 5) {
					PacketTFTP.printErrorMessage(bay);
					System.exit(1);
				}

				if (request.equals("write") && ClientTFTP.incorrectBlockWRQ(bay, block)) {
					// ACK number is not zero
					System.out.println("INCORRECT BLOCK NUMBER SENT -- EXITING");
					System.exit(1);
				} else if (request.equals("read") && ClientTFTP.incorrectBlockRRQ(bay, block)) {
					// intial header of data has block other than 1
					System.out.println("INCORRECT BLOCK NUMBER SENT -- EXITING");
					System.exit(1);
				}

				// header looks good, break out
				break;
			} catch (SocketTimeoutException e) {
				System.out.println("TIMEOUT");
				retry++;
				continue;
			}
		}
		if(retry == 3){
			return false;
		}else{
			return true;
		}		
	}
	
	private static void disconnect(){
		UtilTFTP.puts("shutdown");
		System.exit(1);
	}
}
