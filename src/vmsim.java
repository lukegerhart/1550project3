import java.io.FileNotFoundException;
import java.util.Scanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import javax.swing.event.ListSelectionEvent;

public class vmsim {
	
	//global contents of tracefile
	static ArrayList<Long> addresses = new ArrayList<Long>();
	static ArrayList<Character> modes = new ArrayList<Character>();
	static PageTable pageTable;
	
	public static void main(String[] args) {
		if (args.length < 5) {
			term();
		}
		//first parse command line input
		//vmsim -n <numframes> -a <opt|clock|aging|work> [-r <refresh>] [-t <tau>] <tracefile>
		//choose which of 4 algorithms to run
		//determine flags are all there
		//get tracefile
		//open tracefile
		for (String a : args) {
			
		}
		int frames = 0, refresh = 0, tau = 0;
		String algo = "",tracefilePath = "";
		try {
			frames = Integer.valueOf(args[1]);
			algo = args[3];
			if (algo.equals("aging")) {
				if (args.length < 7) term();
				refresh = Integer.valueOf(args[5]);		
			} else if (algo.equals("work")) {
				if (args.length < 9) term();
				refresh = Integer.valueOf(args[5]);	
				tau = Integer.valueOf(args[7]);		
			}
			tracefilePath = args[args.length-1];
		} catch (NumberFormatException nfe) {
			term();
		}
		
		File trace = null;
		Scanner traceScanner = null;
		try {
			trace = new File(tracefilePath);
			traceScanner = new Scanner(trace);
		} catch (FileNotFoundException fnfe) {
			System.out.println("File not found.");
			System.exit(0);
		}
		
		//add the contents of the file to an ArrayList
		String temp;
		while (traceScanner.hasNext()) {
			temp = traceScanner.nextLine();
			addresses.add(getAddress(temp));
			modes.add(getMode(temp));
		}
//		String algo = "clock"; //get this from command line
//		int frames = 8; //get this from command line
//		int refresh = 8;
		if (algo.equals("opt")) {
			System.out.println("running opt");
			//only preprocess the tracefile if we are running opt
			pageTable = new PageTable(frames, preprocess(), addresses);
		}
		else {
			pageTable = new PageTable(frames, null, addresses);
		}

		int faults = 0;
		int writes = 0;
		for (int i = 0; i < addresses.size(); i++) {
			if (pageTable.search(addresses.get(i), modes.get(i), i)) { //page fault
				//System.out.printf("Line %d page fault", i+1);
				faults++;
				if (pageTable.add(addresses.get(i), algo, i, modes.get(i), refresh)) { //try to add to page table
					//System.out.println("added");
					//stats
					writes++;
				}
			} else {
				//success, found the frame
			}
//			System.out.println();
//			if (i == 20) break;
		}
		System.out.printf("Algorithm: %s.\n", algo);
		System.out.printf("Number of frames: %d.\n", frames);
		System.out.printf("Total memory accesses: %d.\n", addresses.size());
		System.out.printf("Total page faults: %d.\n", faults);
		System.out.printf("Total writes to disk: %d.\n", writes);
		
	}
	
	public static void term() {
		System.out.println("Usage: java " + "vmsim -n <numframes> -a <opt|clock|aging|work> [-r <refresh>] [-t <tau>] <tracefile>");
		System.exit(0);
	}
	
	/**
	 * Preprocess the tracefile.
	 * Use a HashMap where the key is the page number, and the value is its line number (time simulation).
	 * Since a HashMap overwrites identical keys, it will contain the last use of each page.
	 * @return
	 */
	public static HashMap<Integer, Integer> preprocess() {
		HashMap<Integer, Integer> map = new HashMap<Integer, Integer>(); 
		for (int i = 0; i < addresses.size(); i++) {
			long adr = addresses.get(i);
			adr = adr >>> 12;
			map.put((int)adr, i); //put (pageNumber, lineNumber) in map
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
	private ArrayList<Long> addresses;
	private ArrayList<Integer> framesAvailable;
	
	//for opt
	/**
	 * map page number to line number
	 */
	private HashMap<Integer, Integer> ranking; //map page number to line number
	/**
	 * map frame number to page number
	 */
	private HashMap<Integer, Integer> mainMemory; //map frame number to page number
	
	//for clock algo
	private int hand;
	private ArrayList<Integer> circle;
	
	//for aging
	/**
	 * map page number to aging byte (represented using short because of no unsigned)
	 */
	private HashMap<Integer, Short> age;
	
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
		hand = 0;
		circle = new ArrayList<Integer>();
		age = new HashMap<Integer, Short>(8);
	}
	
	public PageTableEntry lookup(long adr) {
		PageTableEntry entry = null;

		int upperbits = (int) (adr & 0x00000000000ffc00);
		upperbits = upperbits >>> 10;
		int lowerbits = (int) (adr & 0x00000000000003ff);
		
		SecondLevel second = table[upperbits];
		if (second != null) {
			entry = second.table[lowerbits];
		}
		return entry;
	}
	
	/**
	 * 
	 * @return -1 if frame not in page table
	 */
	public boolean search(long adr, char mode, int currentLine) {
		boolean retval = true;
		
		//mask the address to get the different addresses
		adr = adr >>> 12;
		PageTableEntry entry = lookup(adr);
		if (entry != null) {
//			frame = entry.frameNumber;
			retval = false;
			if (!entry.dirty) {
				//entry.dirty = (mode == 'W');
				table[(int) ((adr&0xffc00)>>>10)].table[(int)(adr&0x3ff)].dirty = (mode == 'W');
			}
			if (!entry.referenced) {
//				entry.referenced = true;
				table[(int) ((adr&0xffc00)>>>10)].table[(int)(adr&0x3ff)].referenced = true;
			}
		}
		return retval;
	}
	
	/**
	 * Add an address. Run page replacement algorithm if necessary. 
	 * @param adr
	 * @return true if there was a write to disk
	 */
	public boolean add(long adr, String algo, int currentLine, char mode, int refresh) {
		boolean retval = false;
		int frame = 0;
		if (framesAvailable.size() == 0) {
			switch (algo) {
			case "opt":
				retval = opt(currentLine);
				break;
			case "clock":
				retval = clock();
				break;
			case "aging": 
				aging();
				break;
			case "work":
				workingSetClock();
				break;
			}
			//retval = true;
		} //else {
			//look for a frame to use
			frame = framesAvailable.get(0);
//			System.out.println(frame);
			framesAvailable.remove(0); //that frame is no longer available
//			System.out.println(framesAvailable);
			//make new entry
			PageTableEntry newEntry = new PageTableEntry(frame, (mode == 'W'), true);
			
			//mask bits to get upper and lower indices
			adr = adr >>> 12;
			int upperbits = (int) (adr & 0x00000000000ffc00);
			upperbits = upperbits >>> 10;
			int lowerbits = (int) (adr & 0x00000000000003ff);
			
			SecondLevel second = table[upperbits];
			if (second != null) {
				//add it to the table
				second.table[lowerbits] = newEntry;
				if (algo.equals("opt")) mainMemory.put(frame, (int)adr);
				circle.add((int)adr);
				age.put((int)adr, (short) 128);
			}
		//}
		
		return retval;
	}
	
	/**
	 * 
	 * @param currentLine
	 * @return the page number that was written out
	 */
	public boolean opt(int currentLine) {
		
		/*
		 * I don't know why this doesn't work.
		 * I feel like it should work.
		 * I used a HashMap to find the last line number of the tracefile where each 
		 * page number is referenced. 
		 * When opt is called, I get which pages are in each frame, and get their line numbers
		 * from the HashMap.
		 * I sort the line numbers, and take the highest one, or if there is a page that will 
		 * be never used again, I use that.
		 * Then I evict the page. 
		 * But there are way too many page faults and writes. 
		 */
		boolean retval = false;
		Collection<Integer> pagenums = mainMemory.values();
		//search those page numbers in ranking
		
		ArrayList<Integer> lines = new ArrayList<Integer>();
		for (int p : pagenums) {
			lines.add(ranking.get(p));
		}
		lines.sort(null);

		int line = 0;
		if (lines.get(0) < currentLine) {
			line = lines.get(0);
		} else {
			line = lines.get(lines.size()-1);
		}

		long adr = addresses.get(line);
		//now we know which address, shift to get page number
		adr = adr >>> 12;

//		evict that frame
		PageTableEntry entry = evict(adr);
		retval = entry.dirty;
		framesAvailable.add(entry.frameNumber);
		mainMemory.remove(entry.frameNumber);
		return retval;
	}	

	public boolean clock() {
		//page fault
		PageTableEntry entry = null;
		while (true) {
			int adr = circle.get(hand);
			entry = lookup(adr);
			if (entry.referenced) {
				//set referenced bit to false;
				entry.referenced = false;
			} else {
				//evict this page
				evict(adr);
				circle.remove(hand);
				framesAvailable.add(entry.frameNumber);
				return entry.dirty;
			}
			hand = (hand+1) % frames;
		}
	}
	
	public void aging() {
		
	}
	
	public void workingSetClock() {
		
	}
	
	
	public PageTableEntry evict(long adr) {
		PageTableEntry entry = null;
		int upperbits = (int) (adr & 0x00000000000ffc00);
		upperbits = upperbits >>> 10;
		int lowerbits = (int) (adr & 0x00000000000003ff);
		
		SecondLevel second = table[upperbits];
		if (second != null) {
			entry = second.table[lowerbits];
			second.table[lowerbits] = null;
		}
		return entry;
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
	
	
	/**
	 * 
	 * @param f
	 * @param d
	 * @param r
	 */
	public PageTableEntry(int f, boolean d, boolean r) {
		frameNumber = f;
		dirty = d;
		referenced = r;
	}
}