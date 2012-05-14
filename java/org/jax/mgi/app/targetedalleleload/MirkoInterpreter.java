package org.jax.mgi.app.targetedalleleload;

import java.util.HashSet;
import java.util.Set;

import org.jax.mgi.app.targetedalleleload.lookups.LookupMirkoClusterByCellLine;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.RecordFormatException;

public class MirkoInterpreter extends KnockoutAlleleInterpreter {
	// QC string constants
	protected static final String NUM_CLUSTER_ESC = "Input record(s) that belong to a cluster skipped";
	protected static final String NUM_UNKNOWN_MUTATION = "Input record(s) with unknown mutation type skipped";
	protected static final String NUM_UNKNOWN_PARENT = "Input record(s) with unknown parental cell line skipped";
	protected static final String NUM_MISSING_ESC = "Input record(s) missing ES Cell name skipped";

	protected static final Set alleleTypes = new HashSet();
	static {
		alleleTypes.add("deletion");
	}

	protected TargetedAlleleLoadCfg cfg = null;
	protected String pipeline = null;
	protected DLALogger logger = null;

	// The minimum length of a valid input record (including NL character).
	protected static final int MIN_REC_LENGTH = 10;

	LookupMirkoClusterByCellLine lookupMirkoClusterByCellLine;

	public MirkoInterpreter() throws MGIException {
		cfg = new TargetedAlleleLoadCfg();
		pipeline = cfg.getPipeline();
		this.logger = DLALogger.getInstance();
		lookupMirkoClusterByCellLine = LookupMirkoClusterByCellLine.getInstance();
	}

	/**
	 * Parse one line. Split the line apart on tab character
	 * 
	 * @return array of Strings
	 */
	public String[] parse(String line) {
		String prepared = line.replaceAll("\\r|\\n", "");
		return prepared.split("\t");
	}

	public Object interpret(String rec) throws MGIException {
		SangerAlleleInput inputData = new SangerAlleleInput();
		qcStatistics.record("SUMMARY", "Number of input records");

		// Throw an exception if the input record does not meet the 
		// minimum length required to extract the fields.
		if (rec.length() < MIN_REC_LENGTH) {
			RecordFormatException e = new RecordFormatException();
			e.bindRecord(rec);
			qcStatistics.record("WARNING",
					"Number of incorrectly formatted input records");
			throw e;
		}

		// Get fields from the input record
		String[] fields = parse(rec);

		// Set the attributes of the inputData object using the fields 
		// parsed from the input record
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

		if (fields[8].equals("conditional_ready")) {
			inputData.setMutationType("Conditional");
		} else if (fields[8].equals("targeted_non_conditional")) {
			inputData.setMutationType("Targeted non-conditional");
		} else if (fields[8].equals("deletion")) {
			inputData.setMutationType("Deletion");
		}

		inputData.setLocus1(fields[9]);
		inputData.setLocus2(fields[10]);

		// Return the populated inputData object.
		return inputData;
	}

	public boolean isValid(String rec) {

		try {
			String[] parts = parse(rec);

			if (parts[0].equals("")) {
				// Skip any missing MGI IDs
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
				// The project IDs look like mirKOxxxx where xxxx
				// is an integer
				Integer.parseInt(parts[4].replaceAll("mirKO", ""));
			} catch (NumberFormatException e) {
				// MirKO IKMC project IDs should be Integers, but this
				// record is not an integer
				return false;
			}

			if (!parts[3].replaceAll("\"", "").matches(pipeline)) {
				// Wrong project
				return false;
			}

			if (parts[5].equals("")) {
				// Missing ESC name
				qcStatistics.record("WARNING", NUM_MISSING_ESC);
				logger.logdInfo("Missing ESC name: " + rec, false);
				return false;
			}

			if (parts[6].indexOf(",") != -1) {
				// strangely formatted ES Cell (parental)
				qcStatistics.record("WARNING", NUM_UNKNOWN_PARENT);
				return false;
			}

			if (!alleleTypes.contains(parts[8])) {
				// unknown mutation type
				qcStatistics.record("WARNING", NUM_UNKNOWN_MUTATION);
				return false;
			}

			// if the record is missing coordinates, fail it
			try {
				if (Integer.valueOf(parts[9]) == null) {
					return false;
				}
				if (Integer.valueOf(parts[10]) == null) {
					return false;
				}
			} catch (NumberFormatException e) {
				return false;
			}

			// Skip all cell lines that belong to a mirKO cluster
			if (lookupMirkoClusterByCellLine.lookup(parts[5]) != null) {
				qcStatistics.record("WARNING", NUM_CLUSTER_ESC);
				return false;
			}

			// Looks like this cell line is not already
			// loaded as belonging to a mirKO cluster project
			return true;

		} catch (MGIException e) {
			logger.logdInfo("Malformed record " + rec, false);
			logger.logdInfo(e.toString(), false);
			return false;
		}
	}

}
