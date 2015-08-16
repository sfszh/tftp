import java.util.*;
public class blockTest{
    
    public static void main(String[] args){
	byte bay[] = new byte [2];
	bay[0] = (byte)1;
	bay[1] = (byte)128;

	int lowBlock = bay[1] & 0xff;
	int highBlock = bay[0];
	highBlock = highBlock << 8;
	int total = highBlock | lowBlock;
	total = total;
	System.out.println("Low block: " + lowBlock);
	System.out.println ("int of bay1 :" + (int)bay[1]);
	System.out.println("Total: " + total);

	int block2 = 128; //101100011
	System.out.println("Block no is: " + block2);
	int mask = 0xFF;  //11111111
	int block3 = block2 & mask;
	//bay[2] = block3;
	

	block2 = block2 >>> 8; //00000001
	
	System.out.println("b2: " + block2); 
	System.out.println("b3: "+ block3);
	System.out.println ("put them together");
	int block = block2 << 8;
	System.out.println("block shifted < 8: " + block2);
	block = block | block3;
	System.out.println (block);
	
    }
}