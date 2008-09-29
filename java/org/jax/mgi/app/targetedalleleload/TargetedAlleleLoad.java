package org.jax.mgi.app.targetedalleleload;

import java.util.*;
import java.io.File;
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

import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.dbs.mgd.LogicalDBConstants;
import org.jax.mgi.shr.dbutils.Table;

import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.dla.loader.DLALoaderException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.ioutils.IOUException;


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

public class TargetedAlleleLoad extends DLALoader
{

    private RecordDataIterator iter = null;
    private KnockoutAlleleLookup koAlleleLookup = null;
    private KnockoutAlleleProcessor processor = null;
    KnockoutAlleleFactory alleleFactory = null;

    /**
     * constructor
     * @throws DLALoaderException thrown if the super class cannot be
     * instantiated
     */
    public TargetedAlleleLoad()
    throws MGIException
    {
        koAlleleLookup = new KnockoutAlleleLookup();

        // Prime the Knockout Allele lookup
        koAlleleLookup.initCache();

        SQLDataManager sqlMgr =
            SQLDataManagerFactory.getShared(SchemaConstants.MGD);

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
        super.logger.logInfo("Opening report files");

        super.logger.logInfo("Reading input files");

        String basedir = super.dlaConfig.getReportsDir() + File.separator;
        
        // Get the configuration object and read in the input file
        TargetedAlleleLoadCfg cfg = new TargetedAlleleLoadCfg(); 

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
        HashMap alleleCount = getKnockoutAlleleCount();
        
        while(iter.hasNext())
        {
            KnockoutAllele allele = null;

            try
            {
                KnockoutAlleleInput in = (KnockoutAlleleInput)iter.next();
                allele = processor.process(in);
            }
            catch (MGIException e)
            {
                super.logger.logdInfo(e.toString(), true);
                
                continue;
            }

            // If the allele exists in the database already,
            // do the QC check then skip it
            KnockoutAllele existingAllele = 
                koAlleleLookup.lookup(allele.getAlleleId());

            if (existingAllele != null)
            {

                // Used for QC checking
                String existing = null;
                String fromFile = null;
                int changes = 0;
                
                // QC Check 1
                //  - Verify that the note is the same as the 
                //    one generated from the file (This garantees that the 
                //    deletion start, end, size, build, cassette haven't
                //    changed).  Strip the whitespace out to compare content only
                existing = existingAllele.getAlleleNote();
                existing = existing.replaceAll("\\W*","");
                existing = existing.replaceAll("\\n*","");
                fromFile = allele.getAlleleNote();
                fromFile = fromFile.replaceAll("\\W*","");
                fromFile = fromFile.replaceAll("\\n*","");
                if (!(existing.equals(fromFile)))
                {
                    String message = existingAllele.getAlleleSymbol();
                    message += "\nNONSTANDARD ALLELE MOLECULAR NOTE";
                    message += "\nold: "+existingAllele.getAlleleNote().replaceAll("\\n*","").trim();
                    message += "\nnew: "+allele.getAlleleNote().trim();
                    message += "\n";
                    super.logger.logcInfo(message, false);
                    changes += 1;   // count the change
                }

                // QC Check 2
                //  - Verify that the Mutant ES Cell line of the existing
                //    allele is the same as the one reported in the file
                existing = existingAllele.getMutant().getName();
                fromFile = allele.getMutant().getName();
                 if (!(existing.equals(fromFile)))
                {
                    String message = existingAllele.getAlleleSymbol();
                    message += "\nALLELE MUTANT ES CELL NAME CHANGED";
                    message += "\nold: "+existing;
                    message += "\nnew: "+fromFile;
                    message += "\n";
                    super.logger.logcInfo(message, false);
                    changes += 1;   // count the change
                }

                // QC Check 3
                //  - Verify that the type the existing
                //    allele has not changed from Targeted (Knockout)
                existing = existingAllele.getAlleleType().toString();
                fromFile = allele.getAlleleType().toString();
                if (!(existing.equals(fromFile)))
                {
                    String message = existingAllele.getAlleleSymbol();
                    message = "\nALLELE TYPE CHANGED";
                    message += "\nold: "+existing;
                    message += "\nnew: "+fromFile;
                    message += "\n";
                    super.logger.logcInfo(message, false);
                    changes += 1;   // count the change
                }

                // QC Check 4
                //  - Verify that the marker the existing
                //    allele has not changed from the file (use MGI ID)
                existing = existingAllele.getGene().getAccid().toString();
                fromFile = allele.getGene().getAccid().toString();
                if (!(existing.equals(fromFile)))
                {
                    String message = existingAllele.getAlleleSymbol();
                    message = "\nALLELE MARKER MGI ID CHANGED";
                    message += "\nold: "+existing;
                    message += "\nnew: "+fromFile;
                    message += "\n";
                    super.logger.logcInfo(message, false);
                    changes += 1;   // count the change
                }

                super.logger.logdInfo(
                    "Allele exists: "+allele.getAlleleId(), false);

                if (changes > 0)
                {
                    super.logger.logdInfo(
                        "  +-- Allele has changed since loading", false);
                }
                continue;
            }

            // Allele sequential naming starts at 1 for the clone numbering
            Integer sequence = new Integer(1);

            // The sequence is based on the number of alleles of this type for
            // this gene
            String geneSymbol = allele.getGene().getSymbol();
            
            // Check to see if we've seen alleles for this gene already
            if (alleleCount.containsKey(geneSymbol))
            {
                // The gene already has allele entries, update the 
                // allele per gene count 
                String count = alleleCount.get(geneSymbol).toString();
                int current = Integer.valueOf(count).intValue();
                sequence = new Integer(current + 1);
            }

            // We've added an allele for this gene - Update the hashmap
            alleleCount.put(geneSymbol, sequence);

            // Update the allele attributes to reflect the sequence
            String aName = allele.getAlleleName();
            String aSymbol = allele.getAlleleSymbol();
            aName = aName.replaceAll("~~SEQUENCE~~", sequence.toString());
            aSymbol = aSymbol.replaceAll("~~SEQUENCE~~", sequence.toString());

            allele.setAlleleName(aName);
            allele.setAlleleSymbol(aSymbol);

            // This allele doesn't exist in the database.  Add it to the
            // database 
            super.logger.logdInfo("Adding: "+allele.getAlleleId(), false);
            super.logger.logdInfo("Adding: "+allele.getAlleleSymbol(), false);
            allele.insert(loadStream);
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

    /**
     * Generate a map containing gene symbol to number of alleles in the db
     * @assumes this.koAlleleLookup exists and is primed
     * @assumes this.koAlleleLookup contains alleles for this provider only
     * @effects nothing
     * @throws nothing
     */
    private HashMap getKnockoutAlleleCount()
    throws MGIException
    {
        HashMap alleleCount = new HashMap();
        
        Map koAlleleMap = koAlleleLookup.getCache();

        for (Iterator i = koAlleleMap.keySet().iterator(); i.hasNext();)
        {
            String sym = (String)i.next();
            KnockoutAllele allele = 
                (KnockoutAllele)koAlleleLookup.lookup(sym);

            String geneSymbol = allele.getGene().getSymbol();
            if (alleleCount.containsKey(geneSymbol))
            {
                String count = alleleCount.get(geneSymbol).toString();
                int newCount = Integer.valueOf(count).intValue() + 1;
                alleleCount.put(geneSymbol, new Integer(newCount));
            }
            else
            {
                alleleCount.put(geneSymbol, new Integer(1));
            }
        }
        return alleleCount;
    }
}
