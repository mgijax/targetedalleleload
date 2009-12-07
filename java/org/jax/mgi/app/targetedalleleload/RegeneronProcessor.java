package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import java.util.Vector;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.dbs.mgd.lookup.VocabKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;

import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.dbs.mgd.lookup.TranslationException;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.exception.MGIException;


/**
 * @is An object that knows how to create a KnockoutAllele object from 
 * a RegeneronAlleleInput
 * @has
 *   <UL>
 *   <LI> KnockoutAllele object, which is then returned
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Parses a RegeneronAlleleInput object into a KnockoutAllele object
 *   <LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

public class RegeneronProcessor extends KnockoutAlleleProcessor
{
    /////////////////
    //  Variables  //
    /////////////////

    // KnockoutAllele object
    //
    private TargetedAlleleLoadCfg cfg = null;
    private MarkerLookupByMGIID markerLookup = null;
    private StrainKeyLookup strainKeyLookup = null;
    private VocabKeyLookup vocabLookup = null;
    private ProjectLookupByMarker projectLookupByMarker = null;
    

    /**
     * Constructs a KnockoutAllele processor object.
     * @assumes Nothing
     * @effects Nothing
     * @throws ConfigException
     * @throws DLALoggingException if a logger instance cannot be obtained
     * @throws DBException if a database access error occurs
     * @throws CacheException 
     * @throws TranslationException 
     */
    public RegeneronProcessor ()
    throws ConfigException,DLALoggingException,
    DBException,CacheException,TranslationException
    {
        cfg = new TargetedAlleleLoadCfg();
        projectLookupByMarker = new ProjectLookupByMarker(cfg.getProjectLogicalDb());
        markerLookup = new MarkerLookupByMGIID();
		vocabLookup = new VocabKeyLookup(Constants.ALLELE_VOCABULARY);
		strainKeyLookup = new StrainKeyLookup();
    }


    /**
     * Set all the attributes of the clone object by parsing the given
     * input record and providing Regeneron Specific constant values.
     * @assumes Nothing
     * @effects Loads the clone object.
     * @param inputData A record from the Regeneron allele input file
     * @return An KnockoutAllele object
     * @throws RecordFormatException
     * @throws ConfigException
     * @throws DBException
     * @throws CacheException
     * @throws TranslationException 
     */
    public KnockoutAllele process (KnockoutAlleleInput inputData)
    throws RecordFormatException,ConfigException,KeyNotFoundException,
    DBException,CacheException,TranslationException,MGIException
    {
        // Cast the input to a Regeneron specific allele input type
        RegeneronAlleleInput in = (RegeneronAlleleInput)inputData;

        KnockoutAllele koAllele = new KnockoutAllele();

        // Get the external dependencies referenced in this row
        Marker marker = markerLookup.lookup(in.getGeneId());
        Integer strainKey = strainKeyLookup.lookup(in.getStrainName());

        koAllele.setMarkerKey(marker.getKey());
        koAllele.setProjectId(in.getProjectId());
        koAllele.setProjectLogicalDb(cfg.getProjectLogicalDb());
        koAllele.setStrainKey(strainKey);

        // Regeneron Specific Mutation types
        Vector mutationTypeKeys = new Vector();
        String[] types = cfg.getMutationTypes().split(",");
        for (int i=0;i<types.length;i++)
        {
            Integer key = vocabLookup.lookup(types[i].trim());
            mutationTypeKeys.add(key);
        }
        koAllele.setMutationTypes(mutationTypeKeys);

        // get the NEXT allele symbol sequence number which is one higher than
        // the sum of CURRENT KOMP ALELLES attached to this marker BY THIS
        // PROVIDER
        int seq = 1; // Default to the first project
        HashSet allProj = projectLookupByMarker.lookup(marker.getSymbol());
        if (allProj != null && !allProj.contains(in.getProjectId())) {
            // There is already a project (or projects) associated to this
            // marker, and it is not tHIS project. Increment the count 
            // for this new allele symbol
            seq = (allProj.size()) + 1;
        }

        // Set the koAllele's constructed values
        String alleleName = cfg.getNameTemplate();
        alleleName = alleleName.replaceAll("~~SYMBOL~~", marker.getSymbol()); 
        alleleName = alleleName.replaceAll("~~SEQUENCE~~", Integer.toString(seq)); 
        koAllele.setName(alleleName);

        String alleleSymbol = cfg.getSymbolTemplate();
        alleleSymbol = alleleSymbol.replaceAll("~~SYMBOL~~", marker.getSymbol()); 
        alleleSymbol = alleleSymbol.replaceAll("~~SEQUENCE~~", Integer.toString(seq)); 
        koAllele.setSymbol(alleleSymbol);

        String jNumber = cfg.getJNumber();
        koAllele.setJNumber(jNumber);

        String note = cfg.getNoteTemplate();
        
        // If coordinates are missing, fill out the incomplete note template
        if (in.getDelSize().toString().equals("0") ||
            in.getDelStart().toString().equals("0") ||
            in.getDelEnd().toString().equals("0"))
        {
            note = cfg.getNoteTemplateMissingCoords();
        }

        note = note.replaceAll("~~CASSETTE~~", in.getCassette().toString()); 
        note = note.replaceAll("~~SIZE~~", in.getDelSize().toString());
        note = note.replaceAll("~~START~~", in.getDelStart().toString());
        note = note.replaceAll("~~END~~", in.getDelEnd().toString());
        note = note.replaceAll("~~CHROMOSOME~~", marker.getChromosome()); 
        note = note.replaceAll("~~BUILD~~", in.getBuild()); 
        koAllele.setNote(note);

        // Return the populated koAllele object.
        //
        return koAllele;
    }

}
