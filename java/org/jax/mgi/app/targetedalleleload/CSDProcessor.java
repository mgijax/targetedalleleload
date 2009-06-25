package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import java.lang.Math;
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
import org.jax.mgi.shr.exception.MGIException;


/**
 * @is An object that knows how to create KOMP Clone objects from 
 * the CSD allele file 
 * @has
 *   <UL>
 *   <LI> KOMP Clone object.
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Parses a CSD Allele file record into a KOMP Clone object
 *   <LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class CSDProcessor extends KnockoutAlleleProcessor
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
    private StrainByEsCellLookup strainByEsCellLookup = null;
    

    private static final String PROMOTER_DRIVEN = "L1L2_Bact_P|L1L2_PGK_P";
    private static final String PROMOTER_LESS = "L1L2_gt0|L1L2_gt1|L1L2_gt2|L1L2_gtK|L1L2_st0|L1L2_st1|L1L2_st2";

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
    public CSDProcessor ()
    throws ConfigException,DLALoggingException,
    DBException,CacheException,TranslationException
    {
        cfg = new TargetedAlleleLoadCfg();
        allelesByMarkerLookup = new AlleleByMarkerLookup(cfg.getProjectLogicalDb());
        markerLookup = new MarkerLookup();
        strainLookup = new StrainLookup();
        strainByEsCellLookup = new StrainByEsCellLookup();
		escellLookup = new ESCellLookup();
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
    DBException,CacheException,TranslationException,MGIException
    {
        CSDAlleleInput in = (CSDAlleleInput)inputData;

        KnockoutAllele clone = new KnockoutAllele();

        // Get the external dependencies referenced in this row
        Marker marker = markerLookup.lookup(in.getGeneId());
		ESCell parental = escellLookup.lookupExisting(in.getParentESCellName());
        Strain strain = strainByEsCellLookup.lookup(parental.getName());
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


        String note = "";
        int delSize = 0;

        if (in.getMutationType().compareTo("Deletion") == 0)
        {
            if (in.getCassette().matches(PROMOTER_DRIVEN))
            {
                note = cfg.getNoteTemplateDeletionPromoter();
            }
            else if (in.getCassette().matches(PROMOTER_LESS))
            {
                note = cfg.getNoteTemplateDeletionPromoterless();
            }
            
            // Calculate the deletion size
            if (in.getLocus1().compareTo("0") != 0 && 
                in.getLocus2().compareTo("0") != 0 )
            {
                int delStart = Integer.parseInt(in.getLocus1());
                int delEnd = Integer.parseInt(in.getLocus2());
                delSize = Math.abs(delEnd - delStart);
            }
            else
            {
                throw new MGIException(
                    "SKIPPING THIS RECORD: missing coordinate\n"+clone
                );
            }
        }
        else if (in.getMutationType().compareTo("Conditional") == 0)
        {
            if (in.getCassette().matches(PROMOTER_DRIVEN))
            {
                note = cfg.getNoteTemplateCondPromoter();
            }
            else if (in.getCassette().matches(PROMOTER_LESS))
            {
                note = cfg.getNoteTemplateCondPromoterless();
            }
        }
        else if (in.getMutationType().compareTo("Targeted non-conditional") == 0)
        {
            if (in.getCassette().matches(PROMOTER_DRIVEN))
            {
                note = cfg.getNoteTemplateNonCondPromoter();
            }
            else if (in.getCassette().matches(PROMOTER_LESS))
            {
                note = cfg.getNoteTemplateNonCondPromoterless();
            }
        }
        else
        {
            throw new MGIException(
                "SKIPPING THIS RECORD: Unknown mutation type\n"+clone
            );
        }

        note = note.replaceAll("~~CASSETTE~~", clone.getCassette()); 
        note = note.replaceAll("~~LOCUS1~~", in.getLocus1());
        note = note.replaceAll("~~LOCUS2~~", in.getLocus2());
        note = note.replaceAll("~~CHROMOSOME~~", marker.getChromosome());
        note = note.replaceAll("~~DELSIZE~~", Integer.toString(delSize));
        note = note.replaceAll("~~BUILD~~", clone.getGenomeBuild());
        if (in.getCassette().matches(PROMOTER_DRIVEN))
        {
            note = note.replaceAll("~~PROMOTER~~", cfg.getPromoter(clone.getCassette()));
        }
        clone.setAlleleNote(note);

        // Return the populated clone object.
        //
        return clone;
    }

}
