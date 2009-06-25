package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;

import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;

import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.config.ConfigException;

/**
 * @is An object that knows how to Interpret
 * the CSD allele file 
 * @has
 *   <UL>
 *   <LI> logger
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Interprets a CSD Allele record into it's various parts
 *   </UL>
 * @returns
 *   <UL>
 *   <LI> CSDAlleleInput object
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class CSDInterpreter implements RecordDataInterpreter
{

    // The minimum length of a valid input record (including NL character).
    //
    private static final int MIN_REC_LENGTH = 50;

    private DLALogger logger = null;
    
    /**
     * Constructs a CSD specific interpreter object
     * @assumes Nothing
     * @effects Nothing
     */
    public CSDInterpreter ()
    {
        try
        {
            logger = DLALogger.getInstance();
        }
        catch (DLALoggingException e)
        {
            logger.logdInfo(e.getMessage(), true);
        }
    }

    /**
     * Set all the attributes of the inputData object by parsing the given
     * input record.
     * @assumes Nothing
     * @effects Loads the clone object.
     * @param rec A record from the Regeneron allele input file
     * @return An RegeneronAlleleInput object
     * @throws RecordFormatException
     */
    public Object interpret (String rec)
    throws MGIException
    {

        CSDAlleleInput inputData = new CSDAlleleInput();

        // Throw an exception if the input record does not meet the minimum
        // length required to extract the fields.
        //
        if (rec.length() < MIN_REC_LENGTH)
        {
            RecordFormatException e = new RecordFormatException();
            e.bindRecord(rec);
            throw e;
        }

        // Get fields from the input record
        // The file is TAB delimited
        String[] fields = rec.split("\t");
        
        // Strip off any trailing whitespace from each field
        for (int i=0; i< fields.length; i++)
        {
            fields[i] = fields[i].trim();
        }

        // Set the attributes of the inputData object using the fields parsed
        // from the input record.
        // 0 - Gene ID
        // 1 - Genome Build
        // 2 - Cassette
        // 3 - Project (CSD, EUCOMM, NorCOMM)
        // 3 - Project ID
        // 4 - Mutant ES cell line ID
        // 5 - Parent ES cell line name
        // 6 - Mutation type
        // 7 - Insertion point 1
        // 8 - Insertion point 2

        inputData.setGeneId(fields[0]);
        inputData.setBuild(fields[1]);
        inputData.setCassette(fields[2]);
        //project is 3
        inputData.setProjectId(fields[4]);
        inputData.setESCellName(fields[5]);
        inputData.setParentESCellName(fields[6]);
        inputData.setMutationType(fields[7]);
        inputData.setLocus1(fields[8]);
        inputData.setLocus2(fields[9]);

        // Return the populated inputData object.
        //
        return inputData;
    }

    /**
     * Determines if the given input record is a valid record. A comment
     * line is considered to be invalid.  The header line is invalid.
     * @assumes Nothing
     * @effects Nothing
     * @param rec A record from the Regeneron input file
     * @return Indicator of whether the input record is valid (true)
     * or not (false)
     */
    public boolean isValid (String rec)
    {
        // If the first character of the input record is a "#", it is a
        // comment and should be ignored.
        // The first line is a header starting with the string "CloneID"
        // and should be ignored.
        String[] parts = rec.split("\t");
        
        if (rec.substring(0,1).equals("#"))
        {
            // Ignore comment lines which start with a "#" character
            return false;
        }
        else if (!parts[3].matches("CSD"))
        {
            // unknown mutation type
            String msg = "SKIPPING THIS RECORD: ";
            msg += "Not this project\n";
            msg += rec;
            logger.logcInfo(msg,false);
            return false;
        }
        else if (!parts[7].matches("Conditional|Targeted non-conditional|Deletion"))
        {
            // unknown mutation type
            String msg = "SKIPPING THIS RECORD: ";
            msg += "Unknown mutation type\n";
            msg += rec;
            logger.logcInfo(msg,false);
            return false;
        }
        else
        {
            return true;
        }
    }
}

