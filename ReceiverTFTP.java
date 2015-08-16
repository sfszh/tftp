/*
  Names: Lee Page, Edward Banner
  Class: CIS 410 - Networks
  Assignment: 5
  Due date: April 18 2012
*/
import java.net.*;
import java.io.*;
import java.util.*;

public class ReceiverTFTP {

    public DatagramSocket sss;
    public DatagramPacket pkt;
    public DatagramPacket ackPkt;
    public DataOutputStream fileOutput;
    public InetAddress address;
    public int port;
    public byte[] bay;
    public byte[] ack;
    public int block;
    public int packetSize;


    public ReceiverTFTP (InetAddress address, int port, DataOutputStream fileOutput, DatagramSocket sss, int block) {
        this.sss = sss;
        this.fileOutput = fileOutput;
        this.port = port;
        this.address = address;
        this.ack = new byte[4];
        this.bay = new byte[516];
        this.ackPkt = new DatagramPacket(ack, ack.length, address, port);
        this.block = block;
        this.pkt = new DatagramPacket(bay, bay.length);
    }

    public void receive() {

        try {
            do{ // wait around for a packet to come
                // write the contents of buf to the file until size(buf) < 516
                sss.receive(pkt);

                if (pkt.getPort() != port) {
                    /* packet came from foreign host 
                     * send an error to that host and discard it */
                    byte[] errorData = makeErrorData(5, "Unknown transfer ID.");
                    DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, pkt.getAddress(), pkt.getPort());
                    sss.send(errorPacket);
                    continue;
                }

                // packet is from normal sender -- get the data
                bay = pkt.getData();

                // get current block number from packet
                int recBlock = getBlockNumber(bay);

                // get packet size to make sure it's less that 512 bytes
                packetSize = pkt.getLength();

                // make sure the block number is correct
                if (bay[0] != 0 || bay[1] != 3 || recBlock != block){
                    // block number is not correct -- send error code & continue
                    byte[] errorData = makeErrorData(0, "Incorrect block number.");
                    DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, pkt.getAddress(), pkt.getPort());
                    sss.send(errorPacket);
                    continue;
                }

                // create ACK packet with current block number & send
                byte[] ackNext = {0, 4, bay[2], bay[3]};
                ackPkt = new DatagramPacket(ackNext, ackNext.length, address, port);
                sss.send(ackPkt);
                             
                // write next 512 bytes to file
                fileOutput.write(bay, 4, pkt.getLength()-4);

                // increment to the next block
                block++;

            } while (packetSize-4 == 512);  // quit once a packet of has less than 512 bytes of data

        } catch (Exception e) {
            System.out.println(e);
        }
    }
    //extracts error message from error packets
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
    //converts 2 bytes to block number
    public int getBlockNumber(byte[] bay) {
        int highByte = bay[2] & 0xff;
        highByte = highByte << 8;
        return highByte | (bay[3] & 0xff);
    }
}