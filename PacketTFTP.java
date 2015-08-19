

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class PacketTFTP {

	static final char TFTP_OPCODE_READ 	= 1;
	static final char TFTP_OPCODE_WRITE = 2;
	static final char TFTP_OPCODE_DATA 	= 3;
	static final char TFTP_OPCODE_ACK 	= 4;
	static final char TFTP_OPCODE_ERROR = 5;
	
	public static final String TFTP_ERROR_0 = "Not defined, see error message (if any)";
	public static final String TFTP_ERROR_1 = "File not found";
	public static final String TFTP_ERROR_2 = "Access violation";
	public static final String TFTP_ERROR_3 = "Disk full or allocation exceeded";
	public static final String TFTP_ERROR_4 = "Illegal TFTP operation";
	public static final String TFTP_ERROR_5 = "Unknown transfer ID";
	public static final String TFTP_ERROR_6 = "File already exists";
	public static final String TFTP_ERROR_7 = "No such user";
	
	public static final int TFTP_PACKET_MAX_SIZE = 1024;
	public static final int TFTP_PACKET_DATA_SIZE = 512;
	public static final int TIMEOUT_TIME = 1000;
	
	static final String TFTP_DEFAULT_TRANSFER_MODE = "octet";
	
	protected int current_packet_size;
	public byte[] data; 
	
	protected int packet_num;
	
	public static byte[] createAck(int packet_num){
		byte[] ack = { 0, 4, 0, (byte)packet_num };
		return ack;
	}
	
	public static byte[] CreateAck(byte first, byte second){
		byte[] ack = {0,4,first,second};
		return ack;
	}
	
    //converts 2 bytes to block number
    public static int getBlockNumber(byte[] bay) {
        int highByte = bay[2] & 0xff;
        highByte = highByte << 8;
        return highByte | (bay[3] & 0xff);
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
    
	public PacketTFTP() {
		
		current_packet_size = 0;
		packet_num = 0;
		
		data = new byte[TFTP_PACKET_MAX_SIZE];
		
	}
	
	
	
	public void clear() {
		
		current_packet_size = 0;
		
		for (int i = 0; i < TFTP_PACKET_MAX_SIZE; i++) data[i] = 0;
		
	}
	
	public int getSize() {
		
		return current_packet_size;
		
	}
	
	public void setSize(int size) throws Exception {
		
		if (size < TFTP_PACKET_MAX_SIZE) {
			current_packet_size = size;
		} else {
			throw new Exception("Packet size exceeded");
		}
		
	}
	
	public void dumpData() {
		
		UtilTFTP.puts("--------------DUMP---------------------");
		UtilTFTP.puts("Packet Size: " + current_packet_size);

		UtilTFTP.puts(new String(data));
		
		UtilTFTP.puts("--------------------------------------------");

	}
	
	public boolean addByte(byte b) {
		
		if (current_packet_size >= TFTP_PACKET_MAX_SIZE) {
			return false;
		}

		data[current_packet_size] = b;
		current_packet_size++;

		return true;
		
	}
	
	public boolean addWord(char w) {

		if (!addByte((byte)((w&0xFF00)>>8))) {
			return false;
		}
		
		return addByte((byte)(w&0x00FF));
		
	}
	
	public boolean addString(String s) {
		
		byte[] b = new byte[s.length()];
		b = s.getBytes();

		for (int i = 0; i < s.length(); i++) {

			if (!addByte(b[i])) { 

				return false;

			}

		}

		return true;
		
	}
	
	public boolean addMemory(byte[] buf, int buf_size) {

		if (current_packet_size + buf_size >= TFTP_PACKET_MAX_SIZE)	{
			UtilTFTP.puts("Packet size exceeded");
			return false;
		}
		
		for (int i = 0; i < buf_size; i++) {
			
			 data[current_packet_size + i] = buf[i];
			
		}
		
		current_packet_size += buf_size;
		
		return false;
		
	}
	
	public byte getByte(int offset) {
		
		return data[offset];
		
	}
	
	public char getWord(int offset) {
		
		return (char)(data[offset] << 8 | data[offset + 1]);
		
	}
	
	public String getString(int offset, int length) throws Exception {

		if (offset > current_packet_size) throw new Exception("getString() out of packet bounds");

		if (length < current_packet_size - offset) throw new Exception("getString() length is out of packet bounds");

		String output = new String();

		for (int i = offset; i < offset + length; i++) {
			
			if (data[i] == 0) break; //zero-terminated

			output += (char)data[i];

		}
		
		return output;
		
	}
	
	public char getPacketNumber() {
		
		return (isData() || isACK()) ? getWord(2) : 0;
		
	}
	
	public byte[] getData(int offset) {
		
		if (!isData()) return null;
		
		byte[] data_part = new byte[getSize() - 4];
		
		for (int i = offset; i < current_packet_size; i++) {
			data_part[i-offset] = data[i];
		}
		
		return data_part;
		
	}
	
	
	
	public void createRRQ(String filename) {
		
		clear();
		addWord(TFTP_OPCODE_READ);
		addString(filename);
		addByte((byte)0);
		addString(TFTP_DEFAULT_TRANSFER_MODE);
		addByte((byte)0);

	}
	
	public void createWRQ(String filename) {
		
		clear();
		addWord(TFTP_OPCODE_WRITE);
		addString(filename);
		addByte((byte)0);
		addString(TFTP_DEFAULT_TRANSFER_MODE);
		addByte((byte)0);

	}
	
	public void createACK(char packet_num) {
		
		clear();
		addWord(TFTP_OPCODE_ACK);
		addWord(packet_num);

	}	

	public void createData(int block, byte[] data, int data_size) {
		
		clear();
		addWord(TFTP_OPCODE_DATA);
		addWord((char)block);
		addMemory(data, data_size);

	}

	public void createError(int error_code, String message) {
		
		clear();
		addWord(TFTP_OPCODE_ERROR);
		addWord((char)error_code);
		addString(message);
		addByte((byte)0);

	}
	
	public boolean isRRQ() {
		
		return ((int)getWord(0) == TFTP_OPCODE_READ);		
		
	}
	
	public boolean isWRQ() {

		return ((int)getWord(0) == TFTP_OPCODE_WRITE);
		
	}
	
	public boolean isACK() {
		
		return ((int)getWord(0) == TFTP_OPCODE_ACK);
		
	}
	
	public boolean isData() {
		
		return ((int)getWord(0) == TFTP_OPCODE_DATA);
		
	}
	
	public boolean isError() {
		
		return ((int)getWord(0) == TFTP_OPCODE_ERROR);
		
	}
	
	public boolean sendPacket(BufferedOutputStream out) {
		
		try {
			out.write(data, 0, getSize());
			out.flush();
			return true;
		} catch (Exception e) {
			UtilTFTP.puts("Exception in sendPacket()");
			return false;
		}
		
	}
	
	//send current packet to ip:port via skt
	public boolean sendPacket(DatagramSocket skt, InetAddress ip, int port){
		try{
			skt.setSoTimeout(TIMEOUT_TIME);
			DatagramPacket pkt = new DatagramPacket(data, getSize(), ip, port);
			skt.send(pkt);
			return true;
		}catch (Exception e) {
			UtilTFTP.puts("Exception in sendPacket() " + e.getMessage());
			return false;
		}
	}
	
	public boolean getPacket(BufferedInputStream in) {
		
		clear();

   		int bytes_read = 0;
   		
   		try {
   			bytes_read = in.read(data, 0, TFTP_PACKET_MAX_SIZE);
   			
   			if (bytes_read == -1) {
   	   			return false;
   	   		}
   			
   			setSize(bytes_read);
   			
   		} catch (Exception e) {
   			UtilTFTP.puts("Exception in getPacket()");
   			return false;
   		}
   		
   		return true;
		
	}
	
	public boolean getPacket(DatagramSocket skt, InetAddress ip, int port){
		try {
			byte[] bay = new byte[516];
			//skt.setSoTimeout(200000);
			DatagramPacket pkt = new DatagramPacket(bay,bay.length,ip,port); 
			skt.receive(pkt);
			if(pkt.getLength() == 0){
				return false;
			}
			data = pkt.getData();
			setSize(pkt.getLength());
  		} catch (Exception e) {
			UtilTFTP.puts("Exception in getPacket()" + e.getMessage());
			return false;
		}
		
		return true;
	}
	

}
