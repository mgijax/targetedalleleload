package org.jax.mgi.app.targetedalleleload;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.dbs.mgd.AccessionLib;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_CellLineDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_CellLineState;
import org.jax.mgi.dbs.mgd.dao.ALL_CellLineDAO;
import org.jax.mgi.dbs.mgd.loads.Alo.MutantCellLine;
import org.jax.mgi.dbs.mgd.lookup.ParentStrainLookupByParentKey;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;
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
 * @is a DLALoader for loading KOMP produced Alleles into the database
 * and associating them with appropriate marker annotations, strains,
 * molecular notes, and J-number references.  This process will also
 * create the official nomenclature and generate an official MGI ID 
 * for the allele.
 * @has nothing
 * @does reads the Allele input file (downloaded nightly from the KOMP
 * production centers websites) and determines, for each line in the file,
 * if the Allele already exists in the MGI database.  If it does not exist,
 * The targetedalleleload creates the allele.
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class TargetedAlleleLoad
extends DLALoader
{

	private RecordDataIterator iter = null;
	private KnockoutAlleleProcessor processor = null;
	private KnockoutAlleleInterpreter interp = null;
	private KnockoutAlleleFactory alleleFactory = null;
	private TargetedAlleleLoadCfg cfg = null;
	private SQLDataManager sqlDBMgr = null;
	private Timestamp currentTime = null;

	// Lookups
	private KOMutantCellLineLookup koMutantCellLineLookup = null;
	private AlleleLookupByKey alleleLookupByKey = null;
	private AlleleLookupByProjectId alleleLookupByProjectId = null;
	private AlleleLookupByMarker alleleLookupByMarker = null;
	private DerivationLookupByVectorCreatorParent derivationLookup = null;
	private VectorLookup vectorLookup = null;
	private MarkerLookupByMGIID markerLookup = null;
	private ParentStrainLookupByParentKey parentStrainLookupByParentKey = null;
	private StrainKeyLookup strainKeyLookup = null;

	private Set alleleProjectIdUpdated = null;
	private Set databaseProjectIds = null;
	private Set databaseCellLines = null;

	private AlleleLookupByCellLine alleleLookupByCellLine = null;

	protected QualityControlStatistics qcStatistics = new QualityControlStatistics();

	/**
	 * constructor
	 * @throws DLALoaderException thrown if the super class cannot be
	 * instantiated
	 */
	public TargetedAlleleLoad()
	throws MGIException
	{
		// Instance the configuration object
		cfg = new TargetedAlleleLoadCfg();    
		sqlDBMgr = SQLDataManagerFactory.getShared(SchemaConstants.MGD);

		Integer escLogicalDB = cfg.getEsCellLogicalDb();
		koMutantCellLineLookup = new KOMutantCellLineLookup(escLogicalDB);

		Integer projectLogicalDB = cfg.getProjectLogicalDb();

		// These lookups implement a Singleton pattern because 
		// they're shared across objects so updates in one object
		// should be reflected in the other objects
		alleleLookupByKey = AlleleLookupByKey.getInstance();
		alleleLookupByProjectId = AlleleLookupByProjectId.getInstance(projectLogicalDB);
		alleleLookupByMarker = AlleleLookupByMarker.getInstance();

		alleleLookupByCellLine = new AlleleLookupByCellLine();
		alleleLookupByCellLine.initCache();

		parentStrainLookupByParentKey = new ParentStrainLookupByParentKey();
		strainKeyLookup = new StrainKeyLookup();
		derivationLookup = new DerivationLookupByVectorCreatorParent();
		vectorLookup = new VectorLookup();
		markerLookup = new MarkerLookupByMGIID();

		alleleFactory = KnockoutAlleleFactory.getFactory();
		alleleProjectIdUpdated = new TreeSet();
		databaseProjectIds = new HashSet();
		databaseCellLines = new HashSet();

		// Add only cell lines appropriate for this pipeline and provider
		// to the QC pool (cell lines for other pipeline don't need QC
		// during this run)
		Iterator iterator = alleleLookupByProjectId.getKeySet().iterator();
		while (iterator.hasNext())
		{   
			String label = (String)iterator.next();
			HashMap a = alleleLookupByProjectId.lookup(label);
			HashMap b = (HashMap)a.values().toArray()[0];
			String s = "("+cfg.getPipeline()+")"+cfg.getProvider();
			if (((String)b.get("symbol")).indexOf(s) >= 0)
			{
				databaseProjectIds.add(label);
			}
		}

		// Add only cell lines appropriate for this pipeline and provider
		// to the QC pool (cell lines for other pipeline don't need QC
		// during this run)
		iterator = alleleLookupByCellLine.getKeySet().iterator();
		while (iterator.hasNext())
		{   
			String label = (String)iterator.next();
			KnockoutAllele a = alleleLookupByCellLine.lookup(label);
			String s = "("+cfg.getPipeline()+")"+cfg.getProvider();
			if (a.getSymbol().indexOf(s) >= 0)
			{
				databaseCellLines.add(label);
			}
		}

	}

	/**
	 * initialize the internal structures used by this class
	 * @assumes nothing
	 * @effects internal structures including database caching is initialized
	 */
	protected void initialize()
	throws MGIException
	{
		logger.logInfo("Reading input files");

		sqlDBMgr.setLogger(logger);
		logger.logdDebug("TargetedAlleleLoader sqlDBMgr.server " + 
				sqlDBMgr.getServer());
		logger.logdDebug("TargetedAlleleLoader sqlDBMgr.database " + 
				sqlDBMgr.getDatabase());

		InputDataFile inputFile = new InputDataFile(cfg);

		// Get an appropriate Interpreter for the file
		interp = alleleFactory.getInterpreter();

		// Get an Iterator for going through the input file
		iter = inputFile.getIterator(interp);

		// Get an appropriate Processor for the records in the file
		processor = alleleFactory.getProcessor();

	}

	/**
	 * @does The input files are in different formats, so read
	 * each of the files according to their input format objects 
	 * to provide a collection of consitent Allele objects to the
	 * run process
	 * @assumes nothing
	 * @effects nothing
	 * @throws nothing
	 */
	protected void preprocess()
	throws MGIException
	{
		qcStatistics.record("SUMMARY", "Number of alleles created", 0);
		qcStatistics.record("SUMMARY", "Number of mutant cell lines created", 0);
		return;
	}

	/**
	 * read the knockout allele input file and run the process that creates
	 * new alleles in MGD
	 * @assumes nothing
	 * @effects the data will be created for loading Alleles and associated
	 * ES Cell lines into the database
	 * @throws MGIException thrown if there is an error accessing the input
	 * file or writing output data
	 */
	protected void run()
	throws MGIException
	{
		// Keep track of which alleles we've updated the notes for
		// so we only update it once
		HashSet alreadyProcessed = new HashSet();
		HashSet alleleNoteUpdated = new HashSet();

		// For each input record
		while(iter.hasNext())
		{
			// Instance the input records
			KnockoutAlleleInput in = null;

			try
			{
				in = (KnockoutAlleleInput)iter.next();
			}
			catch (MGIException e)
			{
				logger.logdInfo(e.toString(), true);
				qcStatistics.record("WARNING", "Number of input records that were unable to be processed");
				continue;
			}

			if (alreadyProcessed.contains(in.getMutantCellLine()))
			{
				String m = "Multiple input records for: ";
				m += in.getMutantCellLine() + "\n";
				logger.logdInfo(m, false);
				qcStatistics.record("WARNING", "Number of duplicate cell line records in input file");
				continue;
			}
			else
			{
				alreadyProcessed.add(in.getMutantCellLine());
			}

			// Keep track of the projects and mutant cell lines we've already seen
			databaseProjectIds.remove(in.getProjectId().toLowerCase());
			databaseCellLines.remove(in.getMutantCellLine().toLowerCase());

			// Construct the allele from the input record
			KnockoutAllele constructed = null;

			try
			{
				constructed = processor.process(in);
			}
			catch (KeyNotFoundException e)
			{
				qcStatistics.record("ERROR", "Number of alleles that were not able to be constructed");

				String m = "Allele creation error, check: ";
				m += in.getMutantCellLine() + "\n";
				m += e.getMessage();
				logger.logdInfo(m, false);
				m = "An error occured while processing the input record for: ";
				m += in.getMutantCellLine() + "\n";
				m += "The provider might be using a secondary MGI ID (";
				m += in.getGeneId();
				m += ")\n";
				logger.logcInfo(m, false);
				continue;
			}
			catch (MGIException e)
			{
				qcStatistics.record("ERROR", "Number of alleles that were not able to be constructed");

				String m = "General error, skipping record: ";
				m += in.getMutantCellLine() + "\n";
				m += in + "\n";
				m += e.getMessage();
				logger.logdInfo(m, false);
				continue;                
			}


			if (constructed == null)
			{
				qcStatistics.record("ERROR", "Number of alleles that were not able to be constructed");

				String m = "Allele creation error, check: ";
				m += in.getMutantCellLine();
				logger.logdInfo(m, false);
				continue;                    
			}




			// For each Mutant cell line (an input record corresponds to a Mutant cell line)
			//     * Does the Mutant Cell Line record exist in the cache (database or recently created)?
			String currentCellLine = in.getMutantCellLine();

			MutantCellLine esCell = koMutantCellLineLookup.lookup(currentCellLine);
			if (esCell != null)
			{
				// Mutant ES Cell found in database, check the alleles

				// QC check the allele the MCL is attached to
				KnockoutAllele existing = alleleLookupByCellLine.lookup(currentCellLine);
				if (existing == null)
				{
					qcStatistics.record("ERROR", "Number of cellines that cannot find associated allele");
					String s = "Skipping record. Cannot find allele for:";
					s += "\n ES cell line: " + currentCellLine;
					s += "\n";
					logger.logdInfo(s, true);
					continue;
				}

				// Compare the notes to see if anything changed.
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
				if (!existingGeneKey.equals(constructedGeneKey))
				{
					String noteMsg = "\nMARKER CHANGED (not updating)\n";
					noteMsg += "Mutant Cell line: " + in.getMutantCellLine();
					noteMsg += "\nOld: " + existing.getSymbol();
					noteMsg += "\nNew: " + constructed.getSymbol();
					noteMsg += "\n";
					logger.logcInfo(noteMsg, false);
					logger.logdInfo(noteMsg, false);
					qcStatistics.record("SUMMARY", "Number of cell lines that changed marker, skipped");

					// This mutant cell line has had a major change, don't bother QC
					// checking the rest of the values
					continue;
				}

				// Check the es cell project ID versus the allele project ID
				// If the project changed, likely the allele type needs to
				// change as well. That condition is hanled below
				if (!existing.getProjectId().equals(constructed.getProjectId()))
				{
					// Record that this mutant cell line had a project ID change
					String m = "";
					//MCL ID, the allele it's currently associated with, the existing project ID, and the new project ID
					m += existing.getSymbol() + "\t";
					m += existing.getProjectId() + "\t";
					m += constructed.getProjectId() + "\t";
					m += in.getMutantCellLine();

					alleleProjectIdUpdated.add(m);
					continue;
				}

				// If the marker hasn't changed, then check if the symbol
				// changed at all... if it did, that means the type changed
				// This check should never catch Regeneron alleles because
				// they are all of the same type
				// (from xx<tm1a(KOMP)Wtsi> to xx<tm1e(KOMP)Wtsi> or etc.)
				if (!existing.getSymbol().equals(constructed.getSymbol()))
				{
					String noteMsg = "\nTYPE CHANGED\n";
					noteMsg += "Mutant Cell line: " + in.getMutantCellLine();
					noteMsg += "\nOld allele: " + existing.getSymbol();
					noteMsg += "\nNew allele: " + constructed.getSymbol();
					noteMsg += "\n";
					logger.logcInfo(noteMsg, false);
					qcStatistics.record("SUMMARY", "Number of cell lines that changed type");

					// Change the allele this MCL is currently attached to
					String query = "DELETE FROM ALL_Allele_Cellline";
					query += " WHERE _Allele_key = "+ existing.getKey();
					query += " AND _MutantCellLine_key = "+ esCell.getMCLKey();
					sqlDBMgr.executeUpdate(query);

					// lookup existing alleles for this project
					String projectId = in.getProjectId();
					HashMap alleles = alleleLookupByProjectId.lookup(projectId);

					if(alleles != null)
					{
						// Try to get the allele identified by the constructed symbol
						HashMap allele = (HashMap)alleles.get(constructed.getSymbol());

						// If we found the allele, we can attach the MCL to it
						if (allele != null)
						{
							// Found an allele with this same symbol
							Integer alleleKey = (Integer)allele.get("key");
							associateCellLineToAllele(alleleKey, esCell.getMCLKey());
							continue;
						}
					}

					// The MCL Didn't match any alleles, create an allele and
					// attach the MCL to it.
					createAllele(constructed, in, alleles);
					associateCellLineToAllele(constructed.getKey(), esCell.getMCLKey());

					// This mutant cell line has had a major change, don't bother QC
					// checking the rest of the values
					continue;
				}

				// Check the derivation (this implicitly checks the 
				// parental cell line, the creator, the vector and the allele
				// type)
				if(!esCell.getDerivationKey().equals(getDerivationKey(in)))
				{
					String noteMsg = "\nDERIVATION CHANGED\n";
					noteMsg += "Mutant Cell line: " + in.getMutantCellLine();
					noteMsg += "\nOld derivation key: " + esCell.getDerivationKey();
					noteMsg += "\nNew derivation key: " + getDerivationKey(in);
					noteMsg += "\n";
					logger.logcInfo(noteMsg, false);
					qcStatistics.record("SUMMARY", "Number of cell lines that changed derivation");					
				}

				// Only check the note content if the allele type and marker 
				// hasn't changed.  If the type changed, then by definition 
				// the note changed because different types use different 
				// note tempates
				if (!existingNote.equals(constructedNote) && 
						!alleleNoteUpdated.contains(existing.getSymbol()))
				{
					alleleNoteUpdated.add(existing.getSymbol());
					String noteMsg = "\nMOLECULAR NOTE CHANGED\n";

					// If the note was entered by this load, go ahead and
					// update the note to reflect the current note,
					// otherwise, a curator updated the note, so we 
					// shouldn't update it.
					Integer jobStreamKey = cfg.getJobStreamKey();
					Integer noteModifiedBy = existing.getNoteModifiedByKey();
					if (noteModifiedBy == null || 
							cfg.getOverwriteNote() || 
							(jobStreamKey.compareTo(noteModifiedBy) == 0))
					{
						noteMsg += "Allele: " + existing.getSymbol() + "\n";
						noteMsg += "Mutant Cell line: " + currentCellLine;
						noteMsg += "\n\nCurrent/New note:\n";
						noteMsg += existing.getNote() + "\n";
						noteMsg += constructed.getNote() + "\n";

						// If a note exists
						// Delete the existing note
						if (existing.getNoteKey() != null)
						{
							String query = "DELETE FROM MGI_Note WHERE ";
							query += "_Note_key = ";
							query += existing.getNoteKey();

							sqlDBMgr.executeUpdate(query);

							// Attach the new note to the existing allele
							String newNote = constructed.getNote();
							existing.updateNote(loadStream, newNote);
							qcStatistics.record("SUMMARY", "Number of alleles that had molecular notes updated");
						}
						else
						{
							noteMsg += "!!! Could not find existing note key NOT UPDATING\n";
						}
					}
					else
					{
						noteMsg += "Allele: " + existing.getSymbol();
						noteMsg += " (not updating)\n";
						noteMsg += "Mutant Cell line: " + currentCellLine;
						noteMsg += "\nJobstream: " + jobStreamKey;
						noteMsg += "\nModifiedBy: " + noteModifiedBy;
						noteMsg += "\nCurrent/New note:\n";
						noteMsg += existing.getNote() + "\n";
						noteMsg += constructed.getNote() + "\n";
						qcStatistics.record("WARNING", "Number of alleles that need to have molecular notes updated by curator");
					}
					logger.logcInfo(noteMsg, false);
				}

				// Go on to the next mutant cell line                
				continue;
			}
			else
			{
				// Mutant ES Cell NOT found in database

				Integer mclKey = null;
				try
				{
					mclKey = createMutantCellLine(in);
				} catch (MGIException e)
				{
					qcStatistics.record("ERROR", "Number of mutant cell lines that were not able to be constructed");
					String m = "Exception creating mutant cell line, skipping record: ";
					m += in.getMutantCellLine() + "\n";
					m += in + "\n";
					m += e.getMessage();
					logger.logdInfo(m, false);
					continue;
				}
				if(mclKey == null)
				{
					qcStatistics.record("ERROR", "Number of mutant cell lines that were not able to be constructed");
					String m = "Mutant cell line not created, skipping record: ";
					m += in.getMutantCellLine() + "\n";
					m += in + "\n";
					logger.logdInfo(m, false);
					continue;
				}

				// lookup existing alleles for this project
				String projectId = in.getProjectId();
				HashMap alleles = alleleLookupByProjectId.lookup(projectId);

				if(alleles != null)
				{
					// Try to get the allele identified by the constructed symbol
					HashMap allele = (HashMap)alleles.get(constructed.getSymbol());

					// If we found the allele, we can attach the MCL to it
					if (allele != null)
					{
						// Found an allele with this same symbol
						Integer alleleKey = (Integer)allele.get("key");
						//KnockoutAllele existing = koAlleleLookup.lookup(alleleKey);

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

	protected Integer getDerivationKey(KnockoutAlleleInput in)
	throws MGIException
	{

		// Find the derivation key for this ES Cell
		String cassette = in.getCassette();
		String dCompoundKey = vectorLookup.lookup(cassette);

		dCompoundKey +=  "|" + cfg.getCreatorKey();

		try
		{
			String parent = in.getParentCellLine();
			dCompoundKey +=  "|" + cfg.getParentalKey(parent);
		}
		catch (ConfigException e)
		{
			String s = in.getParentCellLine();
			s += " Does not exist in CFG file! Skipping record";
			logger.logdInfo(s, true);
			qcStatistics.record("ERROR", "Number of derivations not found");
			throw new MGIException("Cannot find parental cell line key for "+in.getParentCellLine());
		}

		String aType = in.getMutationType();
		dCompoundKey += "|" + Constants.MUTATION_TYPE_KEYS.get(aType);
		
		Integer derivationKey = derivationLookup.lookup(dCompoundKey);

		if (derivationKey == null)
		{
			String s = "Skipping record. Cannot find derivation for:";
			s += "\n Vector: " + cassette;
			s += "\n Creator Key: " + cfg.getCreatorKey();
			s += "\n Parental: " + in.getParentCellLine();
			s += "\n";
			logger.logdInfo(s, true);
			qcStatistics.record("ERROR", "Number of derivations not found");
			throw new MGIException("Cannot find derivation for "+in.getMutantCellLine());
		}

		return derivationKey;
	}

	protected Integer createMutantCellLine(KnockoutAlleleInput in)
	throws MGIException
	{
		Integer derivationKey = getDerivationKey(in);
		
		// Create the mutant cell line
		MutantCellLine mcl = new MutantCellLine();
		mcl.setCellLine(in.getMutantCellLine());
		mcl.setCellLineTypeKey(new Integer(Constants.ESCELL_TYPE_KEY));
		mcl.setDerivationKey(derivationKey);
		mcl.setIsMutant(new Boolean(true));

		// Get the stain key of the parental cell line
		Integer parentalCellLineKey = cfg.getParentalKey(in.getParentCellLine());
		String strainName = parentStrainLookupByParentKey.lookup(parentalCellLineKey);
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
		AccessionId mclAccId = new AccessionId(
				in.getMutantCellLine(),         // MCL name
				cfg.getEsCellLogicalDb(),       // Logical DB
				mclDAO.getKey().getKey(),       // MCL object key
				new Integer(Constants.ESCELL_MGITYPE_KEY),   // MGI type
				Boolean.FALSE,  // Private?
				Boolean.TRUE    // Preferred?
		);
		mclAccId.insert(loadStream);

		qcStatistics.record("SUMMARY", "Number of mutant cell lines created");

		return mclDAO.getKey().getKey();
	}

	/**
	 * Must close the BCP file stream and commit the new maximum MGI \
	 * number back to the database
	 * @assumes nothing
	 * @effects Updates the database row in ACC_AccessionMax for MGI IDs
	 * @throws MGIException if something goes wrong
	 */
	protected void associateCellLineToAllele(Integer alleleKey, Integer celllineKey)
	throws MGIException
	{
		// Create the allele to cell line association
		ALL_Allele_CellLineState aclState = new ALL_Allele_CellLineState();
		aclState.setMutantCellLineKey(celllineKey);
		aclState.setAlleleKey(alleleKey);
		ALL_Allele_CellLineDAO aclDAO = new ALL_Allele_CellLineDAO(aclState);
		loadStream.insert(aclDAO);
	}

	protected KnockoutAllele createAllele(KnockoutAllele constructed, KnockoutAlleleInput in, HashMap alleles)
	throws MGIException
	{
		constructed.insert(loadStream);

		// Create the allele with all the data from this row
		HashMap allele = new HashMap();
		allele.put("projectid", in.getProjectId());
		allele.put("key", constructed.getKey());
		allele.put("symbol", constructed.getSymbol());
		Vector mcls = new Vector();
		mcls.add(in.getMutantCellLine());
		allele.put("mutantCellLines", mcls);
		allele.put("parentCellLine", in.getParentCellLine());
		allele.put("parentCellLineKey", cfg.getParentalKey(in.getParentCellLine()));

		if (alleles == null)
		{
			alleles = new HashMap();
		}

		// add the new allele to the map
		alleles.put(constructed.getSymbol(), allele);

		alleleLookupByProjectId.addToCache(in.getProjectId(), alleles);

		// Finally, add the newly created allele to the cache
		alleleLookupByKey.addToCache(constructed.getKey(), constructed);

		Marker mrk = markerLookup.lookup(in.getGeneId());
		String markerSymbol = mrk.getSymbol();

		HashSet alleleSet = null;
		alleleSet = alleleLookupByMarker.lookup(markerSymbol);
		if (alleleSet == null)
		{
			alleleSet = new HashSet();
		}
		alleleSet.add(constructed.getKey());
		alleleLookupByMarker.addToCache(markerSymbol, alleleSet);

		qcStatistics.record("SUMMARY", "Number of alleles created");

		return constructed;
	}

	/**
	 * Must close the BCP file stream and commit the new maximum MGI
	 * number back to the database.  Print out the QC statistics
	 * @assumes nothing
	 * @effects Updates the database row in ACC_AccessionMax for MGI IDs
	 * @throws MGIException if something goes wrong
	 */
	protected void postprocess()
	throws MGIException
	{
		loadStream.close();

		// If any new MGI IDs have been generated during processing, the
		// ACC_AccessionMax table needs to be updated with the new maximum
		// value.
		AccessionLib.commitAccessionMax();

		TreeMap qc = null;
		Iterator iterator = null;

		// Print out the error statistics
		qc =(TreeMap)qcStatistics.getStatistics().get("ERROR");

		if (qc != null)
		{
			logger.logdInfo("\nERRORS", false);
			logger.logpInfo("\nERRORS", false);

			iterator = qc.keySet().iterator();
			while (iterator.hasNext())
			{
				String label = (String)iterator.next();
				logger.logdInfo(label + ": " + qc.get(label), false);
				logger.logpInfo(label + ": " + qc.get(label), false);
			}
		}

		// Print out the warning statistics
		qc =(TreeMap)qcStatistics.getStatistics().get("WARNING");

		if (qc != null)
		{
			logger.logdInfo("\nWARNINGS", false);
			logger.logpInfo("\nWARNINGS", false);

			iterator = qc.keySet().iterator();
			while (iterator.hasNext())
			{
				String label = (String)iterator.next();
				logger.logdInfo(label + ": " + qc.get(label), false);
				logger.logpInfo(label + ": " + qc.get(label), false);
			}            
		}

		// Print out the summary statistics
		qc =(TreeMap)qcStatistics.getStatistics().get("SUMMARY");

		if (qc != null)
		{
			logger.logdInfo("\nSUMMARY", false);
			logger.logpInfo("\nSUMMARY", false);

			iterator = qc.keySet().iterator();
			while (iterator.hasNext())
			{
				String label = (String)iterator.next();
				logger.logdInfo(label + ": " + qc.get(label), false);
				logger.logpInfo(label + ": " + qc.get(label), false);
			}            
		}

		if (databaseCellLines.size() > 0 || databaseProjectIds.size() > 0 || alleleProjectIdUpdated.size() > 0)
		{
			logger.logdInfo("Number of project IDs that exist in DB but not in file: " + databaseProjectIds.size(), false);
			logger.logpInfo("Number of project IDs that exist in DB but not in file: " + databaseProjectIds.size(), false);

			logger.logdInfo("Number of celllines that exist in DB but not in file: " + databaseCellLines.size(), false);
			logger.logpInfo("Number of celllines that exist in DB but not in file: " + databaseCellLines.size(), false);

			logger.logdInfo("Number of alleles that changed project IDs: " + alleleProjectIdUpdated.size(), false);
			logger.logpInfo("Number of alleles that changed project IDs: " + alleleProjectIdUpdated.size(), false);

			logger.logdInfo("\nANOMALIES", false);
			logger.logcInfo("\nANOMALIES", false);
		}

		if (databaseCellLines.size() > 0)
		{
			logger.logdInfo("\nCelllines that exist in the database, but not in the input file: " + databaseCellLines.size(), false);
			logger.logcInfo("\nCelllines that exist in the database, but not in the input file: " + databaseCellLines.size(), false);

			logger.logdInfo("\nAllele\tExisting Project\tES Cell Line", false);
			logger.logcInfo("\nAllele\tExisting Project\tES Cell Line", false);

			iterator = databaseCellLines.iterator();
			Set s = new TreeSet();
			while (iterator.hasNext())
			{   
				String label = (String)iterator.next();
				KnockoutAllele a = alleleLookupByCellLine.lookup(label);
				s.add(a.getSymbol() + "\t" + a.getProjectId() + "\t" + label.toUpperCase());
			}

			iterator = s.iterator();
			while (iterator.hasNext())
			{
				String lbl = (String)iterator.next();
				logger.logdInfo(lbl, false);
				logger.logcInfo(lbl, false);
			}            
		}

		if (databaseProjectIds.size() > 0)
		{
			logger.logdInfo("\nProject IDs that exist in MGI, but not in the input file: "+databaseProjectIds.size(), false);
			logger.logcInfo("\nProject IDs that exist in MGI, but not in the input file: "+databaseProjectIds.size(), false);

			logger.logdInfo("\nAllele\tExisting Project", false);
			logger.logcInfo("\nAllele\tExisting Project", false);

			iterator = databaseProjectIds.iterator();
			Set s = new TreeSet();
			while (iterator.hasNext())
			{   
				String label = (String)iterator.next();
				HashMap hmA = alleleLookupByProjectId.lookup(label);
				if (hmA != null)
				{   
					Set entries = hmA.entrySet();
					Iterator aIt = entries.iterator();
					while (aIt.hasNext())
					{   
						Map.Entry entry = (Map.Entry) aIt.next();
						HashMap tmpAllele = (HashMap) entry.getValue();
						s.add((String) tmpAllele.get("symbol") + "\t" + label);
					}
				}
			}

			iterator = s.iterator();
			while (iterator.hasNext())
			{   
				String lbl = (String)iterator.next();
				logger.logdInfo(lbl, false);
				logger.logcInfo(lbl, false);
			}
		}

		if (alleleProjectIdUpdated.size() > 0)
		{
			logger.logdInfo("\nAlleles that have had project ID changes: "+alleleProjectIdUpdated.size(), false);
			logger.logcInfo("\nAlleles that have had project ID changes: "+alleleProjectIdUpdated.size(), false);

			logger.logdInfo("\nAllele\tExisting Project\tNew Project\tMCL", false);
			logger.logcInfo("\nAllele\tExisting Project\tNew Project\tMCL", false);

			iterator = alleleProjectIdUpdated.iterator();
			while (iterator.hasNext())
			{
				String label = (String)iterator.next();
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

