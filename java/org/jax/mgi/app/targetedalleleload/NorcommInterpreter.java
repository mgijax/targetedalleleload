package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.RecordFormatException;

/**
 * An object that knows how to create NorcommAlleleInput objects from the
 * NorCOMM allele file
 */

public class NorcommInterpreter extends KnockoutAlleleInterpreter {

	// The minimum length of a valid input record (including NL character).
	//
	private static final int MIN_REC_LENGTH = 50;
	private TargetedAlleleLoadCfg cfg;

	/**
	 * Constructs a NorCOMM specific interpreter object
	 * 
	 * @assumes Nothing
	 * @effects Nothing
	 */
	public NorcommInterpreter() throws MGIException {
		cfg = new TargetedAlleleLoadCfg();
	}

	/**
	 * Set all the attributes of the inputData object by parsing the 
	 * given input record.
	 * @param rec record from the NorCOMM allele input file
	 * @return A KnockoutAlleleInput object
	 * @throws RecordFormatException
	 */
	public Object interpret(String rec) throws MGIException {

		NorcommAlleleInput in = new NorcommAlleleInput();
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

		// Get fields from the input record (TAB delimit) and trim off
		// any whitespace
		String[] fields = rec.split("\t");
		for (int i=0; i<fields.length; i++) {
			fields[i] = fields[i].trim();
		}

		// Set the attributes of the inputData object using the fields parsed
		// from the input record.
		// 0 - Gene MGI ID
		// 1 - IKMC Project ID
		// 2 - es cell ID
		// 3 - pipeline (NorCOMM)
		// 4 - mutation type (unused)
		// 5 - mutation subtype (unused)
		// 6 - parental (always C2)
		// 7 - cassette
		// 8 - start
		// 9 - end
		// 10 - chromosome
		// 11 - strand
		// 12 - genome build

		in.setGeneId(fields[0]);
		in.setProjectId(fields[1]);
		in.setMutantCellLine(fields[2]);
		in.setInputPipeline(cfg.getPipeline()); // Get pipeline from cfg
		in.setParentCellLine(fields[6]);
		in.setCassette(fields[7]);
		Integer start = new Integer(fields[8]);
		Integer end = new Integer(fields[9]);
		Integer size = new Integer(Math.abs(start.intValue() - end.intValue()));
		in.setStart(start);
		in.setEnd(end);
		in.setDelSize(size);
		in.setChromosome(fields[10]);
		in.setStrand(fields[11]);
		in.setBuild(fields[12]);


		return in;
	}

	/**
	 * Determines if the given input record is a valid record. A comment 
	 * line is considered to be invalid. The header line is invalid.
	 * 
	 * @param rec A record from the NorCOMM input file
	 * @return true if input record is valid, false if not
	 */
	public boolean isValid(String rec) {

		if (rec.length() < MIN_REC_LENGTH) {
			return false;
		}

		return true;
	}
}
