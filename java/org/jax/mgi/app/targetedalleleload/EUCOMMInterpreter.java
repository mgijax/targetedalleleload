package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;

import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;

import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.config.ConfigException;

// Support for CSV splitting
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @is An object that knows how to Interpret
 * the EUCOMM allele file 
 * @has
 *   <UL>
 *   <LI> logger
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Interprets a EUCOMM Allele record into it's various parts
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class EUCOMMInterpreter extends KnockoutAlleleInterpreter
{

    // The minimum length of a valid input record (including NL character).
    //
    private static final int MIN_REC_LENGTH = 50;
    private static final String DELIM = ",";

    public static final String CSV_PATTERN = "\"([^\"]+?)\",?|([^,]+),?|,";
    private static Pattern csvRE;

    private DLALogger logger = null;



    /**
     * Constructs a EUCOMM specific interpreter object
     * @assumes Nothing
     * @effects Nothing
     */
    public EUCOMMInterpreter ()
    {
        csvRE = Pattern.compile(CSV_PATTERN);
        try
        {
            logger = DLALogger.getInstance();
        }
        catch (DLALoggingException e)
        {
            logger.logdInfo(e.getMessage(), true);
        }
    }

    /** Parse one line.
     * @return List of Strings, minus their double quotes
     */
    public List parse(String line) {
        List list = new ArrayList();
        Matcher m = csvRE.matcher(line);
        // For each field
        while (m.find()) {
            String match = m.group();
            if (match == null)
                break;
            if (match.endsWith(",")) {  // trim trailing ,
                match = match.substring(0, match.length() - 1);
            }
            if (match.startsWith("\"")) { // assume also ends with
                match = match.substring(1, match.length() - 1);
            }
            if (match.length() == 0)
                match = null;
            list.add(match);
        }
        return list;
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

        EUCOMMAlleleInput inputData = new EUCOMMAlleleInput();
        qcStatistics.record("SUMMARY", "Number of input records");
        

        // Throw an exception if the input record does not meet the minimum
        // length required to extract the fields.
        //
        if (rec.length() < MIN_REC_LENGTH)
        {
            RecordFormatException e = new RecordFormatException();
            e.bindRecord(rec);
            qcStatistics.record("WARNING", "Poorly formated input record(s)");
            throw e;
        }

        // Get fields from the input record
        // The file is DELIM delimited
        //String[] fields = rec.split(DELIM);
        List list = parse(rec);
        String[] fields = (String[]) list.toArray(new String[0]);
        
        // Strip off any trailing whitespace from each field
        // Also remove the double quotes.
        for (int i=0; i< fields.length; i++)
        {
            fields[i] = fields[i].trim().replaceAll("\"", "");
        }

        // Set the attributes of the inputData object using the fields parsed
        // from the input record.
        // 0 - Gene ID
        // 1 - Genome Build
        // 2 - Cassette
        // 3 - Project (CSD, EUCOMM, NorCOMM)
        // 4 - Project ID
        // 5 - Mutant ES cell line ID
        // 6 - Parent ES cell line name
        // 7 - Sanger allele name
        // 8 - Mutation type
        // 9 - Insertion point 1
        // 10 - Insertion point 2

        inputData.setGeneId(fields[0]);
        inputData.setBuild(fields[1]);
        inputData.setCassette(fields[2]);

        // field 3 defines which pipeline created the MCL
        // This being the EUCOMM interpreter, we already know because we
        // filter out all the other lines in the isValid check.

        inputData.setProjectId(fields[4]);
        inputData.setESCellName(fields[5]);
        inputData.setParentESCellName(fields[6]);
        inputData.setMutationType(fields[8]);
        String[] locus1parts = fields[9].split("-");
        inputData.setLocus1(locus1parts[0]);
        
        if (fields[10].compareTo("-") == 0)
        {
            // Deletion allele doesn't have second coord pair, use the second
            // part of the first coord pair
            inputData.setLocus2(locus1parts[1]);
        }
        else
        {
            String[] locus2parts = fields[10].split("-");
            inputData.setLocus2(locus2parts[0]);            
        }

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
        //String[] parts = rec.split(DELIM);
        List list = parse(rec);
        String[] parts = (String[]) list.toArray(new String[0]);
        
        if (rec.substring(0,1).equals("#"))
        {
            // Ignore comment lines which start with a "#" character
            return false;
        }
        else if (!parts[3].replaceAll("\"", "").matches("EUCOMM"))
        {
            // Wrong project
            qcStatistics.record("SUMMARY", "Non EUCOMM input record(s) skipped");
            return false;
        }
        else if (parts[5].replaceAll("\"", "").matches("^DEPD.*"))
        {
            // Wrong project
            qcStatistics.record("SUMMARY", "Non EUCOMM input record(s) skipped");
            return false;
        }
        else if (parts[6].indexOf(",") > 0)
        {
            // strangely formatted ES Cell (parental)
            // String msg = "SKIPPING THIS RECORD: ";
            // msg += "Parental cell line is: "+parts[6];
            // msg += "\n";
            // msg += rec;
            // logger.logdInfo(msg,false);
            qcStatistics.record("WARNING", "Input record(s) with unknown parental cell line skipped");
            return false;
        }
        else if (!parts[8].replaceAll("\"", "").matches("Conditional|Targeted non-conditional|Deletion"))
        {
            // unknown mutation type
            // String msg = "SKIPPING THIS RECORD: ";
            // msg += "Mutation type is: "+parts[8];
            // msg += "which is an unknown mutation type\n";
            // msg += rec;
            // logger.logdInfo(msg,false);
            qcStatistics.record("WARNING", "Input record(s) with unknown mutation type skipped");
            return false;
        }
        else
        {
            qcStatistics.record("SUMMARY", "Successfully interpreted EUCOMM input record(s)");
            return true;
        }
    }
}
