package org.jax.mgi.app.targetedalleleload;

import java.util.*;
import java.io.File;
import java.lang.Integer;
import java.lang.Integer;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.config.InputDataCfg;

import org.jax.mgi.shr.ioutils.InputDataFile;
import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.ioutils.RecordDataIterator;
import org.jax.mgi.shr.datetime.DateTime;
import org.jax.mgi.shr.dbutils.BatchProcessor;
import org.jax.mgi.shr.dla.loader.DLALoader;

import org.jax.mgi.dbs.mgd.AccessionLib;
import org.jax.mgi.dbs.SchemaConstants;

import org.jax.mgi.shr.dbutils.Table;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.dla.loader.DLALoaderException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.ioutils.IOUException;


import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;

/**
 * @is a DLALoader for loading KOMP produced Alleles into the database
 * and associating them with appropriate marker annotations, strains,
 * moleculare notes, and J-number references.  This process will also
 * create the official nomenclature and generate an official MGI ID 
 * for the allele.
 * @has nothing
 * @does reads the Allele input file (downloaded nightly from the KOMP
 * production center website) and determines, for each line in the file,
 * if the Allele already exists in the MGI database.  If it does not exist,
 * The targetedalleleload create the allele.
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class TargetedAlleleLoad
extends DLALoader
{

    private RecordDataIterator iter = null;
    private AlleleLookup alleleLookup = null;
    private KnockoutAlleleLookup koAlleleLookup = null;
    private CellLineLookup cellLineLookup = null;
    private KnockoutAlleleProcessor processor = null;
    private KnockoutAlleleFactory alleleFactory = null;
    private TargetedAlleleLoadCfg cfg = null;
    private SQLDataManager sqlDBMgr = null;

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

        String esCellLogicalDb = cfg.getEsCellLogicalDb();
        String projectLogicalDb = cfg.getProjectLogicalDb();

        // Instance the caches with appropriate data that has been identified 
        // by the configuration variables specified in the configuration file
        alleleLookup = new AlleleLookup(projectLogicalDb);
        cellLineLookup = new CellLineLookup(esCellLogicalDb, projectLogicalDb);
        koAlleleLookup = new KnockoutAlleleLookup();

        // Prime the lookups
        alleleLookup.initCache();
        cellLineLookup.initCache();
        koAlleleLookup.initCache();

        alleleFactory = KnockoutAlleleFactory.getFactory();
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
        logger.logdDebug("TargetedAlleleLoader sqlDBMgr.server " + sqlDBMgr.getServer());
        logger.logdDebug("TargetedAlleleLoader sqlDBMgr.database " + sqlDBMgr.getDatabase());

        String basedir = super.dlaConfig.getReportsDir() + File.separator;
        
        InputDataFile inputFile = new InputDataFile(cfg);

        // Get an appropriate Interpreter for the file
        RecordDataInterpreter interp = alleleFactory.getInterpreter();

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
        
        HashSet qcd = new HashSet();
        
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
            
            // Get the set of alleles already associated to this
            // project
            HashSet alleles = alleleLookup.lookup(in.getProjectId());

            if (alleles != null && alleles.size() > 1)
            {

                // Is this project associated with more than ONE allele
                String msg = "SKIPPING THIS RECORD: ";
                msg += "This project has multiple allele records.'\n";
                msg += "Project: " + in.getProjectId();
                msg += " points to allele keys: " + alleles + "\n";
                super.logger.logcInfo(msg, false);
                continue;

            }
            else if (alleles == null || alleles.size() < 1)
            {

                // The allele doesn't exist! Create the allele
                // with all appropriate supporting objects

                try
                {
                    KnockoutAllele allele = processor.process(in);

                    allele.insert(loadStream);

                    String e ="Added allele: "+allele.toString();
                    super.logger.logdInfo(e.toString(), false);

                    // Add the allele to the fullcachedlookups 
                    // (allele lookup and marker allele lookup)
                    String escellKey = Integer.toString(allele.getMutant().getKey());

                    HashMap alleleInfo = new HashMap();
                    alleleInfo.put("allele", Integer.toString(allele.getAlleleKey()));
                    alleleInfo.put("escell", escellKey);

                    alleles = new HashSet();
                    alleles.add(alleleInfo);

                    alleleLookup.addToCache(in.getProjectId(), alleles);
                    
                }
                catch (MGIException e)
                {
                    super.logger.logdInfo(e.toString(), false);
                    continue;
                }

            }
            else
            {

                // We're here so we know:
                //     alleles != null && alleles.size() == 1
                // The allele exists so all we need to do is add
                // entries into the ACC_Accession table for the cell line

                String projectId = in.getProjectId();
                String currentCellLine = in.getESCellName();

                // Get the existing allele from the database
                KnockoutAllele existing = koAlleleLookup.lookup(projectId);

                if (existing == null)
                {
                    // There is nothing to QC because the allele doesn't 
                    // exist in the database yet (only the input file).
                    // We're done QCing it, add it to the "done" pile
                    qcd.add(projectId);
                }
                
                if (!qcd.contains(projectId))
                {
                    // QC check the allele
                    //  Two QC checks
                    //  1) Compare calculated note to stored note
                    //  2) Compare calculated project to marker association 
                    //      to stored association

                    // We're going to QC this now.  Add it to the "done" pile
                    qcd.add(projectId);

                    // let the QC BEGIN!

                    // Construct this allele from the input file
                    KnockoutAllele constructed = processor.process(in);

                    // Compare the notes to see if anything changed.
                    String existingNote = existing.getAlleleNote().replaceAll("\\n", "");
                    String constructedNote = constructed.getAlleleNote().replaceAll("\\n", "");

                    String existingGeneSymbol = existing.getGeneSymbol();
                    String constructedGeneSymbol = constructed.getGeneSymbol();


                    if (existingNote.compareTo(constructedNote) != 0)
                    {
                        String noteMsg = "\nMOLECULAR NOTE CHANGED\n";
                        
                        // If the note was entered by this load, go ahead and
                        // update the note to reflect the current note,
                        // otherwise, a curator updated the note, so we 
                        // shouldn't update it.
                        Integer jobStreamKey = cfg.getJobStreamKey();
                        Integer noteCreatedBy = existing.getAlleleNoteCreatedBy();
                        Integer noteModifiedBy = existing.getAlleleNoteModifiedBy();
                        if (jobStreamKey.compareTo(noteCreatedBy) == 0 && 
                            jobStreamKey.compareTo(noteModifiedBy) == 0)
                        {
                            // Delete the existing note
                            String query = "DELETE FROM MGI_Note WHERE ";
                            query += "_Note_key = ";
                            query += existing.getAlleleNoteKey();
                            sqlDBMgr.executeUpdate(query);

                            // Set the new note in the existing allele,
                            // and save the allele
                            String newNote = constructed.getAlleleNote();
                            existing.updateNote(loadStream, newNote);

                            noteMsg += "Allele : ";
                            noteMsg += existing.getAlleleSymbol() + "\n";                            
                            noteMsg += "Updated molecular note to:\n";
                            noteMsg += constructedNote + "\n";
                        }
                        else
                        {
                            noteMsg += "Allele : ";
                            noteMsg += existing.getAlleleSymbol() + "\n";
                            noteMsg += "NOT UPDATING\n";
                            noteMsg += "Jobstream: " + jobStreamKey;
                            noteMsg += "\nCreatedBy: " + noteCreatedBy;
                            noteMsg += "\nModifiedBy: " + noteModifiedBy;                            
                            noteMsg += "\nExisting note/New note:\n";
                            noteMsg += existingNote + "\n";
                            noteMsg += constructedNote + "\n";
                        }
                        super.logger.logcInfo(noteMsg, false);
                        super.logger.logdInfo(noteMsg, false);
                    }

                    if (existingGeneSymbol.compareTo(constructedGeneSymbol) != 0)
                    {
                        String symMsg = "\nMARKER ASSOCIATION CHANGED\n";
                        symMsg += "Allele : " + existing.getAlleleSymbol() + "\n";
                        symMsg += "Existing allele marker : " + existingGeneSymbol + "\n";
                        symMsg += "New marker             : " + constructedGeneSymbol + "\n";
                        super.logger.logcInfo(symMsg, false);
                        super.logger.logdInfo(symMsg, false);
                    }
                }


                // Get the set of cell lines already associated to this
                // project to see if this cell line already exists
                HashSet cellLines = cellLineLookup.lookup(projectId);

                if (cellLines != null && cellLines.contains(currentCellLine))
                {
                    // Cell line already exists, we've already QCd the allele, 
                    // so just skip to the next record
                    continue;
                } 

                // What allele does this belong to?
                HashMap allele = (HashMap)alleles.toArray()[0];
                int escKey = Integer.parseInt((String)allele.get("escell"));

                // Constants for the Accession entry
                int esCellDB = Integer.parseInt(cfg.getEsCellLogicalDb());
                int esCellType = Constants.ESCELL_MGITYPE_KEY;
                int typeKey = esCellType;

                // Create the ES Cell Accession object
                AccessionId accId = new AccessionId(
                    currentCellLine,  // Cell line name
                    esCellDB,         // Logical DB
                    escKey,           // ES cell object key
                    typeKey,          // MGI type
                    Boolean.FALSE,    // Private?
                    Boolean.TRUE      // Preferred?
                    );

                accId.insert(loadStream);

                String e ="Added accession id: "+accId.toString();
                super.logger.logdInfo(e.toString(), false);

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
        //
        AccessionLib.commitAccessionMax();

        super.logger.logInfo("Process Finishing");
        return;
    }

}
