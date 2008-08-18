package org.jax.mgi.app.targetedalleleload;

import java.util.*;
import java.io.File;

import org.jax.mgi.shr.ioutils.OutputDataFile;
import org.jax.mgi.shr.datetime.DateTime;
import org.jax.mgi.shr.dbutils.BatchProcessor;
import org.jax.mgi.shr.dla.loader.DLALoader;
import org.jax.mgi.shr.dla.loader.DLALoaderException;
import org.jax.mgi.shr.exception.MGIException;

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

    /**
     * constructor
     * @throws DLALoaderException thrown if the super class cannot be
     * instantiated
     */
    public TargetedAlleleLoad() throws DLALoaderException
    {
    }
    /**
     * initialize the internal structures used by this class
     * @assumes nothing
     * @effects internal structures including database caching is initialized
     * @throws MGIException thrown if there is an error during initialization
     */
    protected void initialize() throws MGIException
    {
        super.logger.logInfo("Opening report files");
        String basedir = super.dlaConfig.getReportsDir() + File.separator;

        super.logger.logInfo("Initializing cache");

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
    protected void preprocess() throws MGIException
    {
	return;
    }

    /**
     * read the iproclass input file and run the mapping algorithm and creates
     * output data files
     * @assumes nothing
     * @effects the data will be created for loading vocabulary and annotations
     * into the database and output data files are created
     * @throws MGIException thrown if there is an error accessing the input
     * file or writing output data
     */
    protected void run() throws MGIException
    {
        return;
    }

    /**
     * No postprocessing needed for this load
     * @assumes nothing
     * @effects nothing
     * @throws nothing
     */
    protected void postprocess() throws MGIException
    {
        super.logger.logInfo("Process Finishing");
    }

}
