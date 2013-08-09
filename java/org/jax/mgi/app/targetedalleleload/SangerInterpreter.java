package org.jax.mgi.app.targetedalleleload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.RecordFormatException;

/**
 * @is An object that knows how to Interpret the Sanger allele file
 * @has
 * @does Interprets a Sanger Allele input record into it's various parts
 * @company The Jackson Laboratory
 * @author jmason
 */

public class SangerInterpreter extends KnockoutAlleleInterpreter {

	// QC string constants
	protected static final String NUM_UNKNOWN_MUTATION = "Input record(s) with unknown mutation type skipped";
	protected static final String NUM_UNKNOWN_PARENT = "Input record(s) with unknown parental cell line skipped";
	protected static final Set alleleTypes = new HashSet();
	static {
		alleleTypes.add("Conditional Ready");
		alleleTypes.add("Targeted Non Conditional");
		alleleTypes.add("Deletion");
	}

	// The minimum length of a valid input record (including NL character).
	protected static final int MIN_REC_LENGTH = 50;
	protected TargetedAlleleLoadCfg cfg = null;
	protected List allowedCelllines = null;
	protected List knownCelllines = null;
	protected String pipeline = null;
	protected DLALogger logger = null;

	/**
	 * Constructs a Sanger specific interpreter object
	 * 
	 * @throws MGIException
	 * @assumes Nothing
	 * @effects Nothing
	 */
	public SangerInterpreter() throws MGIException {
		cfg = new TargetedAlleleLoadCfg();
		allowedCelllines = cfg.getAllowedCelllines();
		knownCelllines = cfg.getKnownCelllines();
		pipeline = cfg.getPipeline();
		this.logger = DLALogger.getInstance();
	}

	public SangerInterpreter(
			List allowedCelllines, 
			List knownCelllines, 
			String pipeline) 
	throws MGIException {
		cfg = new TargetedAlleleLoadCfg();
		this.allowedCelllines = allowedCelllines;
		this.knownCelllines = knownCelllines;
		this.pipeline = pipeline;
		this.logger = DLALogger.getInstance();
	}

	/**
	 * Parse one line. Split the line apart on tab character
	 * 
	 * @return List of Strings
	 */
	public List parse(String line) {
		List list = Arrays.asList(line.replaceAll("\\r|\\n", "").split("\t"));
		return list;
	}

	/**
	 * Set all the attributes of the inputData object by parsing the given input
	 * record.
	 * 
	 * @assumes Nothing
	 * @effects Loads the clone object.
	 * @param rec
	 *            A record from the Sanger allele input file
	 * @return An RegeneronAlleleInput object
	 * @throws RecordFormatException
	 */
	public Object interpret(String rec) throws MGIException {
		SangerAlleleInput inputData = new SangerAlleleInput();
		qcStatistics.record("SUMMARY", "Number of input records");

		// Throw an exception if the input record does not meet the minimum
		// length required to extract the fields.
		//
		if (rec.length() < MIN_REC_LENGTH) {
			RecordFormatException e = new RecordFormatException();
			e.bindRecord(rec);
			qcStatistics.record("WARNING", "Number of incorrectly formatted input records");
			throw e;
		}

		// Get fields from the input record
		// The file is a CSV file
		List list = parse(rec);
		String[] fields = (String[]) list.toArray(new String[0]);

		// Set the attributes of the inputData object using the fields parsed
		// from the input record.
		// 0 - Gene ID
		// 1 - Genome Build
		// 2 - Cassette
		// 3 - Project (KOMP, EUCOMM, NorCOMM)
		// 4 - Project ID
		// 5 - Mutant ES cell line ID
		// 6 - Parent ES cell line name
		// 7 - Sanger allele name
		// 8 - Mutation type
		// 9 - Mutation sub type
		// 10 - Insertion point 1
		// 11 - Insertion point 2
		// 12 - loxp start
		// 13 - loxp end

		inputData.setGeneId(fields[0]);
		inputData.setBuild(fields[1]);
		inputData.setCassette(fields[2]);

		// Return all fields from the file, but include the pipeline
		// so the app can filter out the inappropriate ones
		inputData.setInputPipeline(fields[3]);

		inputData.setProjectId(fields[4]);
		inputData.setESCellName(fields[5]);
		inputData.setParentESCellName(fields[6]);
		
		if(fields[8].equals("Conditional Ready")) {
			inputData.setMutationType("Conditional");
		} else if (fields[8].equals("Targeted Non Conditional")) {
			inputData.setMutationType("Targeted non-conditional");
		} else if (fields[8].equals("Deletion")) {
			inputData.setMutationType("Deletion");
		}
		inputData.setMutationSubType(fields[9]); 
		// Combine the coordinate pairs
		String c1 = fields[10] + "-" + fields[11];
		String c2 = "-";
		if (fields.length == 14) {
		    c2 = fields[12] + "-" + fields[13];
		}
                              
		
		List coords = getCoords(c1, c2);
		inputData.setLocus1((String) coords.get(0));
		inputData.setLocus2((String) coords.get(1));

		// Return the populated inputData object.
		return inputData;
	}

	/**
	 * Determine if the record encodes a positive of negative strand
	 * gene (negative strand genes are reported with the genomic 
	 * coordinates swapped)
	 * @param coordinates the dash separated coordinates of the first feature
	 * @return list of coordinates in the correct order
	 */
	private List getCoords(String locus1, String locus2) {
		List genomic = new ArrayList();
		String[] parts1 = locus1.split("-");
		
		if (locus2.equals("-")) {
			// If the second coordinate pair field is a dash, then this 
			// record represents a deletion allele. the genomic 
			// coordinates are the two provided in the first 
			// coordinate pair
			genomic.add(0, parts1[0]);
			genomic.add(1, parts1[1]);			
		} else {
			// This is not a deletion allele, so figure out the correct
			// min/max of the coordinates provided.
			String[] parts2 = locus2.split("-");
		
			List coords = new ArrayList();
			coords.add(new Integer(parts1[0]));
			coords.add(new Integer(parts1[1]));
			coords.add(new Integer(parts2[0]));
			coords.add(new Integer(parts2[1]));
			Integer largest = (Integer) Collections.max(coords);
			Integer smallest = (Integer) Collections.min(coords);

			// default to positive strand gene
			genomic.add(0, smallest.toString());
			genomic.add(1, largest.toString());

			// check for negative strand gene
			int first = Integer.valueOf(parts1[0]).intValue();
			int second = Integer.valueOf(parts2[0]).intValue();
			if(first > second) {
				genomic.add(0, largest.toString());
				genomic.add(1, smallest.toString());
			}
		}
		return genomic;
	}

	/**
	 * Determines if the given input record is a valid record. A comment line is
	 * considered to be invalid. The header line is invalid.
	 * 
	 * @assumes Nothing
	 * @effects Nothing
	 * @param rec
	 *            A record from the Sanger input file
	 * @return Indicator of whether the input record is valid (true) or not
	 *         (false)
	 */
	public boolean isValid(String rec) {
		try {
			List list = parse(rec);
			String[] parts = (String[]) list.toArray(new String[0]);

			if (parts[0].equals("")) {
				// Skip any missing ES Cell IDs 
				return false;
				
			}
			if (parts[0].equals("MGI ACCESSION ID")) {
				// Ignore header line
				return false;
			}
			if (parts[0].substring(0, 1).equals("#")) {
				// Ignore any comment lines which start with a "#" character
				return false;
			}
			try {
				Integer.parseInt(parts[4]);
			} catch (NumberFormatException e) {
				// Sanger IKMC project IDs are Integers, but this record
				// is not an integer
				return false;
			}
			if (parts[3].replaceAll("\"", "").indexOf(pipeline) == -1) {
				// Wrong project
				return false;
			}
			if (parts[6].indexOf(",") != -1) {
				// strangely formatted ES Cell (parental)
				logger.logdInfo("Unknown parental cell line: " + parts[6], false);
				qcStatistics.record("WARNING", NUM_UNKNOWN_PARENT);
				return false;
			}
			if (!alleleTypes.contains(parts[8])) {
				// unknown mutation type
				logger.logdInfo("Unknown mutation type: " + parts[8], false);
				qcStatistics.record("WARNING", NUM_UNKNOWN_MUTATION);
				return false;
			}

			// The first letter of the cell line ID indicates what lab created it
			if (parts[5].length() < 1) {
				return false;
			}
			String firstLetter = parts[5].substring(0, 1);

			if (!knownCelllines.contains(firstLetter)) {
				// A new provider!
				qcStatistics.record("ERROR", 
						"Records with a new provider (" +
						firstLetter + ")");
				logger.logcInfo(
						"Cell line record with a new provider (" + 
						parts[5] + ")", false);
				return false;
			}
			if (!allowedCelllines.contains(firstLetter)) {
				return false;
			}

			try {
				if(Integer.valueOf(parts[10]) == null) {
				    return false;}
				if(Integer.valueOf(parts[11]) == null) {
				    return false;}
			} catch (NumberFormatException e) {
				return false;
			}
			
			if (parts.length > 12) {
				if(parts.length == 13 ) {
					// Missing one of a pair of coords
					return false;
				}
				if ((parts[12].length() < 1 && parts[13].length() > 1) ||
					(parts[12].length() > 1 && parts[13].length() < 1)
					) {
						return false;
				} else {
					try {
						if(Integer.valueOf(parts[12]) == null) {
						    return false;}
						if(Integer.valueOf(parts[13]) == null) {
						    return false;}
					} catch (NumberFormatException e) {
						return false;
					}
				}
			}

			// Default action is to indicate this record as valid
			return true;			
		} catch (Exception e) {
			logger.logdInfo("Malformed record "+rec, false);
			return false;
		}
	}
}
