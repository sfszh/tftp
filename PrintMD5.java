import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.*;
import java.util.*;

public class PrintMD5 {

	public static byte[] createChecksum(String filename) throws Exception {
		InputStream fis = new FileInputStream(filename);

		byte[] buffer = new byte[1024];
		MessageDigest complete = MessageDigest.getInstance("MD5");
		int numRead;

		do {
			numRead = fis.read(buffer);
			if (numRead > 0) {
				complete.update(buffer, 0, numRead);
			}
		} while (numRead != -1);

		fis.close();
		return complete.digest();
	}

	// see this How-to for a faster way to convert
	// a byte array to a HEX string
	public static String getMD5Checksum(String filename) throws Exception {
		byte[] b = createChecksum(filename);
		String result = "";

		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

	public static void main(String args[]) {
		try {
			String usage = "Usage: java md5 <file> or md5 <file> <md5>";
			if (args.length < 1 || args.length > 2) {
				System.err.println(usage);
				System.exit(1);
			} else if (args.length == 1) {
				System.out.println(getMD5Checksum(args[1]));
			} else if(args.length == 2){
				if (getMD5Checksum(args[1]) == new String(args[2])){
					System.out.println("Same!");
				}else{
					System.out.println("Not the Same!");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
