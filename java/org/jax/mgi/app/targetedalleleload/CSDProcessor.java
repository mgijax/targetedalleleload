package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import java.util.Vector;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.*;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.dbs.mgd.lookup.VocabKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.ParentStrainLookupByParentKey;

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
    private MarkerLookupByMGIID markerLookup = null;
    private VocabKeyLookup vocabLookup = null;
    private DerivationLookupByVectorCreatorParent derivationLookup = null;
    private AlleleLookupByProjectId alleleLookpuByProjectId = null;
    private ProjectLookupByMarker projectByMarkerLookup = null;
    private AlleleLookupByMarker alleleLookupByMarker = null;
    private ParentStrainLookupByParentKey parentStrainLookupByParentKey = null;
    private StrainKeyLookup strainKeyLookup = null;
    
    
    

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
        
        Integer escLogicalDB = cfg.getEsCellLogicalDb();
        Integer projectLogicalDB = cfg.getProjectLogicalDb();

        alleleLookpuByProjectId = new AlleleLookupByProjectId(escLogicalDB);
        projectByMarkerLookup = new ProjectLookupByMarker(projectLogicalDB);
        alleleLookupByMarker = new AlleleLookupByMarker(projectLogicalDB);
        markerLookup = new MarkerLookupByMGIID();
		vocabLookup = new VocabKeyLookup(Constants.ALLELE_VOCABULARY);
		parentStrainLookupByParentKey = new ParentStrainLookupByParentKey();
		strainKeyLookup = new StrainKeyLookup();
    }

    public void addToProjectCache(String projectId, HashMap alleleMap)
    throws DBException, CacheException
    {
        alleleLookpuByProjectId.addToCache(projectId, alleleMap);
    }

    public void addToMarkerCache(String symbol, HashSet alleles)
    throws DBException, CacheException
    {
        alleleLookupByMarker.addToCache(symbol, alleles);
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

        KnockoutAllele koAllele = new KnockoutAllele();

        // Get the external dependencies referenced in this row
        Marker marker = markerLookup.lookup(in.getGeneId());
        Integer strainKey = strainKeyLookup.lookup(parentStrainLookupByParentKey.lookup(cfg.getParentalKey(in.getParentCellLine())));

        koAllele.setMarkerKey(marker.getKey());
        koAllele.setProjectId(in.getProjectId());
        koAllele.setProjectLogicalDb(cfg.getProjectLogicalDb());
        koAllele.setStrainKey(strainKey);

        // CSD Specific Mutation types
        Vector mutationTypeKeys = new Vector();
        String[] types = cfg.getMutationTypes(in.getMutationType()).split(",");
        for (int i=0;i<types.length;i++)
        {
            Integer key = vocabLookup.lookup(types[i].trim());
            mutationTypeKeys.add(key);
        }
        koAllele.setMutationTypes(mutationTypeKeys);


        /////////////////////////////////////////////////////////////////////
        // get the NEXT allele symbol sequence number and letter
        /////////////////////////////////////////////////////////////////////

        // Setup the default allele string
        int seq = 1;
        HashSet existingAlleles = alleleLookupByMarker.lookup(marker.getSymbol());
        
        // If we have existing alleles already, we can go ahead and default
        // the value to the next one which will be used IF there is not a 
        // good match already
        if (existingAlleles != null)
        {
            // Loop through the existing alleles counting them up
            Iterator alleleSetIt = existingAlleles.iterator();
            while (alleleSetIt.hasNext())
            {
                String allSymbol = (String)alleleSetIt.next();
                Pattern allPattern = Pattern.compile(".*tm(\\d){1,2}[ae]{0,1}.*");
                Matcher allMatcher = allPattern.matcher(allSymbol);
                if (allMatcher.find())
                {
                    if (Integer.parseInt(allMatcher.group(1)) >= seq)
                    {
                        seq = Integer.parseInt(allMatcher.group(1))+1;
                    }
                }

            }
        }

        // The sequence letter
        // Blank (the default) is for deletion alleles
        String let = "";
        String mutType = in.getMutationType();
        if (mutType.compareTo("Conditional") == 0)
        {
            let = "a";
        }
        else if (mutType.compareTo("Targeted non-conditional") == 0)
        {
            let = "e";
        }
        String finalSequence = new Integer(seq).toString() + let;

        // Okay... now that we have a sensible default... see if we can
        // find an actual allele that exists already with the correct
        // attributes 

        HashMap alleles = alleleLookpuByProjectId.lookup(in.getProjectId());
        HashMap matchingAllele = null;

        if (alleles != null && alleles.size() > 0)
        {
            Boolean alleleFound = Boolean.FALSE;
            Set entries = alleles.entrySet();
            Iterator it = entries.iterator();

            while (it.hasNext() && alleleFound != Boolean.TRUE)
            {
                Map.Entry entry = (Map.Entry) it.next();
                HashMap allele = (HashMap)entry.getValue();
                String allMutType = "Deletion";
                
                if (((String)allele.get("symbol")).matches(".*tm\\d{1,2}a.*"))
                {
                    allMutType = "Conditional";
                }
                else if (((String)allele.get("symbol")).matches(".*tm\\d{1,2}e.*"))
                {
                    allMutType = "Targeted non-conditional";
                }

                // Compare parental cell lines
                Integer fromFile = cfg.getParentalKey(in.getParentCellLine());
                Integer fromData = cfg.getParentalKey((String)allele.get("parentCellLine"));
                if (fromData.compareTo(fromFile) == 0)
                {
                    // MATCHES! reset the sequence to match the current allele
                    Pattern pattern = Pattern.compile(".*<tm(\\d{1,2})[ae]{0,1}\\(.*");
                    Matcher matcher = pattern.matcher(((String)allele.get("symbol")));
                    if (matcher.find())
                    {
                        finalSequence = matcher.group(1) + let;
                    }
                    else
                    {
                        continue;
                    }
                    alleleFound = Boolean.TRUE;
                }
            }

            if (alleleFound != Boolean.TRUE)
            {
                Integer newAllele = new Integer(alleles.size() + 1);
                finalSequence = newAllele.toString() + let;
            }
        }
        

        // Set the clone's constructed values
        String alleleName = cfg.getNameTemplate();
        alleleName = alleleName.replaceAll("~~SYMBOL~~", marker.getSymbol()); 
        alleleName = alleleName.replaceAll("~~SEQUENCE~~", finalSequence); 
        koAllele.setName(alleleName);

        String alleleSymbol = cfg.getSymbolTemplate();
        alleleSymbol = alleleSymbol.replaceAll("~~SYMBOL~~", marker.getSymbol()); 
        alleleSymbol = alleleSymbol.replaceAll("~~SEQUENCE~~", finalSequence); 
        koAllele.setSymbol(alleleSymbol);
        

        String jNumber = cfg.getJNumber();
        koAllele.setJNumber(jNumber);


        String note = "";
        int delSize = 0;

        if (in.getMutationType().compareTo("Deletion") == 0)
        {
            koAllele.setTypeKey(cfg.getAlleleType("DELETION"));
            
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
                    "SKIPPING THIS RECORD: missing coordinate\n"+koAllele
                );
            }
        }
        else if (in.getMutationType().compareTo("Conditional") == 0)
        {
            koAllele.setTypeKey(cfg.getAlleleType("CONDITIONAL"));

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
            koAllele.setTypeKey(cfg.getAlleleType("NONCONDITIONAL"));

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
                "SKIPPING THIS RECORD: Unknown mutation type\n"+in.getMutationType() + "|" +koAllele
            );
        }

        note = note.replaceAll("~~CASSETTE~~", in.getCassette()); 
        note = note.replaceAll("~~LOCUS1~~", in.getLocus1());
        note = note.replaceAll("~~LOCUS2~~", in.getLocus2());
        note = note.replaceAll("~~CHROMOSOME~~", marker.getChromosome());
        note = note.replaceAll("~~DELSIZE~~", Integer.toString(delSize));
        note = note.replaceAll("~~BUILD~~", in.getBuild());
        if (in.getCassette().matches(PROMOTER_DRIVEN))
        {
            note = note.replaceAll("~~PROMOTER~~", cfg.getPromoter(in.getCassette()));
        }
        koAllele.setNote(note);

        // Return the populated clone object.
        //
        return koAllele;
    }

}
