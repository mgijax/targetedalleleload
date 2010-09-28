package org.jax.mgi.app.targetedalleleload;

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
 * @is a DLALoader for loading KOMP produced Alleles into the database and
 *     associating them with appropriate marker annotations, strains, molecular
 *     notes, and J-number references. This process will also create the
 *     official nomenclature and generate an official MGI ID for the allele.
 * @has nothing
 * @does reads the Allele input file (downloaded nightly from the KOMP
 *       production centers websites) and determines, for each line in the file,
 *       if the Allele already exists in the MGI database. If it does not exist,
 *       The targetedalleleload creates the allele.
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class TargetedAlleleLoad extends DLALoader {

	private QualityControlStatistics qcStats = new QualityControlStatistics();

	// String constants for QC reporting
	private static final String NUM_DERIVATIONS_NOT_FOUND = "Number of derivations not found";
	private static final String NUM_BAD_CELLLINE_PROCESSING = "Number of cell lines that were not able to be constructed";
	private static final String NUM_ALLELES_NEED_NOTE_CHANGE = "Number of alleles that need to have molecular notes updated by curator";
	private static final String NUM_ALLELES_NOTE_CHANGE = "Number of alleles that had molecular notes updated";
	private static final String NUM_CELLLINES_CHANGE_TYPE = "Number of cell lines that changed type";
	private static final String NUM_CELLLINES_CHANGED_DERIVATION = "Number of cell lines that changed derivation";
	private static final String NUM_CELLLINES_CHANGED_PIPELINE = "Number of cell lines that changed IKMC groups";
	private static final String NUM_CELLLINES_CHANGED_NUMBER = "Number of cell lines that changed sequence number";
	private static final String NUM_CELLINES_MISSING_ALLELE = "Number of cell lines that cannot find associated allele";
	private static final String NUM_CELLINES_CHANGED_MARKER = "Number of cell lines that changed marker, skipped";
	private static final String NUM_BAD_ALLELE_PROCESSING = "Number of alleles that were not able to be constructed";
	private static final String NUM_MISSING_PARENT = "Number of input records missing parental cell line";
	private static final String NUM_CELLLINES_CREATED = "Number of cell lines created";
	private static final String NUM_ALLELES_CREATED = "Number of alleles created";
	private static final String NUM_DUPLICATE_INPUT_REC = "Number of duplicate cell line records in input file";
	private static final String NUM_BAD_INPUT_REC = "Number of input records that were unable to be processed";
	private static final String NUM_CELLLINES_CHANGED_CREATOR = "Number of cell lines that changed creator";
	private static final String NUM_CELLLINES_CHANGED_ALLELE = "Number of cell lines that changed allele associations";

	// String constants for Log messages
	private static final String LOG_ALLELE_NOT_FOUND = "Cell line ~~INPUT_MCL~~ found in database, but cannot find associated allele\n"; 
	private static final String LOG_MARKER_CHANGED = "MUTANT ES CELL CHANGED MARKER\nMutant Cell line: ~~INPUT_MCL~~\nExisting Marker: ~~EXISTING_MARKER~~\nChanged to: ~~INPUT_MARKER~~\n";
	private static final String LOG_CELLLINE_TYPE_CHANGED = "MUTANT ES CELL CHANGED TYPE\nMutant Cell line: ~~INPUT_MCL~~\nExisting allele symbol: ~~EXISTING_SYMBOL~~\nNew allele symbol: ~~INPUT_SYMBOL~~\n";
	private static final String LOG_CELLLINE_GROUP_CHANGED = "MUTANT ES CELL CHANGED GROUP\nMutant Cell line: ~~INPUT_MCL~~\nExisting allele symbol: ~~EXISTING_SYMBOL~~\nNew allele symbol: ~~INPUT_SYMBOL~~\n";
	private static final String LOG_CELLLINE_CREATOR_CHANGED = "MUTANT ES CELL CHANGED CREATOR\nMutant Cell line: ~~INPUT_MCL~~\nExisting allele symbol: ~~EXISTING_SYMBOL~~\nNew allele symbol: ~~INPUT_SYMBOL~~\n";
	private static final String LOG_CELLLINE_DERIVATION_CHANGED = "MUTANT ES CELL CHANGED DERIVATION\nMutant Cell line: ~~INPUT_MCL~~\nExisting allele symbol: ~~EXISTING_SYMBOL~~\nChanged derivation from ~~EXISTING_DERIVATION~~ to ~~INPUT_DERIVATION~~\n";
	private static final String LOG_CELLLINE_NUMBER_CHANGED = "MUTANT ES CELL CHANGED SEQUENCE NUMBER\nMutant Cell line: ~~INPUT_MCL~~\nExisting allele symbol: ~~EXISTING_SYMBOL~~\nNew allele symbol: ~~INPUT_SYMBOL~~\n";
	private static final String LOG_CELLLINE_ALLELE_CHANGED = "MUTANT ES CELL CHANGED ALLELE\nMutant Cell line: ~~INPUT_MCL~~\nOld allele symbol: ~~EXISTING_SYMBOL~~\nNew allele symbol: ~~INPUT_SYMBOL~~\nDeriavation changed from ~~EXISTING_DERIVATION~~ to ~~INPUT_DERIVATION~~\n";

	// Constant Regular expression patterns
	private static final Pattern pipelinePattern = Pattern.compile(".*<tm\\d{1,2}[ae]{0,1}\\((.*)\\).*>");
	private static final Pattern alleleSequencePattern = Pattern.compile(".*<tm(\\d{1,2})[ae]{0,1}.*>");
	private static final Pattern alleleTypePattern = Pattern.compile(".*<tm\\d{1,2}([ae]{0,1}).*>");
	private static final Pattern alleleCreatorPattern = Pattern.compile(".*<tm\\d{1,2}[ae]{0,1}\\(.*\\)(.*)>");

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

	// Class variables to hold global QC data
	private Map alleleProjects = new HashMap();
	private Map alleleNotes = new HashMap();
	private Set alleleProjectIdUpdated = new TreeSet();
	private Set alleleNoteUpdated = new TreeSet();
	private Set databaseProjectIds = new HashSet();
	private Set databaseCellLines = new HashSet();

	/**
	 * constructor
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
	 * @param databaseProjectIds set of all project IDs to be QCed 
	 * @param loadProvider string to identify the appropriate projects
	 * @throws DBException if the lookup fails
	 * @throws CacheException if the lookup fails
	 */
	private void filterProjectIds(Set databaseProjectIds, String loadProvider) 
	throws DBException, CacheException {
		Iterator it = alleleLookupByProjectId.getKeySet().iterator();
		while (it.hasNext()) {
			String label = (String) it.next();
			Map a = alleleLookupByProjectId.lookup(label);

			// All alleles in the project belong to the same pipeline/provider
			// combination, so just look at the first one.
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
	 * @param databaseCellLines set of all cell lines to be QCed 
	 * @param loadProvider string to identify the appropriate projects
	 * @throws DBException if the lookup fails
	 * @throws CacheException if the lookup fails
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
	 * @does The input files are in different formats, so read each of the files
	 *       according to their input format objects to provide a collection of
	 *       consistent Allele objects to the run process
	 * @assumes nothing
	 * @effects nothing
	 * @throws nothing
	 */
	protected void preprocess() throws MGIException {
		// Initialize the statistics for alleles and cell lines created
		// by the load so far
		qcStats.record("SUMMARY", NUM_ALLELES_CREATED, 0);
		qcStats.record("SUMMARY", NUM_CELLLINES_CREATED, 0);
		return;
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
			// processor, skip it.  The only reason we included it in the
			// first place was to assist in the QC of all cell lines
			if (!in.getInputPipeline().equals(cfg.getPipeline())) {
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

				String m = "Allele creation error, check: "
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
						+ in.getMutantCellLine() + "\n" + e.getMessage();
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
			MutantCellLine esCell = koMutantCellLineLookup
					.lookup(in.getMutantCellLine());
			if (esCell != null) {
				// Mutant ES Cell found in MGI, check the associated allele

				// Find the existing associated allele
				KnockoutAllele existing = alleleLookupByCellLine
						.lookup(in.getMutantCellLine());

				// If the associated allele can't be found, there's a major
				// problem. The caches are out of synch, or the cell line to
				// allele association is missing. Regardless, we can't
				// process this record further, report the error and continue
				if (existing == null) {
					// Report this to the diagnostic log
					String m = LOG_ALLELE_NOT_FOUND
						.replaceAll("~~INPUT_MCL~~", in.getMutantCellLine());					
					logger.logdInfo(m, true);
					qcStats.record("ERROR", NUM_CELLINES_MISSING_ALLELE);
					continue;
				}

				// Check the allele to marker association, if it has changed,
				// report to the log for manual curation.
				boolean matchingGene = isMatchingGene(existing, constructed);
				if (!matchingGene) {
					// Report this to the curator log
					String m = LOG_MARKER_CHANGED
						.replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
						.replaceAll("~~EXISTING_MARKER~~", existing.getSymbol())
						.replaceAll("~~INPUT_MARKER~~", constructed.getSymbol());

					logger.logcInfo(m, false);
					qcStats.record("SUMMARY", NUM_CELLINES_CHANGED_MARKER);
					continue;
				}

				// If the associated allele symbol has changed at all,
				// then we need to change it and update the derivation
				if (!existing.getSymbol().equals(constructed.getSymbol())) {
					// Symbols don't match
					// The marker didn't change (checked previously)
					// so one of these attributes changed.
					// 1- Parental cell line
					// 2- Type
					// 3- IKMC group
					// 4- Creator
					
					boolean typeChange = isTypeChange(existing, constructed);
					boolean groupChange = isGroupChange(existing, constructed);
					boolean creatorChange = iscreatorChange(existing, constructed);
					boolean numberChange = isNumberChange(existing, constructed);
					
					if (typeChange) {
						String m = LOG_CELLLINE_TYPE_CHANGED
							.replaceAll("~~INPUT_MCL~~",  in.getMutantCellLine())
							.replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
							.replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol());
						logger.logcInfo(m, false);
						qcStats.record("SUMMARY", NUM_CELLLINES_CHANGE_TYPE);
					}

					if (groupChange) {
						String m = LOG_CELLLINE_GROUP_CHANGED
							.replaceAll("~~INPUT_MCL~~",  in.getMutantCellLine())
							.replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
							.replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol());
						logger.logcInfo(m, false);
						qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_PIPELINE);
					}

					if (creatorChange) {
						String m = LOG_CELLLINE_CREATOR_CHANGED
							.replaceAll("~~INPUT_MCL~~",  in.getMutantCellLine())
							.replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
							.replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol());
						logger.logcInfo(m, false);
						qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_CREATOR);
					}

					if (numberChange) {
						String m = LOG_CELLLINE_NUMBER_CHANGED
							.replaceAll("~~INPUT_MCL~~",  in.getMutantCellLine())
							.replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
							.replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol());
						logger.logcInfo(m, false);
						qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_NUMBER);
					}

					// The change of any of these attributes requires a
					// derivation change
					changeDerivationKey(getDerivationKey(in), esCell);
					
					// Change the allele association
					changeMutantCellLineAssociation(in, esCell, existing,
							constructed);
					String m = LOG_CELLLINE_ALLELE_CHANGED
						.replaceAll("~~INPUT_MCL~~",  in.getMutantCellLine())
						.replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
						.replaceAll("~~INPUT_SYMBOL~~", existing.getSymbol())
						.replaceAll("~~EXISTING_DERIVATION~~", esCell.getDerivationKey().toString())
						.replaceAll("~~INPUT_DERIVATION~~", getDerivationKey(in).toString());
					logger.logcInfo(m, false);
					qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_DERIVATION);
					qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_ALLELE);

					// This record has changed substantially, any further
					// QC checks would be incorrect, so skip to the next 
					// input record
					continue;
				}




				// Check the derivation (this implicitly checks the
				// parental cell line, the creator, the vector and the allele
				// type)
				if (!esCell.getDerivationKey().equals(getDerivationKey(in))) {
					String m = LOG_CELLLINE_DERIVATION_CHANGED
						.replaceAll("~~INPUT_MCL~~",  in.getMutantCellLine())
						.replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
						.replaceAll("~~EXISTING_DERIVATION~~", esCell.getDerivationKey().toString())
						.replaceAll("~~INPUT_DERIVATION~~", getDerivationKey(in).toString());
					logger.logcInfo(m, false);

					changeDerivationKey(getDerivationKey(in), esCell);
					changeMutantCellLineAssociation(in, esCell, existing,
							constructed);
					qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_DERIVATION);
					continue;
				}

				// Check the ES cell project ID versus the
				// existing allele project ID
				// If the project changed, report it for manual curation
				if (!existing.getProjectId().equals(constructed.getProjectId())) {
					// This mutant cell line had a project ID change
					String m = existing.getSymbol() + "\t"
							+ existing.getProjectId() + "\t"
							+ constructed.getProjectId() + "\t"
							+ in.getMutantCellLine();

					String k = existing.getSymbol() + " (" + existing.getProjectId() +")";
					if (alleleProjects.get(k) == null) {
						alleleProjects.put(k, new HashSet());
					}
					
					// Record the updated project ID for this allele symbol
					Set projSet = (Set)alleleProjects.get(k);
					projSet.add(constructed.getProjectId());
					alleleProjects.put(k, projSet);

					alleleProjectIdUpdated.add(m);
				}

				// Compress the note fields to discount any extra spaces that
				// might have snuck in
				String existingNote = existing.getNote()
					.replaceAll("\\n", "")
					.replaceAll(" ", "");
				String constructedNote = constructed.getNote()
					.replaceAll("\\n", "")
					.replaceAll(" ", "");

				// If we get this far in the QC checks, then
				// we can be sure that the creator, the type, the vector, 
				// and the parental cell line are all the same.  The only
				// thing left that could have changed are the coordinates
				if (!existingNote.equals(constructedNote)) {
					// This mutant cell line is associated with an allele
					// that has a molecular note change
					String k = existing.getSymbol();
					if (alleleNotes.get(k) == null) {
						alleleNotes.put(k, new HashSet());
					}
					
					// Record the updated molecular note for this allele symbol
					Set notes = (Set)alleleNotes.get(k);
					notes.add(constructed.getNote());
					alleleNotes.put(k, notes);

					String m = existing.getSymbol() + "\t" + in.getMutantCellLine();
					alleleNoteUpdated.add(m);
				}

				//				// If we get this far in the QC checks, then
//				// we can be sure that the creator, the type, the vector, 
//				// and the parental cell line are all the same.  The only
//				// thing left that could have changed are the coordinates
//				if (!existingNote.equals(constructedNote)
//						&& !alleleNoteUpdated.contains(existing.getSymbol())) {
//					alleleNoteUpdated.add(existing.getSymbol());
//					String noteMsg = "\nMOLECULAR NOTE CHANGED\n";
//
//					// If the note was entered by this load,
//					// update the note to reflect the current note,
//					// otherwise, a curator updated the note, so we
//					// shouldn't update it.
//					Integer jobStreamKey = cfg.getJobStreamKey();
//					Integer noteModifiedBy = existing.getNoteModifiedByKey();
//					if (noteModifiedBy == null || cfg.getOverwriteNote()
//							|| (jobStreamKey.compareTo(noteModifiedBy) == 0)) {
//						noteMsg += "Allele: " + existing.getSymbol() + "\n"
//								+ "Mutant Cell line: " + in.getMutantCellLine()
//								+ "\n\nCurrent/New note:\n"
//								+ existing.getNote() + "\n"
//								+ constructed.getNote() + "\n";
//
//						// If a note exists
//						// Delete the existing note
//						if (existing.getNoteKey() != null) {
//							String query = "DELETE FROM MGI_Note "
//									+ "WHERE _Note_key = "
//									+ existing.getNoteKey();
//
//							sqlDBMgr.executeUpdate(query);
//
//							// Attach the new note to the existing allele
//							String newNote = constructed.getNote();
//							existing.updateNote(loadStream, newNote);
//							qcStats.record("SUMMARY", NUM_ALLELES_NOTE_CHANGE);
//						} else {
//							noteMsg += "!!! Could not find existing note key. Not updating\n";
//						}
//					} else {
//						noteMsg += "Allele: " + existing.getSymbol()
//								+ " (not updating)\n" + "Mutant Cell line: "
//								+ in.getMutantCellLine() + "\nJobstream: "
//								+ jobStreamKey + "\nModifiedBy: "
//								+ noteModifiedBy + "\nCurrent/New note:\n"
//								+ existing.getNote() + "\n"
//								+ constructed.getNote() + "\n";
//						qcStats.record("WARNING", NUM_ALLELES_NEED_NOTE_CHANGE);
//					}
//					logger.logcInfo(noteMsg, false);
//				}

				// Done with QC checks. Go on to the next mutant cell line
				continue;
			} else {
				// Mutant ES Cell NOT found in database

				Integer mclKey = null;
				try {
					mclKey = createMutantCellLine(in);
				} catch (MGIException e) {
					qcStats.record("ERROR", NUM_BAD_CELLLINE_PROCESSING);
					String m = "Exception creating mutant cell line, skipping record: ";
					m += in.getMutantCellLine() + "\n";
					m += in + "\n";
					m += e.getMessage();
					logger.logdInfo(m, false);
					continue;
				}
				if (mclKey == null) {
					qcStats.record("ERROR", NUM_BAD_CELLLINE_PROCESSING);
					String m = "Mutant cell line not created, skipping record: ";
					m += in.getMutantCellLine() + "\n";
					m += in + "\n";
					logger.logdInfo(m, false);
					continue;
				}

				// lookup existing alleles for this project
				String projectId = in.getProjectId();
				Map alleles = alleleLookupByProjectId.lookup(projectId);

				if (alleles != null) {
					// Try to get the allele identified by the constructed
					// symbol
					Map allele = (Map) alleles.get(constructed.getSymbol());

					// If we found the allele, we can attach the MCL to it
					if (allele != null) {
						// Found an allele with this same symbol
						Integer alleleKey = (Integer) allele.get("key");

						associateCellLineToAllele(alleleKey, mclKey);
						continue;
					}
				}

				// The MCL Didn't match any alleles, create an allele and
				// attach the MCL to it.
				createAllele(constructed, in, alleles);
				associateCellLineToAllele(constructed.getKey(), mclKey);
			}
		}

		return;
	} // end protected void run()


	/**
	 * Checks if two KnockoutAllele objects have the same creator lab code
	 * based on a substring of the symbols.  The creator lab code has been
	 * included in the allele symbol and is a strong pattern 
	 * to find (alleleCreatorPattern)
	 * 
	 * This method short circuits with true (changed) if it cannot find 
	 * creator for either of the alleles
	 * 
	 * Example:
	 *    Xyz&lt;tm1a(KOMP)Wtsi&lt; 
	 *    has a type difference from 
	 *    Xyz&lt;tm1a(KOMP)Ucd&gt;
	 * @param first
	 * @param second
	 * @return true if both alleles have the same creator, false otherwise
	 */
	private boolean iscreatorChange(KnockoutAllele first, KnockoutAllele second) {
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
	 * Checks if two KnockoutAllele objects have the same sequence number
	 * based on a substring of the symbols.  The sequence number  has been
	 * included in the allele symbol as an integer and is a strong pattern 
	 * to find (alleleSequencePattern)
	 * 
	 * This method short circuits with true (changed) if it cannot find  
	 * sequence for either of the alleles
	 * 
	 * Example:
	 *    Xyz&lt;tm1a(KOMP)Wtsi&lt; 
	 *    has a type difference from 
	 *    Xyz&lt;tm2a(KOMP)Wtsi&gt;
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
	 * Checks if two KnockoutAllele objects have the same type
	 * based on a substring of the symbols.  The type has been included
	 * in the allele symbol as a letter code (or lacking a letter)  and
	 * is a strong pattern to find (alleleTypePattern)
	 * 
	 * Example:
	 *    Xyz&lt;tm1a(KOMP)Wtsi&lt; 
	 *    has a type difference from 
	 *    Xyz&lt;tm1e(KOMP)Wtsi&gt;
	 * @param first
	 * @param second
	 * @return true if both alleles have the same type, false otherwise
	 */
	private boolean isTypeChange(KnockoutAllele first, KnockoutAllele second) {
		Matcher regexMatcher;
		String firstType;
		String secondType;

		regexMatcher = alleleTypePattern.matcher(first.getSymbol());
		if (regexMatcher.find()) {
			firstType = regexMatcher.group(1);
		} else {
			firstType = "Deletion";
		}

		regexMatcher = alleleTypePattern.matcher(second.getSymbol());
		if (regexMatcher.find()) {
			secondType = regexMatcher.group(1);
		} else {
			secondType = "Deletion";
		}

		if (firstType.equals(secondType)) {
			// is not different
			return false;
		}

		// is different
		return true;
	}


	/**
	 * Checks if two KnockoutAllele objects have the same IKMC group
	 * based on a substring of the symbols.  The IKMC group has been included
	 * in the allele symbol in parenthesis and there is a strong pattern
	 * to find it (pipelinePattern)
	 * 
	 * This method short circuits with true (changed) if it cannot find an  
	 * IKMC group for either of the alleles
	 * 
	 * Example:
	 *   Xyz&lt;tm1a(KOMP)Wtsi&gt; 
	 *   has a group difference from 
	 *   Xyz&lt;tm1a(EUCOMM)Wtsi&gt;
	 * @param first
	 * @param second
	 * @return true if both alleles have the same IKMC group, false otherwise
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
	 * Checks if two KnockoutAllele objects belong to the same gene.
	 * The gene is identified by gene_key
	 * @param first
	 * @param second
	 * @return
	 */
	private boolean isMatchingGene( KnockoutAllele first, KnockoutAllele second) {
		Integer existingGeneKey = first.getMarkerKey();
		Integer constructedGeneKey = second.getMarkerKey();
		if (existingGeneKey.equals(constructedGeneKey)) {
			return true;
		}
		return false;
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

		Integer vectorKey = new Integer(vectorLookup.lookup(cassette));
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
		Integer typeKey = new Integer(
				(String) Constants.MUTATION_TYPE_KEYS.get(aType));

		String dCompoundKey = vectorKey.toString();
		dCompoundKey += "|" + creatorKey;
		dCompoundKey += "|" + parentKey;
		dCompoundKey += "|" + typeKey;

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
		sqlDBMgr.executeUpdate(query);
		qcStats.record("WARNING", NUM_CELLLINES_CHANGED_DERIVATION);
	}

	private void changeMutantCellLineAssociation(KnockoutAlleleInput in,
			MutantCellLine esCell, KnockoutAllele oldAllele,
			KnockoutAllele newAllele) throws MGIException {
		// Remove the association existing allele <-> cellline association
		// from the database
		String query = "DELETE FROM ALL_Allele_Cellline";
		query += " WHERE _Allele_key = ";
		query += oldAllele.getKey();
		query += " AND _MutantCellLine_key = ";
		query += esCell.getMCLKey();
		sqlDBMgr.executeUpdate(query);

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
				return;
			}
		}

		// Otherwise, the cellline Didn't match any alleles, create the new
		// allele and association the cellline with the new allele
		createAllele(newAllele, in, alleles);
		associateCellLineToAllele(newAllele.getKey(), esCell.getMCLKey());
		qcStats.record("WARNING", NUM_CELLLINES_CHANGED_ALLELE);
	}

	private Integer createMutantCellLine(KnockoutAlleleInput in)
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

		// Add the recently created cell line to the cache
		koMutantCellLineLookup.addToCache(in.getMutantCellLine(), mcl);

		// Create the MutantCellLine Accession object
		// note the missing AccID parameter which indicates this is
		// an MGI ID
		AccessionId mclAccId = new AccessionId(in.getMutantCellLine(), // MCL
																		// name
				cfg.getEsCellLogicalDb(), // Logical DB
				mclDAO.getKey().getKey(), // MCL object key
				new Integer(Constants.ESCELL_MGITYPE_KEY), // MGI type
				Boolean.FALSE, // Private?
				Boolean.TRUE // Preferred?
		);
		mclAccId.insert(loadStream);

		qcStats.record("SUMMARY", NUM_CELLLINES_CREATED);

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
		loadStream.close();

		// If any new MGI IDs have been generated during processing, the
		// ACC_AccessionMax table needs to be updated with the new maximum
		// value.
		AccessionLib.commitAccessionMax();

		TreeMap qc = null;
		Iterator iterator = null;

		
		// These alleles need to have their project ID updated
		if (alleleProjects.size() > 0) {
			Set entries = alleleProjects.entrySet();
			Iterator it = entries.iterator();
			while (it.hasNext()) {
				Map.Entry entry = (Map.Entry) it.next();
				String symbol = (String) entry.getKey();
				Set projects = (Set) entry.getValue();
				if (projects.size()==1) {
					System.out.println("Allele "+symbol+" could be updated automatically to project "+projects);
				} else {
					System.out.println("Allele "+symbol+" COULD NOT BE updated automatically. One (or more) of the MCLs have the old Project ID\n"+projects);
				}
			}			
		}

		// These alleles need to have their molecular note updated
		if (alleleNotes.size() > 0) {
			Set entries = alleleNotes.entrySet();
			Iterator it = entries.iterator();
			while (it.hasNext()) {
				Map.Entry entry = (Map.Entry) it.next();
				String symbol = (String) entry.getKey();
				Set notes = (Set) entry.getValue();
				if (notes.size()==1) {
					System.out.println("Allele note for "+symbol+" could be updated automatically to note:\n"+notes+"\n\n");
				} else {
					System.out.println("Allele note for "+symbol+" COULD NOT BE updated automatically. One (or more) of the MCLs have different notes:\n"+notes+"\n\n");
				}
			}			
		}

		
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

		// Empty line to the log files
		logger.logdInfo("\n", false);
		logger.logcInfo("\n", false);

		logger.logInfo("Process Finishing");
		return;
	}

}
