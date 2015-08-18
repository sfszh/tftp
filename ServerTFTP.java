import java.net.*;
import java.io.*;
import java.util.*;

public class ServerTFTP extends Thread {

    DatagramPacket pkt;
    DatagramPacket ackPkt;

    public ServerTFTP (DatagramPacket pkt) {
        this.pkt = pkt;
    }

    public void run() {
        byte[] buf = new byte[516];
        // get sending information of client
        int port = pkt.getPort();
        InetAddress address = pkt.getAddress();

        try {
            // open up a new socket for data flow
            DatagramSocket serverSocket = new DatagramSocket();
            
            //add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                	serverSocket.close();
                    System.out.println("Server Socket closed");
                }
            });
            // assume the datagram buffer contains characters
            buf = pkt.getData();

            // get the file name from the packet
            String fileName = getFileName(buf);

            // get mode from the packet
            String mode = getMode(buf);

            // if the mode is something other than ``octet" then quit
            if (!mode.equals("octet")) {
                byte[] errorData = makeErrorData(0, "Mode other than octet.");
                DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, address, port);
                serverSocket.send(errorPacket);
                System.exit(1);
            }

            // Check to see whether it's a RRQ or a WRQ
            if (buf[0] == 0 && buf[1] == 1) {  // it's a RRQ
                
                try {  // check to see if file exists
                    if (!new File(fileName).exists()) {
                        byte[] errorData = makeErrorData(1, "File not found.");
                        DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, address, port);
                        serverSocket.send(errorPacket);
                        System.exit(1);
                    }

                    // open up a reader on the file
                    DataInputStream fileData = new DataInputStream(new FileInputStream(fileName));

                    // create a new SenderTFTP object and start the sending process
                    SenderTFTP sender = new SenderTFTP(address, port, fileData, serverSocket);
                    sender.send();

                } catch (Exception e) {
                    System.out.println(e);
                }

            } else if (buf[0] == 0 && buf[1] == 2) {  // it's a WRQ

                if (new File(fileName).exists()) {
                    // file already exists, send error packet and quit
                    byte[] error = makeErrorData(6, "File already exists.");
                    DatagramPacket errorPacket = new DatagramPacket(error, error.length, pkt.getAddress(), pkt.getPort());
                    serverSocket.send(errorPacket);
                    System.exit(1);
                }

		DataOutputStream fileOutput = new DataOutputStream(new FileOutputStream(fileName));

                // populate buf with the ACK op number and a block of zero
                byte[] ack = {0, 4, 0, 0};
                int block = 1;	

                ackPkt = new DatagramPacket(ack, ack.length, address, port);
                serverSocket.send(ackPkt);

                // start receiving 512 byte chunks
                ReceiverTFTP receiver = new ReceiverTFTP(address, port, fileOutput, serverSocket, block);
                receiver.receive();
            } else {  // it's not a RRQ nor a WRQ
                byte[] errorData = makeErrorData(0, "First packet must be RRQ or WRQ.");
                DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, address, port);
                serverSocket.send(errorPacket);
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    // make sure the request packet is speaking in terms of octet
    // get the index number after the end of fileName
    public String getMode(byte[] b) {
	int index = 2;  // start looking right after [01]
        while (b[index++] != 0);

        // get the length of whatever is sanwiched between the two null
        // byte [01fileName0*we_want_this_length*0]
        int length = 0;
        int offset = index;
        while (b[index++] != 0)
            length++;

        // create a new string the will contain the mode
        return new String(b, offset, length);
    }

    // extract the file name out of request packet
    public String getFileName(byte[] b) { 
        String fileName = "";
        int i = 2;
        while (b[i] != 0) {
            fileName += (char)b[i++];
        }
        return fileName;
    }

    //method creates error packets
    public byte[] makeErrorData(int errorCode, String errorMessage) {
        int position;
        byte[] errorBytes = new byte[516];
        errorBytes[0] = 0;
        errorBytes[1] = 5;
        errorBytes[2] = 0;
        errorBytes[3] = (byte)errorCode;

        for (position = 0; position < errorMessage.length(); position++) {
            errorBytes[4+position] = (byte)errorMessage.charAt(position);
        }
        errorBytes[position+4] = 0;

        return errorBytes;
    }

    public static void main(String [] args) {

        try {
            DatagramSocket srv = new DatagramSocket(6969);
            while (true) {
                byte [] buf = new byte[516];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                srv.receive(pkt);
                buf = pkt.getData();
                // create a new thread to handle request and continue listening
                new ServerTFTP(pkt).run();
            }
        } catch (Exception e) {
            System.err.println(e);
        }
    }
}
