import java.io.FileNotFoundException;
import java.util.Scanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

public class vmsim {
	
	//global contents of tracefile
	static ArrayList<Long> addresses = new ArrayList<Long>();
	static ArrayList<Character> modes = new ArrayList<Character>();
	static PageTable pageTable;
	
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
		String algo = "opt"; //get this from command line
		int frames = 8; //get this from command line
		
		if (algo.equals("opt")) {
			//only preprocess the tracefile if we are running opt
			pageTable = new PageTable(frames, preprocess(), addresses);
		}
		else {
			pageTable = new PageTable(frames, null, addresses);
		}
		int faults = 0;
		int writes = 0;
//		
		for (int i = 0; i < addresses.size(); i++) {
			if (pageTable.search(addresses.get(i)) == -1) { //page fault
				faults++;
				if (pageTable.add(addresses.get(i), algo, i, modes.get(i))) { //try to add to page table
					//System.out.println("added");
					//stats
					writes++;
				}
			} else {
				//success, found the frame
			}
//			System.out.println(i);
//			break;
		}
		System.out.printf("Page Faults: %d.\n", faults);
		System.out.printf("Writes to disk: %d.\n", writes);
		
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
	
	//privates
	private int frames;
	/**
	 * map page number to line number
	 */
	private HashMap<Integer, Integer> ranking; //map page number to line number
	private ArrayList<Integer> framesAvailable;
	private ArrayList<Long> addresses;
	/**
	 * map frame number to page number
	 */
	private HashMap<Integer, Integer> mainMemory; //map frame number to page number
	
	public PageTable(int maxframes, HashMap<Integer, Integer> rank, ArrayList<Long> adr) {
		table = new SecondLevel[1024];
		for (int i = 0; i < 1024; i++) {
			table[i] = new SecondLevel();
		}
		frames = maxframes;
		ranking = rank;
		framesAvailable = new ArrayList<Integer>();
		addresses = adr;
		for (int i = 0; i < maxframes; i++) {
			framesAvailable.add(i);
		}
		mainMemory = new HashMap<Integer, Integer>();
	}
	
	/**
	 * 
	 * @return -1 if frame not in page table
	 */
	public int search(long adr) {
		int frame = -1;
		
		//mask the address to get the different addresses
		adr = adr >>> 12;
		int upperbits = (int) (adr & 0x00000000000ffc00);
		upperbits = upperbits >>> 10;
		int lowerbits = (int) (adr & 0x00000000000003ff);

		SecondLevel second = table[upperbits];
		if (second != null) {
			PageTableEntry entry = second.table[lowerbits];
			if (entry != null) {
				//found the page in the page table
				frame = entry.frameNumber;
			}
		}
		return frame;
	}
	
	/**
	 * Add an address. Run page replacement algorithm if necessary. 
	 * @param adr
	 * @return true if there was a write to disk, nonzero for success
	 */
	public boolean add(long adr, String algo, int currentLine, char mode) {
		boolean retval = false;
		int frame = 0;
		if (framesAvailable.size() == 0) {
			switch (algo) {
			case "opt":
				retval = opt(currentLine);
				break;
			case "clock":
				clock();
				break;
			case "aging": 
				aging();
				break;
			case "work":
				workingSetClock();
				break;
			}
		} else {
			//look for a frame to use
			frame = framesAvailable.get(0);
//			System.out.println(frame);
			framesAvailable.remove(0); //that frame is no longer available
//			System.out.println(framesAvailable);
			//make new entry
			PageTableEntry newEntry = new PageTableEntry(frame, (mode == 'W'), true/*, false*/);
			
			//mask bits to get upper and lower indices
			adr = adr >>> 12;
			int upperbits = (int) (adr & 0x00000000000ffc00);
			upperbits = upperbits >>> 10;
			int lowerbits = (int) (adr & 0x00000000000003ff);
			
			SecondLevel second = table[upperbits];
			if (second != null) {
				//add it to the table
				second.table[lowerbits] = newEntry;
				mainMemory.put(frame, (int)adr);
			}
		}
		
		return retval;
	}
	
	/**
	 * 
	 * @param currentLine
	 * @return true if there was a write to disk
	 */
	public boolean opt(int currentLine) {
		boolean retval = false;
		/*Collection<Integer> values = ranking.values();
		//search for largest number in values greater than currentLine
		int max = currentLine;
		for (int val : values) {
			if (val > max) max = val;
			//max should now be the line number of the farthest page use
		}
		
		//get the address associated with that line
		long adr = addresses.get(max);
		*/
		
		//search through page table
		
		//find the page that isn't used until the latest
		//get all pages that have a frame
		Collection<Integer> pagenums = mainMemory.values();
		//search those page numbers in ranking
		int max = currentLine;
		int cur = 0;
		for (int p : pagenums) {
			cur = ranking.get(p);
//			System.out.println(cur);
			if (cur > max) max = cur;
			//max is the highest line number
		}
		
		//special case where none of the pages in the page table are going to be used again in the future
		if (max == currentLine) {
			max = ranking.get(pagenums.toArray()[0]);
		}
		
		long adr = addresses.get(max);
		//now we know which address, shift to get page number
		adr = adr >>> 12;
//		adr is now the 20bit page number
		
		//evict that frame
		int upperbits = (int) (adr & 0x00000000000ffc00);
		upperbits = upperbits >>> 10;
		int lowerbits = (int) (adr & 0x00000000000003ff);
		
		SecondLevel second = table[upperbits];
		if (second != null) {
			PageTableEntry entry = second.table[lowerbits];
			retval = entry.dirty;
			framesAvailable.add(entry.frameNumber);
			mainMemory.remove(entry.frameNumber);
			second.table[lowerbits] = null;
		}
		return retval;
	}	

	public void clock() {
		
	}
	
	public void aging() {
		
	}
	
	public void workingSetClock() {
		
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
		for (int i = 0; i < 1024; i++) {
			table[i] = null;
		}
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
	//public boolean valid;
	
	
	/**
	 * 
	 * @param f
	 * @param d
	 * @param r
	 * @param v
	 */
	public PageTableEntry(int f, boolean d, boolean r/*, boolean v*/) {
		frameNumber = f;
		dirty = d;
		referenced = r;
		//valid = v;
	}
}