package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.RecordFormatException;

/**
 * @is An object that knows how to create RegeneronAlleleInput objects from the
 *     Regeneron allele file
 * @has <UL>
 *      <LI>RegeneronAlleleInput object being created (which is then returned)
 *      </UL>
 * @does <UL>
 *       <LI>Parses a Regeneron Allele file record into a RegeneronAlleleInput
 *       object
 *       </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

public class RegeneronInterpreter extends KnockoutAlleleInterpreter {

	// The minimum length of a valid input record (including NL character).
	//
	private static final int MIN_REC_LENGTH = 50;
	private DLALogger logger = null;

	/**
	 * Constructs a Regeneron specific interpreter object
	 * 
	 * @assumes Nothing
	 * @effects Nothing
	 */
	public RegeneronInterpreter() {
		try {
			logger = DLALogger.getInstance();
		} catch (DLALoggingException e) {
			logger.logdInfo(e.getMessage(), true);
		}
	}

	/**
	 * Set all the attributes of the inputData object by parsing the given input
	 * record.
	 * 
	 * @assumes Nothing
	 * @effects Loads the clone object.
	 * @param rec
	 *            A record from the Regeneron allele input file
	 * @return An RegeneronAlleleInput object
	 * @throws RecordFormatException
	 */
	public Object interpret(String rec) throws MGIException {

		RegeneronAlleleInput inputData = new RegeneronAlleleInput();
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
		// The file is TAB delimited
		String[] fields = rec.split("\t");

		// Set the attributes of the inputData object using the fields parsed
		// from the input record.
		// 0 - getESCellName
		// 1 - Parental ES Cell
		// 2 - Mouse Strain
		// 3 - REGN Allele ID
		// 4 - Gene Symbol
		// 5 - MGI ID
		// 6 - Del Start
		// 7 - Del End
		// 8 - Del Size
		// 9 - Genome Build
		// 10 - Cassette
		//

		inputData.setESCellName(fields[0].trim());
		inputData.setParentalESCellName(fields[1].trim());
		inputData.setStrainName(fields[2].trim());
		inputData.setProjectId(fields[3].trim());
		inputData.setGeneSymbol(fields[4].trim());
		inputData.setGeneMgiId(fields[5].trim());

		Integer delStart = new Integer(fields[6].trim());
		Integer delEnd = new Integer(fields[7].trim());

		if (delStart.compareTo(delEnd) > 0) {
			Integer delTmp = delStart;
			delStart = delEnd;
			delEnd = delTmp;
		}

		inputData.setDelStart(delStart);
		inputData.setDelEnd(delEnd);

		inputData.setDelSize(new Integer(fields[8].trim()));
		inputData.setBuild(fields[9].trim());
		inputData.setCassette(fields[10].trim());

		// Return the populated inputData object.
		//
		return inputData;
	}

	/**
	 * Determines if the given input record is a valid record. A comment line is
	 * considered to be invalid. The header line is invalid.
	 * 
	 * @assumes Nothing
	 * @effects Nothing
	 * @param rec
	 *            A record from the Regeneron input file
	 * @return Indicator of whether the input record is valid (true) or not
	 *         (false)
	 */
	public boolean isValid(String rec) {
		// If the first character of the input record is a "#", it is a
		// comment and should be ignored.
		// The first line is a header starting with the string "CloneID"
		// and should be ignored.
		String[] parts = rec.split("\t");

		if (rec.substring(0, 1).equals("#")) {
			// Ignore comment lines which start with a "#" character
			return false;
		} else if (rec.substring(0, 7).equals("CloneID")) {
			// Ignore the header line which has the Column headers
			return false;
		} else if (parts[4].indexOf(",") >= 0) {
			// The gene symbol has a comma in it! Probably a case of
			// one allele knocked out multiple markers.
			qcStatistics.record("WARNING",
					"One allele, multiple markers record(s) skipped");
			return false;
		} else {
			String[] fields = rec.split("\t");
			if (fields[6].equals("0") || fields[7].equals("0")) {
				// These alleles will have a different note template, but it
				// might be helpful to know how many of them there are in the
				// input file.
				qcStatistics.record("WARNING",
						"Deletion start/end not reported");
				return true;
			}
			return true;
		}
	}
}
