import java.io.FileNotFoundException;
import java.util.Scanner;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class vmsim {
	
	//global contents of tracefile
	static ArrayList<Long> addresses = new ArrayList<Long>();
	static ArrayList<Character> modes = new ArrayList<Character>();
	static PageTable pageTable = new PageTable();
	static int frames;
	
	public static void main(String[] args) {
		//first parse command line input
		//vmsim –n <numframes> -a <opt|clock|aging|work> [-r <refresh>] [-t <tau>] <tracefile>
		//choose which of 4 algorithms to run
		//determine flags are all there
		//get tracefile
		//open tracefile
		String tracefilePath = "gcc.trace";
		File trace = null;
		Scanner traceScanner = null;
		try {
			trace = new File(tracefilePath);
			traceScanner = new Scanner(trace);
		} catch (FileNotFoundException fnfe) {}
		
		//add the contents of the file to an ArrayList
		String temp;
		while (traceScanner.hasNext()) {
			temp = traceScanner.nextLine();
			addresses.add(getAddress(temp));
			modes.add(getMode(temp));
		}
		int algo = 0;
		for (int i = 0; i < addresses.size(); i++) {
			if (pageTable.search(addresses.get(i)) == -1) {
				//page fault
				if (pageTable.add(addresses.get(i)) == 0) {
					switch (algo) {
					case 1:
						opt();
						break;
					case 2:
						clock();
						break;
					case 3:
						aging();
						break;
					case 4:
						workingSetClock();
						break;
					}
				}
			} else {
				//add to page table
			}
		}
		
	}
	
	public static void opt() {
		HashMap<Integer, Integer> ranking = preprocess();
		
	}	

	public static void clock() {
		
	}
	
	public static void aging() {
		
	}
	
	public static void workingSetClock() {
		
	}
	
	/**
	 * Preprocess the tracefile.
	 * Use a HashMap where the key is the page number, and the value is its line number (time simulation).
	 * Since a HashMap overwrites identical keys, it will contain the last use of each page.
	 * @return
	 */
	public static HashMap<Integer, Integer> preprocess() {
		HashMap<Integer, Integer> map = new HashMap<Integer, Integer>(); 
		int adrshifted = 0;
		for (int i = 0; i < addresses.size(); i++) {
			adrshifted = (int)(addresses.get(i) >>> 12);
			map.put(adrshifted, i);
		}
		return map;
	}
	
	/**
	 * Get the last char of the string.
	 * @param address the hex string (without 0x)
	 * @return a char that represents the mode (R/W)
	 */
	public static char getMode(String address) {
		return address.charAt(address.length()-1);
	}
	
	/**
	 * Convert from the string representation of the address to a long.
	 * @param address the hex string (without 0x)
	 * @return the value of the hex string in a long
	 */
	public static long getAddress(String address) {
		String hex = "0x".concat(address.substring(0, 8));
		return Long.decode(hex);
	}
}

/**
 * PageTable class has an array of size 1024. It is an array of SecondLevel objects.
 * @author Luke
 *
 */
class PageTable {
	//1st level 
	public SecondLevel[] table;
	
	public PageTable() {
		table = new SecondLevel[1024];
	}
	
	/**
	 * 
	 * @return -1 if frame not in page table
	 */
	public int search(long adr) {
		int frame = -1;
		//mask the address to get the different addresses
		long upperbits = (adr & 0x00000000ffc00000) >>> 22;
		long lowerbits = (adr & 0x00000000003ff000) >>> 12;
		//long offset =     adr & 0x0000000000000fff;
		
		SecondLevel second = table[(int)upperbits];
		if (second != null) {
			PageTableEntry entry = second.table[(int)lowerbits];
			if (entry != null) {
				//found the page in the page table
				frame = entry.frameNumber;
			}
		}
		return frame;
	}
	
	/**
	 * Add an address
	 * @param adr
	 * @return 0 for failure, nonzero for success
	 */
	public int add(long adr) {
		int retval = 0;
		PageTableEntry newEntry = new PageTableEntry(0, false, false, false);
		return retval;
	}
}

/**
 * This represents the second level of the 2-level page table.
 * Each SecondLevel object has an array of 1024 PageTableEntry objects.
 * @author Luke
 *
 */
class SecondLevel {
	//second level table
	public PageTableEntry[] table;
	
	public SecondLevel() {
		table = new PageTableEntry[1024];
	}
	
}

/**
 * Page Table Entry class.
 * Contains int frameNumber, which is the frame number in memory.
 * Three booleans representing the dirty, referenced and valid bits. 
 * @author Luke
 *
 */
class PageTableEntry {
	
	public int frameNumber;
	public boolean dirty;
	public boolean referenced;
	public boolean valid;
	
	
	/**
	 * 
	 * @param f
	 * @param d
	 * @param r
	 * @param v
	 */
	public PageTableEntry(int f, boolean d, boolean r, boolean v) {
		frameNumber = f;
		dirty = d;
		referenced = r;
		valid = v;
	}
}