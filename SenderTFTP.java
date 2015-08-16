/*
  Names: Lee Page, Edward Banner
  Class: CIS 410 - Networks
  Assignment: 5
  Due date: April 18 2012
*/

import java.net.*;
import java.io.*;
import java.util.*;

public class SenderTFTP {

    public DatagramSocket senderSocket;
    public DatagramPacket packet;
    public DatagramPacket ackPacket;
    public DataInputStream fileData;
    public InetAddress address;
    public int port;
    public byte[] bay;
    public byte[] ack;
    public int block;
    public int bytesRead;

    public SenderTFTP(InetAddress address, int port, DataInputStream fileData, DatagramSocket senderSocket) {
        this.senderSocket = senderSocket;
        this.fileData = fileData;
        this.port = port;
        this.address = address;
        this.bay = new byte[516];
        this.ack = new byte[512];
        this.block = 1;
    }

    public void send() {

        // populate data packet byte array with opcode of DATA
        bay[0] = 0;
        bay[1] = 3;

        try {
            do {// read next 512 bytes from file
                // send packet to receiver
                // get response from reciever

                // put current block number in data packet
                insertBlockNumber(bay);

                // get next 512 bytes, starting at the 4th byte in bay
                bytesRead = fileData.read(bay, 4, 512);

                // make a packet with bytes received
                if (bytesRead == 512) {  // we are not at the end of the file
                    packet = new DatagramPacket(bay, 516, address, port);

                } else {  // we are at the end of the file
                    if (bytesRead == -1){  // no bytes left to read
                        // just send over data packet with opcode length
                        packet = new DatagramPacket(bay, 4, address, port);
                    } else {  // contains something but less than 512
                        // send packet over with data length of 
                        // opcode length + data length
                        packet = new DatagramPacket(bay, 4+bytesRead, address, port);
                    }
                }
                System.out.println("sending package with length " + bytesRead + " : " + bay);
                // send the packet of data with timeouts
                sendWithTimeout(packet);

                /* ACK came back with correct block # so increment block */
                block++;

            } while (bytesRead == 512);

        } catch(Exception e) {
            System.out.println(e);
        }
    }

    public byte[] makeErrorData(int errorCode, String errorMessage) {
        // return a byte array containing all data necessary for an error packet
        int position;
        byte[] errorBytes = new byte[516];
        errorBytes[0] = 0;
        errorBytes[1] = 5;
        errorBytes[2] = 0;
        errorBytes[3] = (byte)errorCode;

        // fill up the array with a message
        for (position = 0; position < errorMessage.length(); position++) {
            errorBytes[4+position] = (byte)errorMessage.charAt(position);
        }
        // cap it with a null byte
        errorBytes[position+4] = 0;

        return errorBytes;
    }

    public void sendWithTimeout(DatagramPacket packet) throws Exception {

        // make packet for acks
        ackPacket = new DatagramPacket(ack, ack.length);

        try {
            senderSocket.setSoTimeout(500);
        } catch (Exception e) { }

        // set timeout to 500 ms
        int retry = 0;
        int ackRetry = 0;

        while(retry < 3) {
            try {
                // try to send the packet
                senderSocket.send(packet);

                // wait around to receive a packet -- timeout could occur
                senderSocket.receive(ackPacket);

                if (ackPacket.getPort() != port) {  // packet came from foreign port
                    // send an error packet to the foreign source
                    byte[] errorData = makeErrorData(5, "Unknown transfer ID.");
                    DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, ackPacket.getAddress(), ackPacket.getPort());
                    senderSocket.send(errorPacket);

                    // disregard packet and resend previous
                    continue;
                }

                // got ACK, check to make sure block number is correct
                ack = ackPacket.getData();
                int highByte = ack[2] & 0xff;
                highByte = highByte << 8;
                int ackBlock = highByte | (ack[3]&0xff);

                while (ack[0] != 0 || ack[1] != 4 || ackBlock != block && ackRetry < 3) {
                    System.out.println("Expecting block # " + block);
                    System.out.println("Actual block # is " + ackBlock);

                    // check for duplicate ACK
                    if (ack[0] == 0 && ack[1] == 4 && ackBlock == block-1) {
                        // it's a duplicate ACK
                        break;
                    }

                    if (ack[0] == 0 && ack[1] == 5) {  // it's an error packet
                        // print out the error and continue
                        printErrorMessage(ack);
                    }

                    // only try 3 times if the block number is incorrect
                    packet = new DatagramPacket(bay, bay.length, address, port);

                    // send the packet again
                    senderSocket.send(packet);

                    // attempt to recieve the packet -- timeout could occur
                    senderSocket.receive(ackPacket);

                    if (ackPacket.getPort() != port) {  // packet came from a foreign source
                        // send an error packet to the foreign source
                        byte[] errorData = makeErrorData(5, "Unknown transfer ID.");
                        DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, ackPacket.getAddress(), ackPacket.getPort());
                        senderSocket.send(errorPacket);

                        // disregard packet and resend previous
                        continue;
                    }

                    // get the data from the ACK and try to see if block # is
                    // now correct
                    ack = ackPacket.getData();
                    ackRetry++;
                }

                if (ackRetry == 3) {
                    System.out.println("3 INCORRECT ACKS -- EXITING");
                    System.exit(1);
                }

                // made it all the way through without timing out and correct
                // block #
                break;

            } catch (SocketTimeoutException e) {
                System.out.println("TIMEOUT FROM SENDER");
                System.out.println(e);
                retry++;
                break;
            }
        }
    }

    public void printErrorMessage(byte[] bay) {
        // prints the error message contained within an error packet
        int length = 0;
        int slot = 4;
        while (bay[slot++] != 0)
            length++;

        // create a new string the will contain the mode
        String error = new String(bay, 4, length);
        System.out.println("ERROR MESSAGE: " + error);
    }

    public void insertBlockNumber(byte[] bay) {
        bay[2] = (byte)(block >>> 8);
        bay[3] = (byte)(block & (int)0xff);
    }
}
