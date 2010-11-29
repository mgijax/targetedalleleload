package org.jax.mgi.app.targetedalleleload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private static final String NUM_SUCCESS = "Successfully interpreted input record(s)";
	private static final String NUM_UNKNOWN_MUTATION = "Input record(s) with unknown mutation type skipped";
	private static final String NUM_UNKNOWN_PARENT = "Input record(s) with unknown parental cell line skipped";
	private static final String NUM_NOT_APPROPRIATE = "Input record(s) not appropriate for this provider, skipped";

	// The minimum length of a valid input record (including NL character).
	private static final int MIN_REC_LENGTH = 50;
	public static final String CSV_PATTERN = "\"([^\"]+?)\",?|([^,]+),?|,";
	private static Pattern csvRE;
	private TargetedAlleleLoadCfg cfg = null;
	private List allowedCelllines = null;
	private List knownCelllines = null;
	private String pipeline = null;
	protected DLALogger logger = null;

	/**
	 * Constructs a Sanger specific interpreter object
	 * 
	 * @throws MGIException
	 * @assumes Nothing
	 * @effects Nothing
	 */
	public SangerInterpreter() throws MGIException {
		csvRE = Pattern.compile(CSV_PATTERN);
		cfg = new TargetedAlleleLoadCfg();
		allowedCelllines = cfg.getAllowedCelllines();
		knownCelllines = cfg.getKnownCelllines();
		pipeline = cfg.getPipeline();
		this.logger = DLALogger.getInstance();
	}

	/**
	 * Parse one line. Split the line apart on comma and remove the double
	 * quotes from each piece. Also remove trailing whitespace
	 * 
	 * @return List of Strings
	 */
	public List parse(String line) {
		List list = new ArrayList();
		Matcher m = csvRE.matcher(line);

		while (m.find()) {
			String match = m.group();
			if (match == null)
				break;
			if (match.endsWith(",")) { // trim trailing ,
				match = match.substring(0, match.length() - 1);
			}
			if (match.startsWith("\"")) { // assume also ends with
				match = match.substring(1, match.length() - 1);
			}
			if (match.length() == 0)
				match = null;
			else
				match = match.trim();
			list.add(match);
		}
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
			qcStatistics.record("WARNING", "Poorly formated input record(s)");
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
		// 9 - Insertion point 1
		// 10 - Insertion point 2

		inputData.setGeneId(fields[0]);
		inputData.setBuild(fields[1]);
		inputData.setCassette(fields[2]);

		// Return all fields from the file, but include the pipeline
		// so the app can filter out the inappropriate ones
		inputData.setInputPipeline(fields[3]);

		inputData.setProjectId(fields[4]);
		inputData.setESCellName(fields[5]);
		inputData.setParentESCellName(fields[6]);
		inputData.setMutationType(fields[8]);
		
		// Check if this is a negative strand gene by comparing the
		// orientation of the coordinates
		List coords = getCoords(fields[9], fields[10]);
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
	 * @return 1 (for array element 1) if it is in the negative strand
	 *         else 0.   
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
		List list = parse(rec);
		String[] parts = (String[]) list.toArray(new String[0]);

		if (parts[0].equals("MGI ACCESSION ID")) {
			// Ignore header line
			return false;
		}
		if (parts[0].substring(0, 1).equals("#")) {
			// Ignore any comment lines which start with a "#" character
			return false;
		}
		if (!parts[3].replaceAll("\"", "").matches(pipeline)) {
			// Wrong project
			qcStatistics.record("SUMMARY", NUM_NOT_APPROPRIATE);

			// include these records for later QC
		}
		if (parts[6].indexOf(",") != -1) {
			// strangely formatted ES Cell (parental)
			qcStatistics.record("WARNING", NUM_UNKNOWN_PARENT);
			return false;
		}
		if (!parts[8].replaceAll("\"", "").matches(
				"Conditional|Targeted non-conditional|Deletion")) {
			// unknown mutation type
			qcStatistics.record("WARNING", NUM_UNKNOWN_MUTATION);
			return false;
		}

		// The first letter of the cell line ID indicates what lab created it
		String firstLetter = parts[5].substring(0, 1);

		if (!knownCelllines.contains(firstLetter)) {
			// A new provider!
			qcStatistics.record("ERROR", "Records with a new provider ("
					+ firstLetter + ")");
			logger.logInfo("Cell line record with a new provider (" + parts[5]
					+ ")");
			return false;
		}
		if (!allowedCelllines.contains(firstLetter)) {
			// This cell line is not appropriate for this provider
			qcStatistics.record("SUMMARY", NUM_NOT_APPROPRIATE);
			return false;
		}

		// Default action is to indicate this record as valid
		qcStatistics.record("SUMMARY", NUM_SUCCESS);
		return true;
	}
}
