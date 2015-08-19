import java.net.*;
import java.io.*;
import java.util.*;

public class ReceiverTFTP {

    public DatagramSocket skt;
    public DatagramPacket pkt;
    public DatagramPacket ackPkt;
    public DataOutputStream fileOutput;
    public InetAddress ipAddr;
    public int port;
    public byte[] bay;
    public byte[] ack;
    public int blockNum;
    public int packetSize;


    public ReceiverTFTP (InetAddress ipAddr, int port, DataOutputStream fileOutput, DatagramSocket skt, int block) {
        this.skt = skt;
        this.fileOutput = fileOutput;
        this.port = port;
        this.ipAddr = ipAddr;
        this.ack = new byte[4];
        this.bay = new byte[516];
        this.ackPkt = new DatagramPacket(ack, ack.length, ipAddr, port);
        this.blockNum = block;
        this.pkt = new DatagramPacket(bay, bay.length);
    }

 // wait for a packet to come and write the contents to outputStream
    public void receive() {

        try {
            do{ 
                skt.receive(pkt);

                if (pkt.getPort() != port) {
                    /* packet came from foreign host 
                     * send an error to that host and discard it */
                    byte[] errorData = PacketTFTP.makeErrorData(5, "Unknown transfer ID.");
                    DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, pkt.getAddress(), pkt.getPort());
                    skt.send(errorPacket);
                    continue;
                }

                // packet is from normal sender -- get the data
                bay = pkt.getData();

                // get current block number from packet
                int curBlockNum = PacketTFTP.getBlockNumber(bay);

                // get packet size to make sure it's less that 512 bytes
                packetSize = pkt.getLength();

                // make sure the block number is correct
                if (bay[0] != 0 || bay[1] != 3 || curBlockNum != blockNum){
                    // block number is not correct -- send error code & continue
                    byte[] errorData = PacketTFTP.makeErrorData(0, "Incorrect block number.");
                    DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, pkt.getAddress(), pkt.getPort());
                    skt.send(errorPacket);
                    continue;
                }
                UtilTFTP.puts("received packet: " + curBlockNum + " size: " + pkt.getLength());
                // create ACK with current Block Number
                byte[] ackNext = PacketTFTP.CreateAck(bay[2], bay[3]); //{0, 4, bay[2], bay[3]};
                ackPkt = new DatagramPacket(ackNext, ackNext.length, ipAddr, port);
                skt.send(ackPkt);
                             
                // write next 512 bytes to file
                fileOutput.write(bay, 4, pkt.getLength()-4);

                // increment to the next block
                blockNum++;

            } while (packetSize-4 == 512);  // quit once a packet of has less than 512 bytes of data

        } catch (Exception e) {
            System.out.println(e);
        }
        UtilTFTP.puts("Transfer ended");
    }
}