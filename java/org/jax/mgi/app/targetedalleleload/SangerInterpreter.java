package org.jax.mgi.app.targetedalleleload;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.RecordFormatException;

/**
 * @is An object that knows how to Interpret the Sanger allele file 
 * @has
 * @does Interprets a Sanger Allele input record into it's various parts
 * @company The Jackson Laboratory
 * @author jmason
 */

public class SangerInterpreter extends KnockoutAlleleInterpreter
{

    // The minimum length of a valid input record (including NL character).
    private static final int MIN_REC_LENGTH = 50;
    public static final String CSV_PATTERN = "\"([^\"]+?)\",?|([^,]+),?|,";
    private static Pattern csvRE;
    private TargetedAlleleLoadCfg cfg = null;
    private List allowedCelllines = null;
    private List knownCelllines = null;
    private String pipeline = null;
    protected DLALogger logger = null;

    /**
     * Constructs a Sanger specific interpreter object
     * @throws MGIException 
     * @assumes Nothing
     * @effects Nothing
     */
    public SangerInterpreter ()
    throws MGIException
    {
        csvRE = Pattern.compile(CSV_PATTERN);
        cfg = new TargetedAlleleLoadCfg();
        allowedCelllines = cfg.getAllowedCelllines();
        knownCelllines = cfg.getKnownCelllines();
        pipeline = cfg.getPipeline();
        this.logger = DLALogger.getInstance();
    }

    /** Parse one line. Split the line apart on comma and remove the
     *  double quotes from each piece. Also remove trailing whitespace
     * @return List of Strings
     */
    public List parse(String line) {
        List list = new ArrayList();
        Matcher m = csvRE.matcher(line);

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
            else
            	match = match.trim();
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

        SangerAlleleInput inputData = new SangerAlleleInput();
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
        // The file is a CSV file
        List list = parse(rec);
        String[] fields = (String[]) list.toArray(new String[0]);

        // Set the attributes of the inputData object using the fields parsed
        // from the input record.
        // 0 - Gene ID
        // 1 - Genome Build
        // 2 - Cassette
        // 3 - Project (KOMP, EUCOMM, NorCOMM)
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
        // we filter out all the other pipelines using field 3
        // in the isValid check.

        inputData.setProjectId(fields[4]);
        inputData.setESCellName(fields[5]);
        inputData.setParentESCellName(fields[6]);
        inputData.setMutationType(fields[8]);
        String[] locus1parts = fields[9].split("-");
        inputData.setLocus1(locus1parts[0]);
        
        if (fields[10].equals("-"))
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
        return inputData;
    }

    /**
     * Determines if the given input record is a valid record. A comment
     * line is considered to be invalid.  The header line is invalid.
     * @assumes Nothing
     * @effects Nothing
     * @param rec A record from the Sanger input file
     * @return Indicator of whether the input record is valid (true)
     * or not (false)
     */
    public boolean isValid (String rec)
    {
        List list = parse(rec);
        String[] parts = (String[]) list.toArray(new String[0]);
        
        // The first letter of the cell line ID indicates what lab created it
        String firstLetter = parts[5].substring(0, 1);
        
        if(!knownCelllines.contains(firstLetter))
        {
        	// A new provider!
        	qcStatistics.record("ERROR", "Records with a new provider ("+firstLetter+")");
        	logger.logInfo("Cell line record with a new provider ("+parts[5]+")");
        	return false;
        }
        if(!allowedCelllines.contains(firstLetter))
        {
        	// This cell line is not appropriate for this provider
        	qcStatistics.record("SUMMARY", "Input record(s) not approriate for this provider, skipped");
        	return false;
        }
        if (parts[0].equals("MGI ACCESSION ID"))
        {
            // Ignore header line
            return false;
        }
        if (parts[0].substring(0,1).equals("#"))
        {
            // Ignore any comment lines which start with a "#" character
            return false;
        }
        if (!parts[3].replaceAll("\"", "").matches(pipeline))
        {
            // Wrong project
            qcStatistics.record("SUMMARY", "Input record(s) not approriate for this provider, skipped");
            return false;
        }
        if (parts[6].contains(","))
        {
            // strangely formatted ES Cell (parental)
            qcStatistics.record("WARNING", "Input record(s) with unknown parental cell line skipped");
            return false;
        }
        if (!parts[8].replaceAll("\"", "").matches("Conditional|Targeted non-conditional|Deletion"))
        {
            // unknown mutation type
            qcStatistics.record("WARNING", "Input record(s) with unknown mutation type skipped");
            return false;
        }

        // Default action is to indicate this record as valid
        qcStatistics.record("SUMMARY", "Successfully interpreted input record(s)");
        return true;
    }
}

