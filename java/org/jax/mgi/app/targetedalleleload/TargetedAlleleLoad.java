package org.jax.mgi.app.targetedalleleload;

import java.io.File;
import java.lang.Integer;
import java.util.HashSet;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Vector;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.config.InputDataCfg;

import org.jax.mgi.shr.ioutils.InputDataFile;
import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.ioutils.RecordDataIterator;
import org.jax.mgi.shr.datetime.DateTime;
import org.jax.mgi.shr.dla.loader.DLALoader;

import org.jax.mgi.dbs.mgd.AccessionLib;
import org.jax.mgi.dbs.SchemaConstants;

import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.dla.loader.DLALoaderException;
import org.jax.mgi.shr.cache.KeyNotFoundException;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;

import org.jax.mgi.dbs.mgd.lookup.ParentStrainLookupByParentKey;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;

import org.jax.mgi.dbs.mgd.loads.Alo.MutantCellLine;
import org.jax.mgi.dbs.mgd.dao.ALL_CellLineDAO;

import org.jax.mgi.dbs.mgd.dao.ALL_Allele_CellLineState;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_CellLineDAO;



/**
 * @is a DLALoader for loading KOMP produced Alleles into the database
 * and associating them with appropriate marker annotations, strains,
 * moleculare notes, and J-number references.  This process will also
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
    private KnockoutAlleleLookup koAlleleLookup = null;
    private AlleleLookupByProjectId alleleLookpuByProjectId = null;
    private AlleleLookupByMarker alleleLookupByMarker = null;
    private DerivationLookupByVectorCreatorParent derivationLookup = null;
    private VectorLookup vectorLookup = null;
    private MarkerLookupByMGIID markerLookup = null;
    private ParentStrainLookupByParentKey parentStrainLookupByParentKey = null;
    private StrainKeyLookup strainKeyLookup = null;
    
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
        Integer projectLogicalDB = cfg.getProjectLogicalDb();

        alleleLookpuByProjectId = new AlleleLookupByProjectId(projectLogicalDB);
        alleleLookupByMarker = new AlleleLookupByMarker(projectLogicalDB);
        koMutantCellLineLookup = new KOMutantCellLineLookup(escLogicalDB);
        koAlleleLookup = new KnockoutAlleleLookup();
        derivationLookup = new DerivationLookupByVectorCreatorParent();
        vectorLookup = new VectorLookup();
        alleleLookupByCellLine = new AlleleLookupByCellLine();
        markerLookup = new MarkerLookupByMGIID();
        parentStrainLookupByParentKey = new ParentStrainLookupByParentKey();
        strainKeyLookup = new StrainKeyLookup();

        alleleFactory = KnockoutAlleleFactory.getFactory();
        Timestamp currentTime = new Timestamp(new Date().getTime());

    }

    /**
     * initialize the internal structures used by this class
     * @assumes nothing
     * @effects internal structures including database caching is initialized
     */
    protected void initialize()
    throws MGIException
    {
        super.logger.logInfo("Reading input files");

        sqlDBMgr.setLogger(super.logger);
        logger.logdDebug("TargetedAlleleLoader sqlDBMgr.server " + 
            sqlDBMgr.getServer());
        logger.logdDebug("TargetedAlleleLoader sqlDBMgr.database " + 
            sqlDBMgr.getDatabase());

        String basedir = super.dlaConfig.getReportsDir() + File.separator;
        
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
                super.logger.logdInfo(e.toString(), true);
                continue;
            }


            // Construct the allele from the input record
            KnockoutAllele constructed = null;
            
            try
            {
                constructed = processor.process(in);
            }
            catch (KeyNotFoundException e)
            {
                String m = "Allele creation error, check: ";
                m += in.getMutantCellLine() + "\n";
                m += e.getMessage();
                super.logger.logdInfo(m, false);
                continue;
            }
            catch (MGIException e)
            {
                String m = "General error, skipping record: ";
                m += in.getMutantCellLine() + "\n";
                m += e.getMessage();
                super.logger.logdInfo(m, false);
                continue;                
            }
            

            if (constructed == null)
            {
                String m = "Allele creation error, check: ";
                m += in.getMutantCellLine();
                super.logger.logdInfo(m, false);
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
                    noteMsg += "\nCurrent: " + existing.getSymbol();
                    noteMsg += " New: " + constructed.getSymbol();
                    super.logger.logcInfo(noteMsg, false);
                    qcStatistics.record("SUMMARY", "Number of alleles that changed marker, skipped");

                    // This mutant cell line has had a major change, don't bother QC
                    // checking the rest of the values
                    continue;
                }
                // If the marker hasn't changed, then check if the symbol
                // changed at all... if it did, that means the type changed
                // (from xx<tm1a(KOMP)Wtsi> to xx<tm1e(KOMP)Wtsi> or etc.)
                else if (!existing.getSymbol().equals(constructed.getSymbol()))
                {
                    String noteMsg = "\nTYPE CHANGED (not updating)\n";
                    noteMsg += "Mutant Cell line: " + in.getMutantCellLine();
                    noteMsg += "\nCurrent: " + existing.getSymbol();
                    noteMsg += " New: " + constructed.getSymbol();
                    super.logger.logcInfo(noteMsg, false);
                    qcStatistics.record("SUMMARY", "Number of alleles that changed type, skipped");

                    // This mutant cell line has had a major change, don't bother QC
                    // checking the rest of the values
                    continue;
                }
                // Only check the note content if the allele type and marker 
                // hasn't changed.  If the type changed, then by definition 
                // the note changed because different types use different 
                // note tempates
                else if (!existingNote.equals(constructedNote) && 
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
                        // If a note exists
                        // Delete the existing note
                        if (existing.getNoteKey() != null)
                        {
                            String query = "DELETE FROM MGI_Note WHERE ";
                            query += "_Note_key = ";
                            query += existing.getNoteKey();

                            sqlDBMgr.executeUpdate(query);
                        }

                        noteMsg += "Allele : ";
                        noteMsg += existing.getSymbol() + "\n";
                        noteMsg += "\nCurrent/New note:\n";
                        noteMsg += existing.getNote() + "\n";
                        noteMsg += constructed.getNote() + "\n";

                        // Set the new note in the existing allele,
                        // and save the allele
                        String newNote = constructed.getNote();
                        existing.updateNote(loadStream, newNote);
                        qcStatistics.record("SUMMARY", "Number of alleles that had molecular notes updated");
                    }
                    else
                    {
                        noteMsg += "Allele : ";
                        noteMsg += existing.getSymbol() + " (not updating)\n";
                        noteMsg += "Jobstream: " + jobStreamKey;
                        noteMsg += "\nModifiedBy: " + noteModifiedBy;
                        noteMsg += "\nCurrent/New note:\n";
                        noteMsg += existing.getNote() + "\n";
                        noteMsg += constructed.getNote() + "\n";
                        qcStatistics.record("WARNING", "Number of alleles that need to have molecular notes updated by curator");
                    }
                    super.logger.logcInfo(noteMsg, false);
                }

                if (existingGeneKey.compareTo(constructedGeneKey) != 0)
                {
                    String s = "\nMARKER ASSOCIATION CHANGED\n";
                    s += "Allele: " + existing.getSymbol() + "\n";
                    s += "Current marker key: " + existingGeneKey + "\n";
                    s += "New marker key: " + constructedGeneKey + "\n";
                    qcStatistics.record("WARNING", "Number of alleles that changed marker");
                    super.logger.logdInfo(s, false);
                }
                
                // Go on to the next mutant cell line                
                continue;
            }
            else
            {
                // Mutant ES Cell NOT found in database

                // Find the derivation key so we can hook the derivation up
                // to the Mutant Cell Line
                String cassette = in.getCassette();
                String dCompoundKey = vectorLookup.lookup(cassette) + "|";

                dCompoundKey += cfg.getCreatorKey() + "|";

                try
                {
                    String parent = in.getParentCellLine();
                    dCompoundKey += cfg.getParentalKey(parent);
                }
                catch (ConfigException e)
                {
                    String s = in.getParentCellLine();
                    s += " Does not exist in CFG file! Skipping record";
                    super.logger.logdInfo(s, true);
                    qcStatistics.record("ERROR", "Number of derivations not found");
                    continue;
                }

                Integer derivationKey = derivationLookup.lookup(dCompoundKey);
                
                if (derivationKey == null)
                {
                    String s = "Skipping record. Cannot find derivation for:";
                    s += "\n Vector: " + cassette;
                    s += "\n Creator Key: " + cfg.getCreatorKey();
                    s += "\n Parental: " + in.getParentCellLine();
                    s += "\n";
                    super.logger.logdInfo(s, true);
                    qcStatistics.record("ERROR", "Number of derivations not found");
                    continue;
                }


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

                // lookup existing alleles for this project
                String projectId = in.getProjectId();
                HashMap alleles = alleleLookpuByProjectId.lookup(projectId);

                if(alleles != null && alleles.size() > 0)
                {
                    // Get the allele identified by the constructed symbol
                    HashMap allele = (HashMap)alleles.get(constructed.getSymbol());

                    // If we found the allele, we can attach the MCL to it
                    if (allele != null)
                    {
                        // Found an allele with this same symbol
                        Integer alleleKey = (Integer)allele.get("key");
                        KnockoutAllele existing = koAlleleLookup.lookup(alleleKey);

                        if (existing == null)
                        {
                            super.logger.logdInfo("Found the allele key but not the allele:"+alleleKey, false);
                            continue;
                        }
                    
                        // Found!!  Do stuff requred for just THIS MCL

                        //Create the allele to cell line association
                        ALL_Allele_CellLineState aclState = new ALL_Allele_CellLineState();
                        aclState.setMutantCellLineKey(mclDAO.getKey().getKey());
                        aclState.setAlleleKey(alleleKey);
                        ALL_Allele_CellLineDAO aclDAO = new ALL_Allele_CellLineDAO(aclState);
                        loadStream.insert(aclDAO);
                        
                        // add the MCL to the allele and replace the allele 
                        // in the alleles map for this project
                        Vector mcls = new Vector();
                        mcls.add(in.getMutantCellLine());
                        allele.put("mutantCellLines", mcls);
                        alleles.put(constructed.getSymbol(), allele);

                        alleleLookpuByProjectId.addToCache(in.getProjectId(), alleles);
                        
                        continue;
                    }
                }

                // Default action is to creat the allele 

                // The MCL Didn't match any alleles, create an allele and
                // attach the MCL to it.
                
                constructed.insert(loadStream);
                
                //Create the allele to cell line association
                ALL_Allele_CellLineState aclState = new ALL_Allele_CellLineState();
                aclState.setMutantCellLineKey(mclDAO.getKey().getKey());
                aclState.setAlleleKey(constructed.getKey());
                ALL_Allele_CellLineDAO aclDAO = new ALL_Allele_CellLineDAO(aclState);
                loadStream.insert(aclDAO);
                

                // Create the allele with all the data from this row
                HashMap allele = new HashMap();
                allele.put("projectid", in.getProjectId());
                allele.put("key", constructed.getKey());
                allele.put("symbol", constructed.getSymbol());
                Vector mcls = new Vector();
                mcls.add(in.getMutantCellLine());
                allele.put("mutantCellLines", mcls);
                allele.put("parentCellLine", in.getParentCellLine());

                if (alleles == null)
                {
                    alleles = new HashMap();
                }

                // add the new allele to the map
                alleles.put(constructed.getSymbol(), allele);

                alleleLookpuByProjectId.addToCache(in.getProjectId(), alleles);
                
                // Finally, add the newly created allele to the cache
                koAlleleLookup.addToCache(constructed.getKey(), constructed);

                Marker mrk = markerLookup.lookup(in.getGeneId());
                String markerSymbol = mrk.getSymbol();

                HashSet alleleSet = null;
                alleleSet = alleleLookupByMarker.lookup(markerSymbol);
                if (alleleSet == null)
                {
                    alleleSet = new HashSet();
                }
                alleleSet.add(constructed.getSymbol());
                alleleLookupByMarker.addToCache(markerSymbol, alleleSet);

                qcStatistics.record("SUMMARY", "Number of alleles created");

            }

        }
        
        return;
    }

    /**
     * Must close the BCP file stream and commit the new maximum MGI \
     * number back to the database
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

        // Emptry line to the log files
        logger.logdInfo("\n", false);
        logger.logpInfo("\n", false);

        super.logger.logInfo("Process Finishing");
        return;
    }

}

