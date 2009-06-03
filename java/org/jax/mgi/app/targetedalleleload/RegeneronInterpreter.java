package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;

import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;

import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.exception.MGIException;

/**
 * @is An object that knows how to create KOMP Clone objects from 
 * the Regeneron allele file 
 * @has
 *   <UL>
 *   <LI> KOMP Clone object.
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Parses a Regeneron Allele file record into a KOMP Clone object
 *   <LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class RegeneronInterpreter implements RecordDataInterpreter
{

    // The minimum length of a valid input record (including NL character).
    //
    private static final int MIN_REC_LENGTH = 50;
    private DLALogger logger = null;

    /**
     * Constructs a Regeneron specific interpreter object
     * @assumes Nothing
     * @effects Nothing
     */
    public RegeneronInterpreter ()
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

        RegeneronAlleleInput inputData = new RegeneronAlleleInput();

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

        // Set the attributes of the inputData object using the fields parsed
        // from the input record.
        // 0 - getESCellName
        // 1 - Parental ES Cell
        // 2 - Mouse Strain 
        // 3 - REGN Allele ID
        // 4 - Gene Symbol
        // 5 - MGI ID 
        // 6 - Del Start
        // 7 - Del End
        // 8 - Del Size
        // 9 - Genome Build
        // 10 - Cassette
        //

        inputData.setESCellName(fields[0].trim());
        inputData.setParentalESCellName(fields[1].trim());
        inputData.setStrainName(fields[2].trim());
        inputData.setProjectId(fields[3].trim());
        inputData.setGeneSymbol(fields[4].trim());
        inputData.setGeneMgiId(fields[5].trim());
        inputData.setDelStart(new Integer(fields[6].trim()));
        inputData.setDelEnd(new Integer(fields[7].trim()));
        inputData.setDelSize(new Integer(fields[8].trim()));
        inputData.setBuild(fields[9].trim());
        inputData.setCassette(fields[10].trim());

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
    public boolean isValid (String rec) {
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
        else if (rec.substring(0,7).equals("CloneID"))
        {
            // Ignore the header line which has the Column headers
            return false;
        }
        else if (parts[4].indexOf(",") >= 0)
        {
            // The gene symbol has a comma in it!  Probably a case of
            // one allele knocked out multiple markers.
            String msg = "SKIPPING THIS RECORD: ";
            msg += "Probable 'one allele knocked out multiple markers' record\n";
            msg += rec;
            logger.logcInfo(msg,false);
            return false;
        }
        else
        {
            String[] fields = rec.split("\t");
            if (fields[6].equals("0") || fields[7].equals("0"))
            {
                String msg = "SKIPPING THIS RECORD: ";
                msg += "Deletion start/end is not reported\n";
                msg += rec;
                logger.logcInfo(msg,false);
                return false;
            }
            return true;
        }
    }
}


/**************************************************************************
*
* Warranty Disclaimer and Copyright Notice
*
*  THE JACKSON LABORATORY MAKES NO REPRESENTATION ABOUT THE SUITABILITY OR
*  ACCURACY OF THIS SOFTWARE OR DATA FOR ANY PURPOSE, AND MAKES NO WARRANTIES,
*  EITHER EXPRESS OR IMPLIED, INCLUDING MERCHANTABILITY AND FITNESS FOR A
*  PARTICULAR PURPOSE OR THAT THE USE OF THIS SOFTWARE OR DATA WILL NOT
*  INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS, OR OTHER RIGHTS.
*  THE SOFTWARE AND DATA ARE PROVIDED "AS IS".
*
*  This software and data are provided to enhance knowledge and encourage
*  progress in the scientific community and are to be used only for research
*  and educational purposes.  Any reproduction or use for commercial purpose
*  is prohibited without the prior express written permission of The Jackson
*  Laboratory.
*
* Copyright \251 1996, 1999, 2002, 2004 by The Jackson Laboratory
*
* All Rights Reserved
*
**************************************************************************/