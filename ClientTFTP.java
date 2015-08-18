
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
		String usage = "Usage: java TFTPClinet <server> <READ | WRITE> <file>";
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

	public static boolean getFile(String filename, String dest, DatagramSocket skt, InetAddress ip, int port) {

		PacketTFTP packet_rrq, packet_ack, packet_data;
		int packet_no;

		File file_exists = null;

		try {
			file_exists = new File(dest);
		} catch (Exception e) {
			UtilTFTP.fatalError(e.getMessage());
		}

		if (file_exists != null && file_exists.exists()) {
			UtilTFTP.fatalError("Unable to download file to " + dest + ". Destination already exists in "
					+ file_exists.getAbsolutePath());
		}
		/*
		 * try { fout = new DataOutputStream(new FileOutputStream(dest));
		 * 
		 * } catch (Exception e) {
		 * 
		 * UtilTFTP.fatalError(
		 * "Unable to download file to specified destination: " + dest);
		 * 
		 * return false;
		 * 
		 * }
		 * 
		 * request = TClientRequest.RRQ;
		 * 
		 * if (server_socket.isConnected()) { UtilTFTP.puts("Connected to " +
		 * server_ip + " on port " + server_port); } else { UtilTFTP.fatalError(
		 * "Disconnected unexpectedly"); }
		 */
		try {
			DataOutputStream fileOut = new DataOutputStream(new FileOutputStream(filename));
			// out = new BufferedOutputStream(server_socket.getOutputStream());
			// in = new BufferedInputStream(server_socket.getInputStream());

			packet_rrq = new PacketTFTP();

			packet_rrq.createRRQ(filename);
			packet_rrq.dumpData();

			UtilTFTP.puts("Sending read request to server");
			packet_rrq.sendPacket(skt, ip, port);

			// wait for data packet
			packet_ack = new PacketTFTP();
			packet_data = new PacketTFTP();

			packet_no = 0;

			mainloop: while (true) {

				UtilTFTP.puts("Waiting for DATA packet");

				if (!packet_data.getPacket(skt, ip, port)) {

					UtilTFTP.puts("End of file reached");
					break;

				}

				// - gavom duomenu paketa
				if (packet_data.isData()) {
					char packet_num = packet_data.getPacketNumber();
					if (packet_num != (char) packet_no) {
						UtilTFTP.puts("WRONG ORDER" + (int) packet_num);
					}

					fileOut.write(packet_data.getData(4));

					UtilTFTP.puts("DATA received, sending ACK packet");
					packet_ack.createACK(packet_num);
					packet_ack.sendPacket(skt,ip , port);

					if (packet_data.getSize() < 4 + PacketTFTP.TFTP_PACKET_DATA_SIZE) {

						UtilTFTP.puts("File transferred");
						break;

					}

				} else if (packet_data.isError()) {

					try {
						UtilTFTP.puts("Error packet received with message: "
								+ packet_data.getString(4, packet_data.getSize()));
					} catch (Exception e) {
					}
					skt.close();
					// disconnect();

				} else {

					UtilTFTP.puts("Unexpected packet");
					skt.close();
					// disconnect();
					break mainloop;

				}

			}

		} catch (IOException ioException) {

			UtilTFTP.puts("IO Exception in thread: " + ioException.getMessage());

		}

		return true;

	}

	public static void printErrorMessage(byte[] bay) {
		// prints the error message contained within an error packet
		int length = 0;
		int slot = 4;
		while (bay[slot++] != 0)
			length++;

		// create a new string the will contain the mode
		String error = new String(bay, 4, length);
		System.out.println("ERROR MESSAGE: " + error);
	}

	public static byte[] makeErrorData(int errorCode, String errorMessage) {
		// returns a byte array of error packet data
		int position;
		byte[] errorBytes = new byte[516];
		errorBytes[0] = 0;
		errorBytes[1] = 5;
		errorBytes[2] = 0;
		errorBytes[3] = (byte) errorCode;

		for (position = 0; position < errorMessage.length(); position++) {
			errorBytes[4 + position] = (byte) errorMessage.charAt(position);
		}
		errorBytes[position + 4] = 0;

		return errorBytes;
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
					ClientTFTP.printErrorMessage(bay);
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
