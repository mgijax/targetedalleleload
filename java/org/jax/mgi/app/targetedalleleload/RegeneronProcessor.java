package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import java.util.Vector;
import java.util.Iterator;
import java.util.HashSet;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.dbs.mgd.lookup.VocabKeyLookup;

import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.dbs.mgd.lookup.TranslationException;
import org.jax.mgi.shr.cache.KeyNotFoundException;


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

public class RegeneronProcessor extends KnockoutAlleleProcessor
{
    /////////////////
    //  Variables  //
    /////////////////

    // KOMP Clone object
    //
    private TargetedAlleleLoadCfg cfg = null;
    private MarkerLookup markerLookup = null;
    private StrainLookup strainLookup = null;
    private ESCellLookup escellLookup = null;
    private VocabKeyLookup vocabLookup = null;
    private AlleleByMarkerLookup allelesByMarkerLookup = null;
    

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
        allelesByMarkerLookup = new AlleleByMarkerLookup(cfg.getProjectLogicalDb());
        markerLookup = new MarkerLookup();
        strainLookup = new StrainLookup();
		escellLookup = new ESCellLookup(strainLookup);
		vocabLookup = new VocabKeyLookup(Constants.ALLELE_VOCABULARY);
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
    DBException,CacheException,TranslationException
    {
        RegeneronAlleleInput in = (RegeneronAlleleInput)inputData;

        KnockoutAllele clone = new KnockoutAllele();

        // Get the external dependencies referenced in this row
        Marker marker = markerLookup.lookup(in.getGeneMgiId());
        Strain strain = strainLookup.lookup(in.getStrainName());
		ESCell parental = escellLookup.lookupExisting(in.getParentalESCellName());
		ESCell mutant = escellLookup.lookup(in.getESCellName());
		
		// If the mutant cell line doesn't exist, we must create
		// a new one.
		if (mutant == null)
		{
		    // The mutant cell line has the same ID as the allele
		    // for all the KOMP generated alleles
		    mutant = new ESCell(0,
                strain,
                in.getESCellName(),
                cfg.getProvider(),
                true
                );
            String esCellDB = cfg.getEsCellLogicalDb();
            mutant.setLogicalDB(Integer.parseInt(esCellDB));
		}

        clone.setESCellName(in.getESCellName());
        clone.setGene(marker);
        clone.setMutant(mutant);
        clone.setParental(parental);
        clone.setProjectId(in.getProjectId());
        clone.setProjectLogicalDb(cfg.getProjectLogicalDb());
        clone.setStrain(strain);
        clone.setProvider(cfg.getProvider());
        clone.setDelStart(in.getDelStart());
        clone.setDelEnd(in.getDelEnd());
        clone.setDelSize(in.getDelSize());
        clone.setGenomeBuild(in.getBuild());
        clone.setCassette(in.getCassette());

        // Regeneron Specific Mutation types
        Vector mutationTypeKeys = new Vector();
        String[] types = cfg.getMutationTypes().split(",");
        for (int i=0;i<types.length;i++)
        {
            Integer key = vocabLookup.lookup(types[i].trim());
            mutationTypeKeys.add(key);
        }
        clone.setMutationTypes(mutationTypeKeys);

        // get the NEXT allele symbol sequence number which is one higher than
        // the sum of CURRENT KOMP ALELLES attached to this marker BY THIS
        // PROVIDER
        HashSet allProj = allelesByMarkerLookup.lookup(marker.getSymbol());

        int seq = 1; // Default to the first project
        if (allProj != null) {
            // There is already a project (or projects) associated to this
            // marker.  Increment the count for this allele
            seq = (allProj.size()) + 1;
        }

        // Set the clone's constructed values
        String alleleName = cfg.getNameTemplate();
        alleleName = alleleName.replaceAll("~~SYMBOL~~", clone.getGeneSymbol()); 
        alleleName = alleleName.replaceAll("~~SEQUENCE~~", Integer.toString(seq)); 
        clone.setAlleleName(alleleName);

        String alleleSymbol = cfg.getSymbolTemplate();
        alleleSymbol = alleleSymbol.replaceAll("~~SYMBOL~~", clone.getGeneSymbol()); 
        alleleSymbol = alleleSymbol.replaceAll("~~SEQUENCE~~", Integer.toString(seq)); 
        clone.setAlleleSymbol(alleleSymbol);

        String jNumber = cfg.getJNumber();
        clone.setJNumber(jNumber);

        String note = cfg.getNoteTemplate();
        note = note.replaceAll("~~CASSETTE~~", clone.getCassette()); 
        note = note.replaceAll("~~SIZE~~", clone.getDelSize().toString());
        note = note.replaceAll("~~START~~", clone.getDelStart().toString());
        note = note.replaceAll("~~END~~", clone.getDelEnd().toString());
        note = note.replaceAll("~~CHROMOSOME~~", clone.getChromosome()); 
        note = note.replaceAll("~~BUILD~~", clone.getGenomeBuild()); 
        clone.setAlleleNote(note);

        // Return the populated clone object.
        //
        return clone;
    }

}
