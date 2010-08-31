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
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
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
	private static final String NUM_BAD_CELLLINE_PROCESSING = "Number of mutant cell lines that were not able to be constructed";
	private static final String NUM_ALLELES_NEED_NOTE_CHANGE = "Number of alleles that need to have molecular notes updated by curator";
	private static final String NUM_ALLELES_NOTE_CHANGE = "Number of alleles that had molecular notes updated";
	private static final String NUM_CELLLINES_CHANGE_TYPE = "Number of cell lines that changed type";
	private static final String NUM_CELLLINES_CHANGED_DERIVATION = "Number of cell lines that changed derivation";
	private static final String NUM_CELLLINES_CHANGED_PIPELINE = "Number of cell lines that changed IKMC groups";
	private static final String NUM_CHANGED_MARKER = "Number of cell lines that changed marker, skipped";
	private static final String NUM_CELLINES_MISSING_ALLELE = "Number of cellines that cannot find associated allele";
	private static final String NUM_BAD_ALLELE_PROCESSING = "Number of alleles that were not able to be constructed";
	private static final String NUM_MISSING_PARENT = "Number of input records missing parental cell line";
	private static final String NUM_CELLLINES_CREATED = "Number of mutant cell lines created";
	private static final String NUM_ALLELES_CREATED = "Number of alleles created";
	private static final String NUM_DUPLICATE_INPUT_REC = "Number of duplicate cell line records in input file";
	private static final String NUM_BAD_INPUT_REC = "Number of input records that were unable to be processed";
	private static final String NUM_MCL_CHANGED_DERIVATION = "Number of mutant cell lines that changed derivations";
	private static final String NUM_MCL_CHANGED_ALLELE = "Number of mutant cell lines that changed allele associations";

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

	//
	private Set alleleProjectIdUpdated;
	private Set databaseProjectIds;
	private Set databaseCellLines;

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

		alleleLookupByCellLine = new AlleleLookupByCellLine();
		alleleLookupByCellLine.initCache();

		parentStrainLookupByParentKey = new ParentStrainLookupByParentKey();
		strainKeyLookup = new StrainKeyLookup();
		derivationLookup = new DerivationLookupByVectorCreatorParentType();
		vectorLookup = new VectorLookup();
		markerLookup = new MarkerLookupByMGIID();
		vocTermLookup = new VocabTermLookup();
		cellLineNameLookupByKey = new CellLineNameLookupByKey();
		cellLineStrainKeyLookupByCellLineKey = new CellLineStrainKeyLookupByCellLineKey();
		strainNameLookup = new StrainNameLookup();

		alleleFactory = KnockoutAlleleFactory.getFactory();
		alleleProjectIdUpdated = new TreeSet();
		databaseProjectIds = new HashSet();
		databaseCellLines = new HashSet();

		// This contains the combination of pipeline and provider
		// that this load is currently loading.
		String loadProvider = "(" + cfg.getPipeline() + ")" + cfg.getProvider();

		// Add only cell lines appropriate for this pipeline and provider
		// to this pool (cell lines for other pipeline don't need QC
		// during this run)
		Iterator iterator = alleleLookupByProjectId.getKeySet().iterator();
		while (iterator.hasNext()) {
			String label = (String) iterator.next();
			Map a = alleleLookupByProjectId.lookup(label);

			// All alleles in the project belong to the same pipelie/provider
			// combination, so it's ok to just look at the first one.
			Map b = (Map) a.values().toArray()[0];

			// If the allele belongs to the same combination of pipeline
			// and provider, then add it to the QC check pool
			if (((String) b.get("symbol")).indexOf(loadProvider) >= 0) {
				databaseProjectIds.add(label);
			}
		}

		// Add only cell lines appropriate for this pipeline and provider
		// to the QC pool (cell lines for other pipeline don't need QC
		// during this run)
		iterator = alleleLookupByCellLine.getKeySet().iterator();
		while (iterator.hasNext()) {
			String label = (String) iterator.next();
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
		// pattern to determine the IKMC pipeline that produced
		// the allele. Examples:
		// 0610009D07Rik<tm1a(EUCOMM)Wtsi> = EUCOMM
		// Dullard<tm1(KOMP)Vlcg> = KOMP
		Pattern pipelinePattern = Pattern
				.compile(".*tm\\d{1,2}[ae]{0,1}\\((.*)\\).*");

		// Keep track of which alleles we've updated the notes for
		// so we only update it once
		Set alreadyProcessed = new HashSet();
		Set alleleNoteUpdated = new HashSet();

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

			// For each Mutant cell line (an input record corresponds to a
			// Mutant cell line)
			String currentCellLine = in.getMutantCellLine();

			// Does the Mutant Cell Line record exist in the
			// cache (database or recently created)?
			MutantCellLine esCell = koMutantCellLineLookup
					.lookup(currentCellLine);
			if (esCell != null) {
				// Mutant ES Cell found in MGI, check the associated allele

				// Find the existing associated allele
				KnockoutAllele existing = alleleLookupByCellLine
						.lookup(currentCellLine);

				// If the associated allele can't be found, there's a major
				// problem. The caches are out of synch, of the cell line to
				// allele association is missing. Regardless, we can't
				// process this record further, report the error and continue
				if (existing == null) {
					qcStats.record("ERROR", NUM_CELLINES_MISSING_ALLELE);
					String s = "Cell line "
							+ currentCellLine
							+ " found in database. Cannot find associated allele\n";
					logger.logdInfo(s, true);
					continue;
				}

				// Gather the values to be compared between the existing
				// allele and the allele that was constructed from the
				// input record
				String existingNote = existing.getNote();
				existingNote = existingNote.replaceAll("\\n", "");
				existingNote = existingNote.replaceAll(" ", "");
				String constructedNote = constructed.getNote();
				constructedNote = constructedNote.replaceAll("\\n", "");
				constructedNote = constructedNote.replaceAll(" ", "");

				Integer existingGeneKey = existing.getMarkerKey();
				Integer constructedGeneKey = constructed.getMarkerKey();

				// Check the allele to marker association, if it has changed,
				// report to the log for manual curation.
				if (!existingGeneKey.equals(constructedGeneKey)) {
					String noteMsg = "\nMARKER CHANGED (not updating)\n"
							+ "Mutant Cell line: " + in.getMutantCellLine()
							+ "\n" + "Old: " + existing.getSymbol() + "\n"
							+ "New: " + constructed.getSymbol() + "\n";
					logger.logcInfo(noteMsg, false);
					logger.logdInfo(noteMsg, false);
					qcStats.record("SUMMARY", NUM_CHANGED_MARKER);

					// This mutant cell line has had a major change, don't
					// bother QC
					// checking the rest of the values
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

					alleleProjectIdUpdated.add(m);
					continue;
				}

				// Check if IKMC pipeline changed
				// (from xx<tm1a(KOMP)Wtsi> to xx<tm1a(EUCOMM)Wtsi> or etc.)
				String existingIkmcGroup = null;
				String constructedIkmcGroup = null;

				Matcher regexMatcher = pipelinePattern.matcher(existing
						.getSymbol());
				if (regexMatcher.find()) {
					existingIkmcGroup = regexMatcher.group(1);
				}

				regexMatcher = pipelinePattern.matcher(constructed.getSymbol());
				if (regexMatcher.find()) {
					constructedIkmcGroup = regexMatcher.group(1);
				}

				// If the mutant cell line changed groups, go ahead and
				// change the association, but report it for manual review
				if (!existingIkmcGroup.equals(constructedIkmcGroup)) {
					// The input file says that this allele changed which
					// IKMC group produced it
					String noteMsg = "\nIKMC GROUP CHANGED\n"
							+ "Mutant Cell line: " + in.getMutantCellLine()
							+ "\nOld allele: " + existing.getSymbol()
							+ "\nNew allele: " + constructed.getSymbol() + "\n";

					// Change the allele this cellline is currently attached to
					changeMutantCellLineAssociation(in, esCell, existing,
							constructed);

					logger.logcInfo(noteMsg, false);
					qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_PIPELINE);
					continue;
				}

				// Check the derivation (this implicitly checks the
				// parental cell line, the creator, the vector and the allele
				// type)
				if (!esCell.getDerivationKey().equals(getDerivationKey(in))) {
					// String noteMsg = "\nDERIVATION CHANGED\n";
					// noteMsg += "Mutant Cell line: " + in.getMutantCellLine();
					// noteMsg += "\nOld derivation key: " +
					// esCell.getDerivationKey();
					// noteMsg += "\nNew derivation key: " +
					// getDerivationKey(in);
					// noteMsg += "\n";
					// logger.logdInfo(noteMsg, false);

					// A change in cellline derivation _usually_ means the
					// associated allele must change as well.
					Integer derivationKey = getDerivationKey(in);
					logger.logcInfo(
							"Changed derivation key for "
									+ in.getMutantCellLine() + " from "
									+ esCell.getDerivationKey() + " to "
									+ derivationKey, false);

					changeDerivationKey(derivationKey, esCell);
					changeMutantCellLineAssociation(in, esCell, existing,
							constructed);
					qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_DERIVATION);
					continue;
				}

				// If the marker hasn't changed, then check if the symbol
				// changed at all... if it did, that means the type changed
				// This check should never catch Regeneron alleles because
				// they are all of the same type
				// (from xx<tm1a(KOMP)Wtsi> to xx<tm1e(KOMP)Wtsi> or etc.)
				if (!existing.getSymbol().equals(constructed.getSymbol())) {
					String noteMsg = "\nTYPE CHANGED\n" + "Mutant Cell line: "
							+ in.getMutantCellLine() + "\nOld allele: "
							+ existing.getSymbol() + "\nNew allele: "
							+ constructed.getSymbol() + "\n";

					// Change the allele this cellline is currently attached to
					changeMutantCellLineAssociation(in, esCell, existing,
							constructed);

					logger.logcInfo(noteMsg, false);
					qcStats.record("SUMMARY", NUM_CELLLINES_CHANGE_TYPE);

					// This mutant cell line has had a major change,
					// don't bother QC checking the rest of the values
					continue;
				}

				// Only check the note content if the allele type and marker
				// hasn't changed. If the type changed, then by definition
				// the note changed because different types use different
				// note templates
				if (!existingNote.equals(constructedNote)
						&& !alleleNoteUpdated.contains(existing.getSymbol())) {
					alleleNoteUpdated.add(existing.getSymbol());
					String noteMsg = "\nMOLECULAR NOTE CHANGED\n";

					// If the note was entered by this load,
					// update the note to reflect the current note,
					// otherwise, a curator updated the note, so we
					// shouldn't update it.
					Integer jobStreamKey = cfg.getJobStreamKey();
					Integer noteModifiedBy = existing.getNoteModifiedByKey();
					if (noteModifiedBy == null || cfg.getOverwriteNote()
							|| (jobStreamKey.compareTo(noteModifiedBy) == 0)) {
						noteMsg += "Allele: " + existing.getSymbol() + "\n"
								+ "Mutant Cell line: " + currentCellLine
								+ "\n\nCurrent/New note:\n"
								+ existing.getNote() + "\n"
								+ constructed.getNote() + "\n";

						// If a note exists
						// Delete the existing note
						if (existing.getNoteKey() != null) {
							String query = "DELETE FROM MGI_Note "
									+ "WHERE _Note_key = "
									+ existing.getNoteKey();

							sqlDBMgr.executeUpdate(query);

							// Attach the new note to the existing allele
							String newNote = constructed.getNote();
							existing.updateNote(loadStream, newNote);
							qcStats.record("SUMMARY", NUM_ALLELES_NOTE_CHANGE);
						} else {
							noteMsg += "!!! Could not find existing note key. Not updating\n";
						}
					} else {
						noteMsg += "Allele: " + existing.getSymbol()
								+ " (not updating)\n" + "Mutant Cell line: "
								+ currentCellLine + "\nJobstream: "
								+ jobStreamKey + "\nModifiedBy: "
								+ noteModifiedBy + "\nCurrent/New note:\n"
								+ existing.getNote() + "\n"
								+ constructed.getNote() + "\n";
						qcStats.record("WARNING", NUM_ALLELES_NEED_NOTE_CHANGE);
					}
					logger.logcInfo(noteMsg, false);
				}

				// Go on to the next mutant cell line
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
			// String s = "Skipping record. Cannot find derivation for:";
			// s += "\n Vector: " + cassette;
			// s += "\n Creator Key: " + cfg.getCreatorKey();
			// s += "\n Parental: " + in.getParentCellLine();
			// s += "\n";
			// logger.logdInfo(s, true);
			// qcStats.record("ERROR", NUM_DERIVATIONS_NOT_FOUND);
			// throw new MGIException("Cannot find derivation for "
			// + in.getMutantCellLine());

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
		qcStats.record("WARNING", NUM_MCL_CHANGED_DERIVATION);
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
		qcStats.record("WARNING", NUM_MCL_CHANGED_ALLELE);
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

	/**
	 * Must close the BCP file stream and commit the new maximum MGI \ number
	 * back to the database
	 * 
	 * @assumes nothing
	 * @effects Updates the database row in ACC_AccessionMax for MGI IDs
	 * @throws MGIException
	 *             if something goes wrong
	 */
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
