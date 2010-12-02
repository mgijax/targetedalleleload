package org.jax.mgi.app.targetedalleleload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.dbs.mgd.AccessionLib;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_CellLineDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_CellLineState;
import org.jax.mgi.dbs.mgd.dao.ALL_CellLineDAO;
import org.jax.mgi.dbs.mgd.loads.Alo.DerivationLookupByKey;
import org.jax.mgi.dbs.mgd.loads.Alo.MutantCellLine;
import org.jax.mgi.dbs.mgd.lookup.CellLineNameLookupByKey;
import org.jax.mgi.dbs.mgd.lookup.ParentStrainLookupByParentKey;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.StrainNameLookup;
import org.jax.mgi.dbs.mgd.lookup.VocabTermLookup;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.dla.loader.DLALoader;
import org.jax.mgi.shr.dla.loader.DLALoaderException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.InputDataFile;
import org.jax.mgi.shr.ioutils.RecordDataIterator;

/**
 * The TargetedAlleleLoad class is the main entry for this load. It is a
 * DLALoader style class that is responsible for reading an Allele input file
 * (downloaded nightly from the KOMP production centers) and determining, for
 * each line in the file, if an Allele already exists in the MGI database. If it
 * does not exist, The TargetedAlleleLoad creates the allele. If it does exist,
 * the Allele gets a series of quality control checks performed on it.
 * 
 * @is a DLALoader for loading KOMP produced Alleles into the database and
 *     associating them with appropriate marker annotations, strains, molecular
 *     notes, and J-number references. This process will also create the
 *     official nomenclature and generate an official MGI ID for the allele.
 * 
 */

public class TargetedAlleleLoad extends DLALoader {

	private QualityControlStatistics qcStats = new QualityControlStatistics();

	// String constants for QC reporting
	private static final String NUM_DERIVATIONS_NOT_FOUND = "Number of derivations not found";
	private static final String NUM_BAD_CELLLINE_PROCESSING = "Number of cell lines that were not able to be constructed";
	private static final String NUM_ALLELES_NOTE_CHANGE = "Number of alleles that had molecular notes updated";
	private static final String NUM_CELLLINES_CHANGE_TYPE = "Number of cell lines that changed type";
	private static final String NUM_CELLLINES_CHANGED_DERIVATION = "Number of cell lines that changed derivation";
	private static final String NUM_CELLLINES_CHANGED_PIPELINE = "Number of cell lines that changed IKMC groups";
	private static final String NUM_CELLLINES_CHANGED_NUMBER = "Number of cell lines that changed sequence number";
	private static final String NUM_CELLINES_MISSING_ALLELE = "Number of cell lines that cannot find associated allele";
	private static final String NUM_CELLINES_CHANGED_MARKER = "Number of cell lines that changed marker, skipped";
	private static final String NUM_ALLELES_CHANGED_TRANS = "Number of alleles that changed transmission type, not processed";
	private static final String NUM_BAD_ALLELE_PROCESSING = "Number of alleles that were not able to be constructed";
	private static final String NUM_MISSING_PARENT = "Number of input records missing parental cell line";
	private static final String NUM_CELLLINES_CREATED = "Number of cell lines created";
	private static final String NUM_ALLELES_CREATED = "Number of alleles created";
	private static final String NUM_DUPLICATE_INPUT_REC = "Number of duplicate cell line records in input file";
	private static final String NUM_BAD_INPUT_REC = "Number of input records that were unable to be processed";
	private static final String NUM_CELLLINES_CHANGED_CREATOR = "Number of cell lines that changed creator";
	private static final String NUM_CELLLINES_CHANGED_ALLELE = "Number of cell lines that changed allele associations";
	private static final String NUM_ORPHANED_ALLELES = "Number of orphaned alleles created";

	// String constants for Log messages
	private static final String LOG_ALLELE_NOT_FOUND = "Cell line ~~INPUT_MCL~~ found in database, but cannot find associated allele\n";
	private static final String LOG_MARKER_CHANGED = "MUTANT ES CELL CHANGED MARKER\n"
			+ "Mutant Cell line: ~~INPUT_MCL~~\n"
			+ "Existing Marker: ~~EXISTING_MARKER~~\n"
			+ "Changed to: ~~INPUT_MARKER~~\n";
	private static final String LOG_ALLELE_TRANSMISSION_CHANGED = "ALLELE GERMLINE TRANSMISSION\n"
		+ "An attempt to change Mutant Cell line ~~INPUT_MCL~~ from allele\n"
		+ "~~EXISTING_ALLELE~~ to allele ~~CONSTRUCTED_ALLELE~~ failed because\n"
		+ "the transmission is germline.  Not further automated action can be taken on ~~EXISTING_ALLELE~~.\n";
	private static final String LOG_CELLLINE_TYPE_CHANGED = "MUTANT ES CELL CHANGED TYPE\n"
			+ "Mutant Cell line: ~~INPUT_MCL~~\n"
			+ "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
			+ "New allele symbol: ~~INPUT_SYMBOL~~\n";
	private static final String LOG_CELLLINE_GROUP_CHANGED = "MUTANT ES CELL CHANGED GROUP\n"
			+ "Mutant Cell line: ~~INPUT_MCL~~\n"
			+ "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
			+ "New allele symbol: ~~INPUT_SYMBOL~~\n";
	private static final String LOG_CELLLINE_CREATOR_CHANGED = "MUTANT ES CELL CHANGED CREATOR\n"
			+ "Mutant Cell line: ~~INPUT_MCL~~\n"
			+ "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
			+ "New allele symbol: ~~INPUT_SYMBOL~~\n";
	private static final String LOG_CELLLINE_DERIVATION_CHANGED = "MUTANT ES CELL CHANGED DERIVATION\n"
			+ "Mutant Cell line: ~~INPUT_MCL~~\n"
			+ "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
			+ "Changed derivation from ~~EXISTING_DERIVATION~~ to ~~INPUT_DERIVATION~~\n";
	private static final String LOG_CELLLINE_NUMBER_CHANGED = "MUTANT ES CELL CHANGED SEQUENCE NUMBER\n"
			+ "Mutant Cell line: ~~INPUT_MCL~~\n"
			+ "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
			+ "New allele symbol: ~~INPUT_SYMBOL~~\n";
	private static final String LOG_CELLLINE_ALLELE_CHANGED = "MUTANT ES CELL CHANGED ALLELE\n"
			+ "Mutant Cell line: ~~INPUT_MCL~~\n"
			+ "Old allele symbol: ~~EXISTING_SYMBOL~~\n"
			+ "New allele symbol: ~~INPUT_SYMBOL~~\n"
			+ "Deriavation changed from ~~EXISTING_DERIVATION~~ to ~~INPUT_DERIVATION~~\n";

	// Constant Regular expression patterns
	private static final Pattern pipelinePattern = Pattern
			.compile(".*<tm\\d{1,2}[ae]{0,1}\\((.*)\\).*>");
	private static final Pattern alleleSequencePattern = Pattern
			.compile(".*<tm(\\d{1,2})[ae]{0,1}.*>");
	private static final Pattern alleleTypePattern = Pattern
			.compile(".*<tm\\d{1,2}([ae]{0,1}).*>");
	private static final Pattern alleleCreatorPattern = Pattern
			.compile(".*<tm\\d{1,2}[ae]{0,1}\\(.*\\)(.*)>");

	// Standard DLA required classes
	private RecordDataIterator iter;
	private KnockoutAlleleProcessor processor;
	private KnockoutAlleleInterpreter interp;
	private KnockoutAlleleFactory alleleFactory;
	private TargetedAlleleLoadCfg cfg;
	private SQLDataManager sqlDBMgr;
	private Timestamp currentTime;

	// Cached DB Lookups
	private KOMutantCellLineLookup koMutantCellLineLookup;
	private AlleleLookupByKey alleleLookupByKey;
	private AlleleLookupByProjectId alleleLookupByProjectId;
	private AlleleLookupByMarker alleleLookupByMarker;
	private DerivationLookupByVectorCreatorParentType derivationLookup;
	private VectorLookup vectorLookup;
	private MarkerLookupByMGIID markerLookup;
	private ParentStrainLookupByParentKey parentStrainLookupByParentKey;
	private StrainKeyLookup strainKeyLookup;
	private AlleleLookupByCellLine alleleLookupByCellLine;
	private VocabTermLookup vocTermLookup;
	private CellLineNameLookupByKey cellLineNameLookupByKey;
	private CellLineStrainKeyLookupByCellLineKey cellLineStrainKeyLookupByCellLineKey;
	private StrainNameLookup strainNameLookup;
	private AlleleCellLineCount alleleCellLineCount;
	private DerivationLookupByKey derivationLookupByKey;

	// Class variables to hold global QC data
	private Map alleleProjects = new HashMap();
	private Map alleleNotes = new HashMap();
	private Set alleleProjectIdUpdated = new TreeSet();
	private Set databaseProjectIds = new HashSet();
	private Set databaseCellLines = new HashSet();

	/**
	 * The
	 * 
	 * @throws DLALoaderException
	 *             thrown if the super class cannot be instantiated
	 */
	public TargetedAlleleLoad() throws MGIException {
		// Instance the configuration object
		cfg = new TargetedAlleleLoadCfg();
		sqlDBMgr = SQLDataManagerFactory.getShared(SchemaConstants.MGD);

		Integer escLogicalDB = cfg.getEsCellLogicalDb();
		koMutantCellLineLookup = new KOMutantCellLineLookup(escLogicalDB);

		// These lookups implement a Singleton pattern because
		// they're shared across objects so updates in one object
		// should be reflected in the other objects
		alleleLookupByKey = AlleleLookupByKey.getInstance();
		alleleLookupByProjectId = AlleleLookupByProjectId.getInstance();
		alleleLookupByMarker = AlleleLookupByMarker.getInstance();
		alleleCellLineCount = AlleleCellLineCount.getInstance();
		derivationLookup = DerivationLookupByVectorCreatorParentType
				.getInstance();

		derivationLookupByKey = new DerivationLookupByKey();
		alleleLookupByCellLine = new AlleleLookupByCellLine();
		alleleLookupByCellLine.initCache();

		parentStrainLookupByParentKey = new ParentStrainLookupByParentKey();
		strainKeyLookup = new StrainKeyLookup();
		vectorLookup = new VectorLookup();
		markerLookup = new MarkerLookupByMGIID();
		vocTermLookup = new VocabTermLookup();
		cellLineNameLookupByKey = new CellLineNameLookupByKey();
		cellLineStrainKeyLookupByCellLineKey = new CellLineStrainKeyLookupByCellLineKey();
		strainNameLookup = new StrainNameLookup();

		alleleFactory = KnockoutAlleleFactory.getFactory();

		// This contains the combination of pipeline and provider
		// that this load is currently loading.
		String loadProvider = "(" + cfg.getPipeline() + ")" + cfg.getProvider();

		filterProjectIds(databaseProjectIds, loadProvider);
		filterCellLines(databaseCellLines, loadProvider);

	}

	/**
	 * Filter out inappropriate project IDs based on pipeline and provider.
	 * Projects for other pipeline don't get QCed during this execution
	 * 
	 * @param databaseProjectIds
	 *            set of all project IDs to be QCed
	 * @param loadProvider
	 *            string to identify the appropriate projects
	 * @throws DBException
	 *             if the lookup fails
	 * @throws CacheException
	 *             if the lookup fails
	 */
	private void filterProjectIds(Set databaseProjectIds, String loadProvider)
			throws DBException, CacheException {
		Iterator it = alleleLookupByProjectId.getKeySet().iterator();
		while (it.hasNext()) {
			String label = (String) it.next();
			Map a = alleleLookupByProjectId.lookup(label);

			// All alleles in the project belong to the same
			// pipeline/provider combination, so just look at the first one
			Map b = (Map) a.values().toArray()[0];

			// If the allele belongs to the same combination of pipeline
			// and provider, then add it to the QC check pool
			if (((String) b.get("symbol")).indexOf(loadProvider) >= 0) {
				databaseProjectIds.add(label);
			}
		}
	}

	/**
	 * Filter out inappropriate cell lines based on pipeline and provider.
	 * Projects for other pipeline don't get QCed during this execution
	 * 
	 * @param databaseCellLines
	 *            set of all cell lines to be QCed
	 * @param loadProvider
	 *            string to identify the appropriate projects
	 * @throws DBException
	 *             if the lookup fails
	 * @throws CacheException
	 *             if the lookup fails
	 */
	private void filterCellLines(Set databaseCellLines, String loadProvider)
			throws DBException, CacheException {
		// Add only cell lines appropriate for this pipeline and provider
		// to the QC pool (cell lines for other pipeline don't need QC
		// during this run)
		Iterator it = alleleLookupByCellLine.getKeySet().iterator();
		while (it.hasNext()) {
			String label = (String) it.next();
			KnockoutAllele a = alleleLookupByCellLine.lookup(label);
			if (a.getSymbol().indexOf(loadProvider) >= 0) {
				databaseCellLines.add(label);
			}
		}

	}

	/**
	 * initialize the internal structures used by this class
	 * 
	 * @assumes nothing
	 * @effects internal structures including database caching is initialized
	 */
	protected void initialize() throws MGIException {
		sqlDBMgr.setLogger(logger);
		logger.logdDebug("TargetedAlleleLoader sqlDBMgr.server "
				+ sqlDBMgr.getServer());
		logger.logdDebug("TargetedAlleleLoader sqlDBMgr.database "
				+ sqlDBMgr.getDatabase());

		logger.logInfo("Reading input files");
		logger.logpInfo("Processing " + cfg.getPipeline(), false);

		InputDataFile inputFile = new InputDataFile(cfg);

		// Get an appropriate Interpreter for the file
		interp = alleleFactory.getInterpreter();

		// Get an Iterator for going through the input file
		iter = inputFile.getIterator(interp);

		// Get an appropriate Processor for the records in the file
		processor = alleleFactory.getProcessor();

	}

	/**
	 * The input files are in different formats, so read each of the files
	 * according to their input format objects to provide a collection of
	 * consistent Allele objects to the run process
	 * 
	 * @assumes nothing
	 * @effects nothing
	 * @throws nothing
	 */
	protected void preprocess() throws MGIException {
		// Initialize the statistics for alleles and cell lines created
		// by the load so far
		qcStats.record("SUMMARY", NUM_ALLELES_CREATED, 0);
		qcStats.record("SUMMARY", NUM_CELLLINES_CREATED, 0);
	}

	/**
	 * read the knockout allele input file and run the process that creates new
	 * alleles in MGD
	 * 
	 * @assumes nothing
	 * @effects the data will be created for loading Alleles and associated ES
	 *          Cell lines into the database
	 * @throws MGIException
	 *             thrown if there is an error accessing the input file or
	 *             writing output data
	 */
	protected void run() throws MGIException {

		// Keep track of which alleles we've updated the notes for
		// so we only update it once
		Set alreadyProcessed = new HashSet();

		// For each input record
		while (iter.hasNext()) {
			// Instance the input records
			KnockoutAlleleInput in = null;

			try {
				in = (KnockoutAlleleInput) iter.next();
			} catch (MGIException e) {
				logger.logdInfo(e.toString(), true);
				qcStats.record("WARNING", NUM_BAD_INPUT_REC);
				continue;
			}

			// Keep track of the projects and mutant cell lines we've already
			// seen
			databaseProjectIds.remove(in.getProjectId().toLowerCase());
			databaseCellLines.remove(in.getMutantCellLine().toLowerCase());

			// If this record is not appropriate to be handled by this
			// processor, skip it. The only reason we included it in the
			// first place was to assist in the QC of all cell lines
			if ( ! in.getInputPipeline().equals(cfg.getPipeline())) {
				continue;
			}

			if (alreadyProcessed.contains(in.getMutantCellLine())) {
				String m = "Multiple input records for: ";
				m += in.getMutantCellLine() + "\n";
				logger.logdInfo(m, false);
				qcStats.record("WARNING", NUM_DUPLICATE_INPUT_REC);
				continue;
			} else {
				alreadyProcessed.add(in.getMutantCellLine());
			}

			if (in.getParentCellLine().equals("")
					|| in.getParentCellLine().equals("-")
					|| in.getParentCellLine().equals("[ENTERYOURDATAVALUE]")) {
				qcStats.record("ERROR", NUM_MISSING_PARENT);

				String m = "Missing parental cell line, skipping record: "
						+ in.getMutantCellLine() + " (parental: "
						+ in.getParentCellLine() + ")\n";
				logger.logdInfo(m, false);
				continue;
			}

			// Construct the allele from the input record
			KnockoutAllele constructed = null;

			try {
				constructed = processor.process(in);
			} catch (KeyNotFoundException e) {
				qcStats.record("ERROR", NUM_BAD_ALLELE_PROCESSING);

				String m = "Allele creation error (bad key), check: "
						+ in.getMutantCellLine() + "\n";
				logger.logdInfo(m, false);
				m = "An error occured while processing the input record for: "
						+ in.getMutantCellLine() + "\n"
						+ "The provider might be using a secondary MGI ID ("
						+ in.getGeneId() + ")\n";
				logger.logcInfo(m, false);
				continue;
			} catch (MGIException e) {
				qcStats.record("ERROR", NUM_BAD_ALLELE_PROCESSING);

				String m = "General error, skipping record: "
						+ in.getMutantCellLine() 
						+ "\n";
				try {
					// Just the first message of the exception needs
					// to be reported
					BufferedReader reader = new BufferedReader(
							new StringReader(e.getMessage()));
					m += reader.readLine();
				} catch (IOException e1) {
					m = "An error occured processing "+ in.getMutantCellLine() +
						" then another error occured trying to get that error.";
				}

				logger.logdInfo(m, false);
				continue;
			}

			if (constructed == null) {
				qcStats.record("ERROR", NUM_BAD_ALLELE_PROCESSING);

				String m = "Allele creation error, check: ";
				m += in.getMutantCellLine();
				logger.logdInfo(m, false);
				continue;
			}

			// Does the Mutant Cell Line record exist in the
			// cache (database or recently created)?
			MutantCellLine esCell = koMutantCellLineLookup.lookup(in
					.getMutantCellLine());
			
			if (cfg.getUpdateDerivationMode()) {
				if (esCell != null) {
					// Find the existing associated allele
					KnockoutAllele existing = alleleLookupByCellLine.lookup(in
							.getMutantCellLine());

					// If the associated allele can't be found, there's a major
					// problem. The caches are out of synch, or the cell line to
					// allele association is missing. Regardless, we can't
					// process this record further, report the error and continue
					if (existing == null) {
						// Report this to the diagnostic log
						String m = LOG_ALLELE_NOT_FOUND.replaceAll("~~INPUT_MCL~~",
								in.getMutantCellLine());
						logger.logdInfo(m, true);
						qcStats.record("ERROR", NUM_CELLINES_MISSING_ALLELE);
						continue;
					}

					org.jax.mgi.dbs.mgd.loads.Alo.Derivation d = 
						(org.jax.mgi.dbs.mgd.loads.Alo.Derivation) 
						derivationLookupByKey.lookup(esCell.getDerivationKey());
					
					// We need to compare the type of allele to the associated
					// derivation.  The allele type overrules the derivation
					// if it's different.
					String aType = getAlleleType(existing);
					Integer typeKey = (Integer) Constants.MUTATION_TYPE_KEYS.get(aType);
					
					String vector = d.getVectorName();
					Integer parentKey = d.getParentCellLineKey();

					Integer realDerivationKey = getDerivationKey(vector, parentKey, typeKey);
					
					if ( ! esCell.getDerivationKey().equals(realDerivationKey)) {
						logDerivationChange(in, esCell, existing);
						changeDerivationKey(realDerivationKey, esCell);
					}
				}
				
				// We are in update the derivation mode only!  Nothing
				// else is supposed to occur, so just skip to the next
				// record
				continue;
			}
		
			// Update mode or create mode
			if (cfg.getUpdateOnlyMode()) {
				if (esCell != null) {
				// Mutant ES Cell found in MGI, check the associated allele

				// Find the existing associated allele
				KnockoutAllele existing = alleleLookupByCellLine.lookup(in
						.getMutantCellLine());

				// If the associated allele can't be found, there's a major
				// problem. The caches are out of synch, or the cell line to
				// allele association is missing. Regardless, we can't
				// process this record further, report the error and continue
				if (existing == null) {
					// Report this to the diagnostic log
					String m = LOG_ALLELE_NOT_FOUND.replaceAll("~~INPUT_MCL~~",
							in.getMutantCellLine());
					logger.logdInfo(m, true);
					qcStats.record("ERROR", NUM_CELLINES_MISSING_ALLELE);
					continue;
				}

				// ********************************************************
				// BEGIN QC CHECKS
				// ********************************************************

				if ( ! isMatchingGene(existing, constructed)) {
					// Check the allele to marker association, if it has changed,
					// report to the log for manual curation.
					logMarkerChanged(in, constructed, existing);
				} else if (alleleProjects.get(existing.getKey()) != null ||
						( ! existing.getProjectId().equals(
						constructed.getProjectId())
						&& ! isTypeChange(existing, constructed)
						&& ! isGroupChange(existing, constructed)
						&& ! isCreatorChange(existing, constructed)
						&& ! isDerivationChange(esCell, in)
						)) {
					// We can only update the project ID once all cell lines
					// for the allele have been
					// verified to require the same change

					// The extra "alleleProjects.get(existing) != null ||"
					// check is to see if there are project IDs that have
					// NOT
					// changed from the original, when others have. The
					// existence of an entry in alleleProjects means we've
					// updated this project ID before.

					// The project ID changed, but the type, group,
					// creator, derivation and marker didn't change, we
					// can just try to update the allele project ID
					// in place

					if (alleleProjects.get(existing.getKey()) == null) {
						alleleProjects.put(existing.getKey(), new HashSet());
					}

					// Record the updated project ID for this allele symbol
					Set projSet = (Set) alleleProjects.get(existing.getKey());
					projSet.add(constructed.getProjectId());
					alleleProjects.put(existing.getKey(), projSet);

					// save the new project ID for this allele symbol
					String m = existing.getSymbol() + "\t"
							+ existing.getProjectId() + "\t"
							+ constructed.getProjectId() + "\t"
							+ in.getMutantCellLine();
					alleleProjectIdUpdated.add(m);
				} else if ( ! existing.getSymbol()
						.equals(constructed.getSymbol())) {
					// If the associated allele symbol has changed at all,
					// then we need to change it and update the derivation

					// Symbols don't match
					// The marker didn't change (checked previously)
					// so one of these attributes changed.
					// 1- Parental cell line
					// 2- Type
					// 3- IKMC group
					// 4- Creator
					// 5- Vector

					if (isTypeChange(existing, constructed)) {
						logTypeChange(in, constructed, existing);
					}

					if (isGroupChange(existing, constructed)) {
						logGroupChange(in, constructed, existing);
					}

					if (isCreatorChange(existing, constructed)) {
						logCreatorChange(in, constructed, existing);
					}

					if (isNumberChange(existing, constructed)) {
						logNumberChange(in, constructed, existing);
					}

					// Re-associate the cell line to a new allele
					logAlleleChanged(in, constructed, esCell, existing);
					changeMutantCellLineAssociation(in, esCell, existing,
							constructed);
				} else if ( ! esCell.getDerivationKey().equals(
						getDerivationKey(in))) {
					// Check the derivation (this implicitly checks the
					// parental cell line, the creator, the vector and the
					// allele type)

					logDerivationChange(in, esCell, existing);
					changeDerivationKey(getDerivationKey(in), esCell);

				} else {

					// Compress the note fields to discount any extra spaces
					// that
					// might have snuck in
					String existingNote = existing.getNote()
							.replaceAll("\\n", "").replaceAll(" ", "");
					String constructedNote = constructed.getNote()
							.replaceAll("\\n", "").replaceAll(" ", "");

					// The extra "|| alleleNotes.get(existing) != null"
					// check is to see if there are notes that have NOT
					// changed from the original, when others have. The
					// existence of an entry in alleleNotes means we've
					// updated this note before.
					if ( ! existingNote.equals(constructedNote)
							|| alleleNotes.get(existing.getKey()) != null) {
						// If we get this far in the QC checks, then
						// we can be sure that the creator, the type,
						// the vector, and the parental cell line are all
						// the same. The only thing left that could have
						// changed are the coordinates

						if (alleleNotes.get(existing.getKey()) == null) {
							alleleNotes.put(existing.getKey(), new HashSet());
						}

						// save the new molecular note for this allele symbol
						Set notes = (Set) alleleNotes.get(existing.getKey());
						notes.add(constructed.getNote());
						alleleNotes.put(existing.getKey(), notes);
					}
				}

				// ********************************************************
				// END QC CHECKS
				// ********************************************************
				// done with QC checks. skip on to the next record
				continue;
				}
			} else {
				
				// Only create anything if the cell line doesn't exist
				// the QC checking process (which occurs if this load
				// is run when the cfg.getUpdateOnlyMode() is true)
				
				// If the cell line was not found in the database,
				// create a new cell line and associated objects
				if (esCell == null) {
					Integer mclKey = null;
					try {
						mclKey = createMutantCellLine(in, false);
					} catch (MGIException e) {
						qcStats.record("ERROR", NUM_BAD_CELLLINE_PROCESSING);
						String m = "Exception creating mutant cell line, "
								+ "skipping record: " + in.getMutantCellLine()
								+ "\n" + in + "\n";
						try {
							// Just the first message of the exception needs
							// to be reported
							BufferedReader reader = new BufferedReader(
									new StringReader(e.getMessage()));
							m += reader.readLine();
						} catch (IOException e1) {
							m = "An error occured processing "+ in.getMutantCellLine() +
								" then another error occured trying to get that error.";
						}
						logger.logdInfo(m, false);
						continue;
					}

					if (mclKey == null) {
						qcStats.record("ERROR", NUM_BAD_CELLLINE_PROCESSING);
						String m = "Mutant cell line not created, "
								+ "skipping record: " + in.getMutantCellLine()
								+ "\n" + in + "\n";
						logger.logdInfo(m, false);
						continue;
					}
	
					// lookup existing alleles for this project
					String projectId = in.getProjectId();
					Map alleles = alleleLookupByProjectId.lookup(projectId);
					Integer alleleKey = null;
					if (alleles != null) {
						// try to get the allele identified by the constructed
						// symbol
						Map allele = (Map) alleles.get(constructed.getSymbol());
						if (allele != null) {
							// found an allele with this same symbol
							alleleKey = (Integer) allele.get("key");
						}
					}
	
					if (alleleKey == null) {
						// did not find appropriate allele. create a new allele
						createAllele(constructed, in, alleles);
						alleleKey = constructed.getKey();
					}
	
					// if an appropriate allele cannot be found or created,
					// report the error and skip on to the next record
					if (alleleKey == null) {
						String m = "An error occured creating allele: ";
						m += constructed + "\n";
						m += in + "\n";
						logger.logdInfo(m, false);
						continue;
					}
	
					associateCellLineToAllele(alleleKey, mclKey);
				}
			} // end if (cfg.getUpdateOnlyMode())
		} // end while (iter.hasNext())
	} // end protected void run()

	// Logging helper functions
	private void logMarkerChanged(KnockoutAlleleInput in,
			KnockoutAllele constructed, KnockoutAllele existing) {
		String m = LOG_MARKER_CHANGED
				.replaceAll("~~INPUT_MCL~~", 
						in.getMutantCellLine())
				.replaceAll("~~EXISTING_MARKER~~",
						existing.getSymbol())
				.replaceAll("~~INPUT_MARKER~~",
						constructed.getSymbol());

		logger.logcInfo(m, false);
		qcStats.record("SUMMARY", NUM_CELLINES_CHANGED_MARKER);
	}
	private void logDerivationChange(KnockoutAlleleInput in,
			MutantCellLine esCell, KnockoutAllele existing) throws MGIException {
		String m = LOG_CELLLINE_DERIVATION_CHANGED
				.replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
				.replaceAll("~~EXISTING_SYMBOL~~",
						existing.getSymbol())
				.replaceAll("~~EXISTING_DERIVATION~~",
						esCell.getDerivationKey().toString())
				.replaceAll("~~INPUT_DERIVATION~~",
						getDerivationKey(in).toString());
		logger.logcInfo(m, false);
		qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_DERIVATION);
	}
	private void logAlleleChanged(KnockoutAlleleInput in,
			KnockoutAllele constructed, MutantCellLine esCell,
			KnockoutAllele existing) throws MGIException {
		String m = LOG_CELLLINE_ALLELE_CHANGED
				.replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
				.replaceAll("~~EXISTING_SYMBOL~~",
						existing.getSymbol())
				.replaceAll("~~INPUT_SYMBOL~~",
						constructed.getSymbol())
				.replaceAll("~~EXISTING_DERIVATION~~",
						esCell.getDerivationKey().toString())
				.replaceAll("~~INPUT_DERIVATION~~",
						getDerivationKey(in).toString());
		logger.logcInfo(m, false);
		qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_DERIVATION);
		qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_ALLELE);
	}
	private void logNumberChange(KnockoutAlleleInput in,
			KnockoutAllele constructed, KnockoutAllele existing) {
		String m = LOG_CELLLINE_NUMBER_CHANGED
				.replaceAll("~~INPUT_MCL~~",
						in.getMutantCellLine())
				.replaceAll("~~EXISTING_SYMBOL~~",
						existing.getSymbol())
				.replaceAll("~~INPUT_SYMBOL~~",
						constructed.getSymbol());
		logger.logcInfo(m, false);
		qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_NUMBER);
	}
	private void logCreatorChange(KnockoutAlleleInput in,
			KnockoutAllele constructed, KnockoutAllele existing) {
		String m = LOG_CELLLINE_CREATOR_CHANGED
				.replaceAll("~~INPUT_MCL~~",
						in.getMutantCellLine())
				.replaceAll("~~EXISTING_SYMBOL~~",
						existing.getSymbol())
				.replaceAll("~~INPUT_SYMBOL~~",
						constructed.getSymbol());
		logger.logcInfo(m, false);
		qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_CREATOR);
	}
	private void logGroupChange(KnockoutAlleleInput in,
			KnockoutAllele constructed, KnockoutAllele existing) {
		String m = LOG_CELLLINE_GROUP_CHANGED
				.replaceAll("~~INPUT_MCL~~",
						in.getMutantCellLine())
				.replaceAll("~~EXISTING_SYMBOL~~",
						existing.getSymbol())
				.replaceAll("~~INPUT_SYMBOL~~",
						constructed.getSymbol());
		logger.logcInfo(m, false);
		qcStats.record("SUMMARY",
				NUM_CELLLINES_CHANGED_PIPELINE);
	}
	private void logTypeChange(KnockoutAlleleInput in,
			KnockoutAllele constructed, KnockoutAllele existing) {
		String m = LOG_CELLLINE_TYPE_CHANGED
				.replaceAll("~~INPUT_MCL~~",
						in.getMutantCellLine())
				.replaceAll("~~EXISTING_SYMBOL~~",
						existing.getSymbol())
				.replaceAll("~~INPUT_SYMBOL~~",
						constructed.getSymbol());
		logger.logcInfo(m, false);
		qcStats.record("SUMMARY", NUM_CELLLINES_CHANGE_TYPE);
	}

	/**
	 * Checks if two KnockoutAllele objects have the same creator lab code based
	 * on a substring of the symbols. The creator lab code has been included in
	 * the allele symbol and is a strong pattern to find (alleleCreatorPattern)
	 * 
	 * This method short circuits with true (changed) if it cannot find creator
	 * for either of the alleles
	 * 
	 * Example: Xyz&lt;tm1a(KOMP)Wtsi&lt; has a type difference from
	 * Xyz&lt;tm1a(KOMP)Ucd&gt;
	 * 
	 * @param first
	 * @param second
	 * @return true if both alleles have the same creator, false otherwise
	 */
	private boolean isCreatorChange(KnockoutAllele first, KnockoutAllele second) {
		Matcher regexMatcher;
		String firstCreator;
		String secondCreator;

		regexMatcher = alleleCreatorPattern.matcher(first.getSymbol());
		if (regexMatcher.find()) {
			firstCreator = regexMatcher.group(1);
		} else {
			return true;
		}

		regexMatcher = alleleCreatorPattern.matcher(second.getSymbol());
		if (regexMatcher.find()) {
			secondCreator = regexMatcher.group(1);
		} else {
			return true;
		}

		if (firstCreator.equals(secondCreator)) {
			// is not different
			return false;
		}

		// is different
		return true;
	}

	/**
	 * Checks if two KnockoutAllele objects have the same sequence number based
	 * on a substring of the symbols. The sequence number has been included in
	 * the allele symbol as an integer and is a strong pattern to find
	 * (alleleSequencePattern)
	 * 
	 * This method short circuits with true (changed) if it cannot find sequence
	 * for either of the alleles
	 * 
	 * Example: Xyz&lt;tm1a(KOMP)Wtsi&lt; has a type difference from
	 * Xyz&lt;tm2a(KOMP)Wtsi&gt;
	 * 
	 * @param first
	 * @param second
	 * @return true if both alleles have the same sequence, false otherwise
	 */
	private boolean isNumberChange(KnockoutAllele first, KnockoutAllele second) {
		Matcher regexMatcher;
		String firstNumber;
		String secondNumber;

		regexMatcher = alleleSequencePattern.matcher(first.getSymbol());
		if (regexMatcher.find()) {
			firstNumber = regexMatcher.group(1);
		} else {
			return true;
		}

		regexMatcher = alleleSequencePattern.matcher(second.getSymbol());
		if (regexMatcher.find()) {
			secondNumber = regexMatcher.group(1);
		} else {
			return true;
		}

		if (firstNumber.equals(secondNumber)) {
			// is not different
			return false;
		}

		// is different
		return true;
	}

	/**
	 * Returns the type of an IKMC allele based on a letter code defined
	 * by the International nomenclature committee
	 * 
	 * @param allele 
	 * @return the type of allele passed in
	 */
	private String getAlleleType(KnockoutAllele allele) {
        Matcher regexMatcher;
        String type;

        regexMatcher = alleleTypePattern.matcher(allele.getSymbol());
        if (regexMatcher.find()) {
            if (regexMatcher.group(1).equals("a")) {
                type = "Conditional";
            } else if (regexMatcher.group(1).equals("e")) {
                type = "Targeted non-conditional";
            } else if (regexMatcher.group(1).equals("")) {
                type = "Deletion";
            } else {
                type = "Unknown";
            }
        } else {
            type = "Deletion";
        }
        return type;
	}

	/**
	 * Checks if two KnockoutAllele objects have the same type based on a
	 * substring of the symbols. The type has been included in the allele symbol
	 * as a letter code (or lacking a letter) and is a strong pattern to find
	 * (alleleTypePattern)
	 * 
	 * Example: Xyz&lt;tm1a(KOMP)Wtsi&lt; has a type difference from
	 * Xyz&lt;tm1e(KOMP)Wtsi&gt;
	 * 
	 * @param first
	 * @param second
	 * @return true if both alleles have the same type, false otherwise
	 */
	private boolean isTypeChange(KnockoutAllele first, KnockoutAllele second) {
		String firstType = getAlleleType(first);
		String secondType = getAlleleType(second);

		if (firstType.equals(secondType)) {
			// is not different
			return false;
		}

		// is different
		return true;
	}

	/**
	 * Checks if two KnockoutAllele objects have the same IKMC group based on a
	 * substring of the symbols. The IKMC group has been included in the allele
	 * symbol in parenthesis and there is a strong pattern to find it
	 * (pipelinePattern)
	 * 
	 * This method short circuits with true (changed) if it cannot find an IKMC
	 * group for either of the alleles
	 * 
	 * Example: Xyz&lt;tm1a(KOMP)Wtsi&gt; has a group difference from
	 * Xyz&lt;tm1a(EUCOMM)Wtsi&gt;
	 * 
	 * @param first
	 * @param second
	 * @return true if different IKMC groups, false if same group
	 */
	private boolean isGroupChange(KnockoutAllele first, KnockoutAllele second) {
		String firstIkmcGroup;
		String secondIkmcGroup;
		Matcher regexMatcher;

		regexMatcher = pipelinePattern.matcher(first.getSymbol());
		if (regexMatcher.find()) {
			firstIkmcGroup = regexMatcher.group(1);
		} else {
			return true;
		}

		regexMatcher = pipelinePattern.matcher(second.getSymbol());
		if (regexMatcher.find()) {
			secondIkmcGroup = regexMatcher.group(1);
		} else {
			return true;
		}

		if (firstIkmcGroup.equals(secondIkmcGroup)) {
			// is not different
			return false;
		}

		// is different
		return true;
	}

	/**
	 * Checks if an input record has the same derivation as an existing
	 * MutantCellLine object
	 * 
	 * @param esCell
	 * @param in
	 * @return true if different derivation key, false if same
	 */
	private boolean isDerivationChange(MutantCellLine esCell,
			KnockoutAlleleInput in) throws MGIException {
		Integer key = esCell.getDerivationKey();
		Integer newKey = getDerivationKey(in);
		if ( key.equals(newKey)) {
			// is not different
			return false;
		}
		// is different
		return true;
	}

	/**
	 * Checks if two KnockoutAllele objects belong to the same gene. The gene is
	 * identified by gene_key
	 * 
	 * @param first
	 * @param second
	 * @return
	 */
	private boolean isMatchingGene(KnockoutAllele first, KnockoutAllele second) {
		Integer existingGeneKey = first.getMarkerKey();
		Integer constructedGeneKey = second.getMarkerKey();
		if (existingGeneKey.equals(constructedGeneKey)) {
			return true;
		}
		return false;
	}

	private Integer getDerivationKey(String vector, Integer parentKey, Integer typeKey)
	throws MGIException {
		// Find the derivation key for this ES Cell
		// The correct derivation is found by combining:
		// * cassette
		// * parental cell line
		// * mutation type
		// * creator
		Integer vectorKey = vectorLookup.lookup(vector);
		
		if (vectorKey == null) {
			throw new MGIException("Cannot find vector: "
					+ vector);
		}

		Integer creatorKey = new Integer(cfg.getCreatorKey());

		String dCompoundKey = vectorKey.toString();
		dCompoundKey += "|" + creatorKey.toString();
		dCompoundKey += "|" + parentKey.toString();
		dCompoundKey += "|" + typeKey.toString();
		
		Integer derivationKey = derivationLookup.lookup(dCompoundKey);
		
		if (derivationKey == null) {
			// CREATE THE NEW DERIVATION AND INSERT IT
			Derivation d = new Derivation();
		
			String creatorName = vocTermLookup.lookup(creatorKey);
			String typeName = vocTermLookup.lookup(typeKey);
			String parentName = cellLineNameLookupByKey.lookup(parentKey);
			String strainName = strainNameLookup
					.lookup(cellLineStrainKeyLookupByCellLineKey
							.lookup(parentKey));
		
			// Derivation name is Creator+Type+Parental+Strain+Vector
			String name = creatorName + " " + typeName + " " + parentName + " "
					+ strainName + " " + vector;
		
			d.setName(name);
			d.setDescription(null);
			d.setVectorKey(vectorKey);
			d.setVectorTypeKey(new Integer(Constants.VECTOR_TYPE_KEY));
			d.setParentCellLineKey(parentKey);
			d.setDerivationTypeKey(typeKey);
			d.setCreatorKey(creatorKey);
			d.setRefsKey(null);
		
			// Inserting a new derivation automatically adds it to the
			// singleton derivation lookup cache
			d.insert(loadStream);
		
			derivationKey = d.getDerivationKey();
		
			String s = "Creating derivation for " + name;
			qcStats.record("WARNING", s);
			logger.logdInfo(s, true);
		}
		
		return derivationKey;
	}
	
	private Integer getDerivationKey(KnockoutAlleleInput in)
			throws MGIException {

		// Find the derivation key for this ES Cell
		// The correct derivation is found by combining:
		// * cassette
		// * parental cell line
		// * mutation type
		// * creator
		String cassette = in.getCassette();
		String parent = in.getParentCellLine();
		String aType = in.getMutationType();

		Integer vectorKey = vectorLookup.lookup(cassette);

		if (vectorKey == null) {
			throw new MGIException("Cannot find vector for cassette: "
					+ cassette);
		}

		Integer creatorKey = new Integer(cfg.getCreatorKey());
		Integer parentKey;

		try {
			parentKey = cfg.getParentalKey(parent);
		} catch (ConfigException e) {
			String s = in.getParentCellLine();
			s += " Does not exist in CFG file! Skipping record";
			logger.logdInfo(s, true);
			qcStats.record("ERROR", NUM_DERIVATIONS_NOT_FOUND);
			throw new MGIException("Cannot find parental cell line key for "
					+ in.getParentCellLine());
		}
		Integer typeKey = (Integer) Constants.MUTATION_TYPE_KEYS.get(aType);

		String dCompoundKey = vectorKey.toString();
		dCompoundKey += "|" + creatorKey.toString();
		dCompoundKey += "|" + parentKey.toString();
		dCompoundKey += "|" + typeKey.toString();

		Integer derivationKey = derivationLookup.lookup(dCompoundKey);

		if (derivationKey == null) {
			// CREATE THE NEW DERIVATION AND INSERT IT
			Derivation d = new Derivation();

			String creatorName = vocTermLookup.lookup(creatorKey);
			String typeName = vocTermLookup.lookup(typeKey);
			String parentName = cellLineNameLookupByKey.lookup(parentKey);
			String strainName = strainNameLookup
					.lookup(cellLineStrainKeyLookupByCellLineKey
							.lookup(parentKey));

			// Derivation name is Creator+Type+Parental+Strain+Vector
			String name = creatorName + " " + typeName + " " + parentName + " "
					+ strainName + " " + cassette;

			d.setName(name);
			d.setDescription(null);
			d.setVectorKey(vectorKey);
			d.setVectorTypeKey(new Integer(Constants.VECTOR_TYPE_KEY));
			d.setParentCellLineKey(parentKey);
			d.setDerivationTypeKey(typeKey);
			d.setCreatorKey(creatorKey);
			d.setRefsKey(null);

			// Inserting a new derivation automatically adds it to the
			// singleton derivation lookup cache
			d.insert(loadStream);

			derivationKey = d.getDerivationKey();

			String s = "Creating derivation for " + name;
			qcStats.record("WARNING", s);
			logger.logdInfo(s, true);
		}

		return derivationKey;
	}

	private void changeDerivationKey(Integer newDerivationKey,
			MutantCellLine esCell) throws MGIException {
		// Update the derivation key for this cell line
		String query = "UPDATE ALL_Cellline SET ";
		query += "_derivation_key = " + newDerivationKey;
		query += " WHERE _cellline_key = " + esCell.getMCLKey();
		executeQuery(query);
		qcStats.record("WARNING", NUM_CELLLINES_CHANGED_DERIVATION);
	}

	private void changeMutantCellLineAssociation(KnockoutAlleleInput in,
			MutantCellLine esCell, KnockoutAllele oldAllele,
			KnockoutAllele newAllele) throws MGIException {

		// Prevent the cell line from being moved to a different
		// allele if the transmission has changed
		if ((oldAllele.getTransmissionKey().intValue() 
				!= Constants.ALLELE_TRANSMISSION_CELLLINE) &&
				! oldAllele.getSymbol().equals(newAllele.getSymbol())) {
			// Check the allele to marker association, if it has changed,
			// report to the log for manual curation.
			String m = LOG_ALLELE_TRANSMISSION_CHANGED
					.replaceAll("~~INPUT_MCL~~", 
							in.getMutantCellLine())
					.replaceAll("~~EXISTING_ALLELE~~",
							oldAllele.getSymbol())
					.replaceAll("~~CONSTRUCTED_ALLELE~~",
							newAllele.getSymbol());

			logger.logcInfo(m, false);
			qcStats.record("SUMMARY", NUM_ALLELES_CHANGED_TRANS);
			return;
		}

		// Update the count of MCL associated to this allele and create an
		// orphan MCL record if the count drops to 0
		alleleCellLineCount.decrement(oldAllele.getSymbol());
		alleleCellLineCount.increment(newAllele.getSymbol());

		Integer count = alleleCellLineCount.lookup(oldAllele.getSymbol());
		if (count.intValue() < 1) {
			// the *last* MCL associated to the old allele has been removed
			// create an orphaned MCL and associate it to the allele
			createOrphanMCL(esCell, oldAllele);

			// we just created a "placeholder" MCL to keep the allele
			// associated to the correct derivation even though the last
			// "real" MCL migrated elsewhere. increment the MCL counter
			alleleCellLineCount.increment(oldAllele.getSymbol());
		}

		// Change the derivation _after_ the orphan is created...
		// Changing the allele requires that the derivation key changes.
		changeDerivationKey(getDerivationKey(in), esCell);

		// Remove the association existing allele <-> cellline association
		// from the database
		String query = "DELETE FROM ALL_Allele_Cellline";
		query += " WHERE _Allele_key = ";
		query += oldAllele.getKey();
		query += " AND _MutantCellLine_key = ";
		query += esCell.getMCLKey();

		executeQuery(query);

		// Lookup existing alleles for this project
		String projectId = in.getProjectId();
		Map alleles = alleleLookupByProjectId.lookup(projectId);

		// If there are any alleles for this project, see if one of the
		// existing alleles is the correct one
		if (alleles != null) {
			// Try to get the allele identified by the new allele symbol
			HashMap allele = (HashMap) alleles.get(newAllele.getSymbol());

			// If we found the new allele, attach the MCL to it and return
			if (allele != null) {
				// Found an allele with this same symbol
				Integer alleleKey = (Integer) allele.get("key");
				associateCellLineToAllele(alleleKey, esCell.getMCLKey());

				KnockoutAllele a = alleleLookupByKey.lookup(alleleKey);

				if (a.getStatus().intValue() 
						!= Constants.ALLELE_STATUS_APPROVED) {
					// This may occur multiple times when an orphaned 
					// allele is brought back from deleted status.  It's 
					// okay. This action is idempotent (it can be applied 
					// multiple times without changing the result).

					// Set the allele status to approved
					String q = "UPDATE ALL_Allele SET _Allele_Status_key = "
						+ Constants.ALLELE_STATUS_APPROVED
						+ " WHERE _Allele_key = "
						+ alleleKey;
					executeQuery(q);					
				}

				return;
			}
		}

		// Turns out that the cellline didn't match any existing alleles, 
		// create a new allele and association the cellline
		createAllele(newAllele, in, alleles);
		associateCellLineToAllele(newAllele.getKey(), esCell.getMCLKey());
		qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_ALLELE);
	}


	private void createOrphanMCL(MutantCellLine esCell,
			KnockoutAllele oldAllele) throws MGIException {
		/*
		SangerAlleleInput input = new SangerAlleleInput();
		input.setESCellName("Orphaned");
		input.setMutationType(esCell.getCellLineType());
		input.setParentESCellName(esCell.get);
		input.setProjectId(oldAllele.getProjectId());
		input.setGeneId(oldAllele.getMarkerKey());
		input.setLocus1("0");
		input.setLocus2("0");
		input.setBuild(in.getBuild());
		input.setCassette();
		input.setInputPipeline(cfg.getPipeline());

		Integer celllineKey = createMutantCellLine(input, true);
		
		*/
		//////////////////////////////////////////////////////////////

		// Create the mutant cell line
		MutantCellLine mcl = new MutantCellLine();
		mcl.setCellLine("Orphaned");
		mcl.setCellLineTypeKey(new Integer(Constants.ESCELL_TYPE_KEY));
		mcl.setDerivationKey(esCell.getDerivationKey());
		mcl.setIsMutant(new Boolean(true));
		mcl.setStrainKey(esCell.getStrainKey());
		mcl.setCreationDate(currentTime);
		mcl.setModificationDate(currentTime);
		mcl.setCreatedByKey(cfg.getJobStreamKey());
		mcl.setModifiedByKey(cfg.getJobStreamKey());

		// Insert the MCL into the database to get the _CellLine_key
		ALL_CellLineDAO mclDAO = new ALL_CellLineDAO(mcl.getState());
		loadStream.insert(mclDAO);
		//////////////////////////////////////////////////////////////
		associateCellLineToAllele(oldAllele.getKey(), 
				mclDAO.getKey().getKey());

		// Set the old allele to deleted status
		String query = "UPDATE ALL_Allele SET _Allele_Status_key = "+
			Constants.ALLELE_STATUS_DELETED + " WHERE _Allele_key = "+
			oldAllele.getKey();
		
		executeQuery(query);

		qcStats.record("WARNING", NUM_ORPHANED_ALLELES);
		logger.logcInfo("Orphaned allele " 
				+ oldAllele.getSymbol() 
				+ "\n", false);
	}

	private Integer createMutantCellLine(KnockoutAlleleInput in, boolean orphan)
			throws MGIException {
		Integer derivationKey = getDerivationKey(in);

		// Create the mutant cell line
		MutantCellLine mcl = new MutantCellLine();
		mcl.setCellLine(in.getMutantCellLine());
		mcl.setCellLineTypeKey(new Integer(Constants.ESCELL_TYPE_KEY));
		mcl.setDerivationKey(derivationKey);
		mcl.setIsMutant(new Boolean(true));

		// Get the stain key of the parental cell line
		Integer parentalCellLineKey = cfg
				.getParentalKey(in.getParentCellLine());
		String strainName = parentStrainLookupByParentKey
				.lookup(parentalCellLineKey);
		Integer strainKey = strainKeyLookup.lookup(strainName);
		mcl.setStrainKey(strainKey);

		mcl.setCreationDate(currentTime);
		mcl.setModificationDate(currentTime);
		mcl.setCreatedByKey(cfg.getJobStreamKey());
		mcl.setModifiedByKey(cfg.getJobStreamKey());

		// Insert the MCL into the database to get the _CellLine_key
		ALL_CellLineDAO mclDAO = new ALL_CellLineDAO(mcl.getState());
		loadStream.insert(mclDAO);

		// Only record the new accession entry and cached version of this
		// cell line if it is not an orphan MCL
		if ( ! orphan) {
			// Add the recently created cell line to the cache
			koMutantCellLineLookup.addToCache(in.getMutantCellLine(), mcl);

			// Create the MutantCellLine Accession object
			// note the missing AccID parameter which indicates this is
			// an MGI ID
			AccessionId mclAccId = new AccessionId(in.getMutantCellLine(), // MCL
					cfg.getEsCellLogicalDb(), // Logical DB
					mclDAO.getKey().getKey(), // MCL object key
					new Integer(Constants.ESCELL_MGITYPE_KEY), // MGI type
					Boolean.FALSE, // Private?
					Boolean.TRUE // Preferred?
			);
			mclAccId.insert(loadStream);

			qcStats.record("SUMMARY", NUM_CELLLINES_CREATED);
		}

		return mclDAO.getKey().getKey();
	}

	private void associateCellLineToAllele(Integer alleleKey,
			Integer celllineKey) throws MGIException {
		// Create the allele to cell line association
		ALL_Allele_CellLineState aclState = new ALL_Allele_CellLineState();
		aclState.setMutantCellLineKey(celllineKey);
		aclState.setAlleleKey(alleleKey);
		ALL_Allele_CellLineDAO aclDAO = new ALL_Allele_CellLineDAO(aclState);
		loadStream.insert(aclDAO);
	}

	private KnockoutAllele createAllele(KnockoutAllele constructed,
			KnockoutAlleleInput in, Map alleles) throws MGIException {
		// Persist the constructed allele
		constructed.insert(loadStream);

		// Create the allele lookup with all the data from this input record
		Map allele = new HashMap();
		allele.put("projectid", in.getProjectId());
		allele.put("key", constructed.getKey());
		allele.put("symbol", constructed.getSymbol());
		List mcls = new ArrayList();
		mcls.add(in.getMutantCellLine());
		allele.put("mutantCellLines", mcls);
		allele.put("parentCellLine", in.getParentCellLine());
		allele.put("parentCellLineKey",
				cfg.getParentalKey(in.getParentCellLine()));

		if (alleles == null) {
			alleles = new HashMap();
		}

		// Include the new allele in the cached allelesByProjectID map
		alleles.put(constructed.getSymbol(), allele);
		alleleLookupByProjectId.addToCache(in.getProjectId(), alleles);

		// add the newly created allele to the allele cache
		alleleLookupByKey.addToCache(constructed.getKey(), constructed);

		// add the newly created allele to the alleleByMarker cache
		Marker mrk = markerLookup.lookup(in.getGeneId());
		String markerSymbol = mrk.getSymbol();

		Set alleleSet = null;
		alleleSet = alleleLookupByMarker.lookup(markerSymbol);
		if (alleleSet == null) {
			alleleSet = new HashSet();
		}
		alleleSet.add(constructed.getKey());
		alleleLookupByMarker.addToCache(markerSymbol, alleleSet);

		qcStats.record("SUMMARY", NUM_ALLELES_CREATED);

		return constructed;
	}

	/**
	 * Must close the BCP file stream and commit the new maximum MGI number back
	 * to the database. Print out the QC statistics
	 * 
	 * @assumes nothing
	 * @effects Updates the database row in ACC_AccessionMax for MGI IDs
	 * @throws MGIException
	 *             if something goes wrong
	 */
	protected void postprocess() throws MGIException {

		TreeMap qc = null;
		Iterator iterator = null;

		// Print out the error statistics
		qc = (TreeMap) qcStats.getStatistics().get("ERROR");

		if (qc != null) {
			logger.logdInfo("\nERRORS", false);
			logger.logpInfo("\nERRORS", false);

			iterator = qc.keySet().iterator();
			while (iterator.hasNext()) {
				String label = (String) iterator.next();
				logger.logdInfo(label + ": " + qc.get(label), false);
				logger.logpInfo(label + ": " + qc.get(label), false);
			}
		}

		// Print out the warning statistics
		qc = (TreeMap) qcStats.getStatistics().get("WARNING");

		if (qc != null) {
			logger.logdInfo("\nWARNINGS", false);
			logger.logpInfo("\nWARNINGS", false);

			iterator = qc.keySet().iterator();
			while (iterator.hasNext()) {
				String label = (String) iterator.next();
				logger.logdInfo(label + ": " + qc.get(label), false);
				logger.logpInfo(label + ": " + qc.get(label), false);
			}
		}

		// Print out the summary statistics
		qc = (TreeMap) qcStats.getStatistics().get("SUMMARY");

		if (qc != null) {
			logger.logdInfo("\nSUMMARY", false);
			logger.logpInfo("\nSUMMARY", false);

			iterator = qc.keySet().iterator();
			while (iterator.hasNext()) {
				String label = (String) iterator.next();
				logger.logdInfo(label + ": " + qc.get(label), false);
				logger.logpInfo(label + ": " + qc.get(label), false);
			}
		}


		// After processing ALL the input records, there is now enough
		// data to determine if the allele level attributes can be changed.

		if ( ! cfg.getUpdateDerivationMode()) {
			// If not in derivation update only mode
			// These alleles need to have their project ID updated
			if (alleleProjects.size() > 0) {
				Set entries = alleleProjects.entrySet();
				Iterator it = entries.iterator();
				while (it.hasNext()) {
					Map.Entry entry = (Map.Entry) it.next();
					Integer key = (Integer) entry.getKey();
					KnockoutAllele existing = alleleLookupByKey.lookup(key);
	
					List projects = new ArrayList((Set) entry.getValue());
	
					if (projects.size() != 1) {
						logger.logdInfo("Project for " + existing.getSymbol()
								+ " could NOT be updated to " + projects, false);
					} else if ( ! existing.getProjectId().equals(projects.get(0))) {
						logger.logdInfo("Project for " + existing.getSymbol()
								+ " updated to " + projects, false);
	
						String newProjectId = (String) projects.get(0);
	
						String query = "UPDATE ACC_Accession" + " SET accID = '"
								+ newProjectId + "'" + " WHERE _Object_key = "
								+ existing.getKey() + " AND _LogicalDB_key = "
								+ cfg.getProjectLogicalDb()
								+ " AND _MGIType_key = "
								+ Constants.ALLELE_MGI_TYPE + " AND accID = '"
								+ existing.getProjectId() + "'";
						executeQuery(query);
					}
				}
			}
	
			// These alleles need to have their molecular note updated
			if (alleleNotes.size() > 0) {
				Set entries = alleleNotes.entrySet();
				Iterator it = entries.iterator();
				while (it.hasNext()) {
					Map.Entry entry = (Map.Entry) it.next();
					Integer key = (Integer) entry.getKey();
					KnockoutAllele a = alleleLookupByKey.lookup(key);
					List notes = new ArrayList((Set) entry.getValue());
	
					if (notes.size() != 1) {
	
						// Multiple notes for this allele!
	
						logger.logdInfo("Molecular note for " + a.getSymbol()
								+ " could NOT be updated to:\n" + notes, false);
					} else if ( ! a.getNote().equals(notes.get(0))) {
	
						// The MCLs all agree that there should be a new note
						logger.logdInfo("Molecular note for " + a.getSymbol()
								+ " updated to:\n" + notes, false);
	
						// If a note exists
						// Delete the existing note
						if (a.getNoteKey() != null) {
							String query = "DELETE FROM MGI_Note WHERE ";
							query += "_Note_key = ";
							query += a.getNoteKey();
	
							executeQuery(query);
	
							// Attach the new note to the existing allele
							a.updateNote(loadStream, (String) notes.get(0));
							qcStats.record("SUMMARY", NUM_ALLELES_NOTE_CHANGE);
						} else {
							logger.logdInfo("Note key for " + a.getSymbol()
									+ " could NOT be found", false);
						}
					}
				}
			}
	
			if (databaseCellLines.size() > 0 || databaseProjectIds.size() > 0
					|| alleleProjectIdUpdated.size() > 0) {
				logger.logdInfo(
						"Number of project IDs that exist in the MGI database, but not in file: "
								+ databaseProjectIds.size(), false);
				logger.logpInfo(
						"Number of project IDs that exist in the MGI database, but not in file: "
								+ databaseProjectIds.size(), false);
	
				logger.logdInfo(
						"Number of celllines that exist in the MGI database, but not in file: "
								+ databaseCellLines.size(), false);
				logger.logpInfo(
						"Number of celllines that exist in the MGI database, but not in file: "
								+ databaseCellLines.size(), false);
	
				logger.logdInfo("Number of alleles that changed project IDs: "
						+ alleleProjectIdUpdated.size(), false);
				logger.logpInfo("Number of alleles that changed project IDs: "
						+ alleleProjectIdUpdated.size(), false);
	
				logger.logdInfo("\nANOMALIES", false);
				logger.logcInfo("\nANOMALIES", false);
			}
	
			if (databaseCellLines.size() > 0) {
				logger.logdInfo(
						"\nCelllines that exist in the MGI database, but not in the input file: "
								+ databaseCellLines.size(), false);
				logger.logcInfo(
						"\nCelllines that exist in the MGI database, but not in the input file: "
								+ databaseCellLines.size(), false);
	
				logger.logdInfo("\nAllele\tExisting Project\tES Cell Line", false);
				logger.logcInfo("\nAllele\tExisting Project\tES Cell Line", false);
	
				iterator = databaseCellLines.iterator();
				Set s = new TreeSet();
				while (iterator.hasNext()) {
					String label = (String) iterator.next();
					KnockoutAllele a = alleleLookupByCellLine.lookup(label);
					s.add(a.getSymbol() + "\t" + a.getProjectId() + "\t"
							+ label.toUpperCase());
				}
	
				iterator = s.iterator();
				while (iterator.hasNext()) {
					String lbl = (String) iterator.next();
					logger.logdInfo(lbl, false);
					logger.logcInfo(lbl, false);
				}
			}
	
			if (databaseProjectIds.size() > 0) {
				logger.logdInfo(
						"\nProject IDs that exist in the MGI database, but not in the input file: "
								+ databaseProjectIds.size(), false);
				logger.logcInfo(
						"\nProject IDs that exist in the MGI database, but not in the input file: "
								+ databaseProjectIds.size(), false);
	
				logger.logdInfo("\nAllele\tExisting Project", false);
				logger.logcInfo("\nAllele\tExisting Project", false);
	
				iterator = databaseProjectIds.iterator();
				Set s = new TreeSet();
				while (iterator.hasNext()) {
					String label = (String) iterator.next();
					Map hmA = alleleLookupByProjectId.lookup(label);
					if (hmA != null) {
						Set entries = hmA.entrySet();
						Iterator aIt = entries.iterator();
						while (aIt.hasNext()) {
							Map.Entry entry = (Map.Entry) aIt.next();
							Map tmpAllele = (Map) entry.getValue();
							s.add((String) tmpAllele.get("symbol") + "\t" + label);
						}
					}
				}
	
				iterator = s.iterator();
				while (iterator.hasNext()) {
					String lbl = (String) iterator.next();
					logger.logdInfo(lbl, false);
					logger.logcInfo(lbl, false);
				}
			}
	
			if (alleleProjectIdUpdated.size() > 0) {
				logger.logdInfo("\nAlleles that have had project ID changes: "
						+ alleleProjectIdUpdated.size(), false);
				logger.logcInfo("\nAlleles that have had project ID changes: "
						+ alleleProjectIdUpdated.size(), false);
	
				logger.logdInfo("\nAllele\tExisting Project\tNew Project\tMCL",
						false);
				logger.logcInfo("\nAllele\tExisting Project\tNew Project\tMCL",
						false);
	
				iterator = alleleProjectIdUpdated.iterator();
				while (iterator.hasNext()) {
					String label = (String) iterator.next();
					logger.logdInfo(label, false);
					logger.logcInfo(label, false);
				}
			}
		} // end if ( ! cfg.getUpdateDerivationMode()) 

		// Empty line to the log files
		logger.logdInfo("\n", false);
		logger.logcInfo("\n", false);

		// Close the database writer
		loadStream.close();

		// If any new MGI IDs have been generated during processing, the
		// ACC_AccessionMax table needs to be updated with the new maximum
		// value.
		AccessionLib.commitAccessionMax();

		logger.logInfo("Process Finishing");
		return;
	}

	// Helper function to log queries when run in debug mode
	// otherwise execute the query.
	private void executeQuery(String query) 
	throws ConfigException, DBException {
		if (cfg.getPreventBcpExecute()) {
			logger.logdInfo("SQL prevented by CFG. Would have run: " + query,
					false);
		} else {
			sqlDBMgr.executeUpdate(query);
		}
	}

}
