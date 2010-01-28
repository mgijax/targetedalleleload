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
 * the EUCOMM allele file 
 * @has
 *   <UL>
 *   <LI> KOMP Clone object.
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Parses a EUCOMM Allele file record into a KOMP Clone object
 *   <LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class EUCOMMProcessor extends KnockoutAlleleProcessor
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
    private AlleleLookupByProjectId alleleLookupByProjectId = null;
    private AlleleLookupByMarker alleleLookupByMarker = null;
    private ParentStrainLookupByParentKey parentStrainLookupByParentKey = null;
    private StrainKeyLookup strainKeyLookup = null;
    private KnockoutAlleleLookup koAlleleLookup = null;

    
    private String PROMOTER_DRIVEN = "";
    private String PROMOTER_LESS = "";

    private Pattern alleleSequencePattern = null;
    private Pattern alleleTypePattern = null;
    private Matcher regexMatcher = null;



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
    public EUCOMMProcessor ()
    throws ConfigException,DLALoggingException,
    DBException,CacheException,TranslationException
    {
        cfg = new TargetedAlleleLoadCfg();
        
        PROMOTER_DRIVEN = cfg.getPromoterDrivenCassettes();
        PROMOTER_LESS = cfg.getPromoterLessCassettes();
        
        Integer projectLogicalDB = cfg.getProjectLogicalDb();

        //alleleLookupByProjectId = new AlleleLookupByProjectId(projectLogicalDB);
        //alleleLookupByMarker = new AlleleLookupByMarker(projectLogicalDB);
        alleleLookupByProjectId = AlleleLookupByProjectId.getInstance(projectLogicalDB);
        alleleLookupByMarker = AlleleLookupByMarker.getInstance(projectLogicalDB);
        markerLookup = new MarkerLookupByMGIID();
		vocabLookup = new VocabKeyLookup(Constants.ALLELE_VOCABULARY);
		parentStrainLookupByParentKey = new ParentStrainLookupByParentKey();
		strainKeyLookup = new StrainKeyLookup();
        koAlleleLookup = KnockoutAlleleLookup.getInstance();

		
		alleleSequencePattern = Pattern.compile(".*tm(\\d){1,2}[ae]{0,1}.*");
		alleleTypePattern = Pattern.compile(".*tm\\d{1,2}([ae]){0,1}.*");
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
        EUCOMMAlleleInput in = (EUCOMMAlleleInput)inputData;

        KnockoutAllele koAllele = new KnockoutAllele();

        // Get the external dependencies referenced in this row
        Marker marker = markerLookup.lookup(in.getGeneId());
        Integer strainKey = strainKeyLookup.lookup(parentStrainLookupByParentKey.lookup(cfg.getParentalKey(in.getParentCellLine())));

        koAllele.setMarkerKey(marker.getKey());
        koAllele.setProjectId(in.getProjectId());
        koAllele.setProjectLogicalDb(cfg.getProjectLogicalDb());
        koAllele.setStrainKey(strainKey);

        // EUCOMM Specific Mutation types
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
        // Default the allele sequence letter, the allele note, the deletion
        // size (only used for deletion alleles) and cache the mutation type
        String let = "";
        String note = "";
        int delSize = 0;
        String mutType = in.getMutationType();

        // Determine the "type" of the allele based on the entry in 
        // the configuration file and set the rest of the mutation type
        // specific values
        if (mutType.equals("Conditional"))
        {
            let = "a";
            koAllele.setTypeKey(cfg.getAlleleType("CONDITIONAL"));
            qcStatistics.record("SUMMARY", "Number of conditional input record(s)");

            // Setup the conditional note template
            if (in.getCassette().matches(PROMOTER_DRIVEN)) {
                note = cfg.getNoteTemplateCondPromoter();
            } else if (in.getCassette().matches(PROMOTER_LESS)) {
                note = cfg.getNoteTemplateCondPromoterless();
            } else {
                qcStatistics.record("ERROR", "Number of records missing cassette in CFG file");
                throw new MGIException(
                    "Missing cassette type in CFG file: "+in.getCassette()
                );
            }
        }
        else if (mutType.equals("Targeted non-conditional"))
        {
            let = "e";
            koAllele.setTypeKey(cfg.getAlleleType("NONCONDITIONAL"));
            qcStatistics.record("SUMMARY", "Number of targeted non-conditional input record(s)");

            // Setup the targeted nonconditional note template
            if (in.getCassette().matches(PROMOTER_DRIVEN)) {
                note = cfg.getNoteTemplateNonCondPromoter();
            } else if (in.getCassette().matches(PROMOTER_LESS)) {
                note = cfg.getNoteTemplateNonCondPromoterless();
            } else {
                qcStatistics.record("ERROR", "Number of records missing cassette in CFG file");
                throw new MGIException(
                    "Missing cassette type in CFG file: "+in.getCassette()
                );
            }
        }
        else if (mutType.equals("Deletion"))
        {
            let = ""; // Empty string for Deletion alleles
            koAllele.setTypeKey(cfg.getAlleleType("DELETION"));
            qcStatistics.record("SUMMARY", "Number of deletion input record(s)");
            
            // Setup the deletion note template
            if (in.getCassette().matches(PROMOTER_DRIVEN)) {
                note = cfg.getNoteTemplateDeletionPromoter();
            } else if (in.getCassette().matches(PROMOTER_LESS)) {
                note = cfg.getNoteTemplateDeletionPromoterless();
            } else {
                qcStatistics.record("ERROR", "Number of records missing cassette in CFG file");
                throw new MGIException(
                    "Missing cassette type in CFG file: "+in.getCassette()
                );
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
                qcStatistics.record("ERROR", "Number of records missing coordinates");
                throw new MGIException(
                    "Missing coordinates\n"+koAllele
                );
            }
        }
        else
        {
            qcStatistics.record("ERROR", "Number of records with unknown mutation type");
            throw new MGIException(
                "Unknown mutation type\n" + mutType + " | " +koAllele
            );
        }

        int seq = 1;
        HashSet existingAlleles = alleleLookupByMarker.lookup(marker.getSymbol());

        // If this marker has existing alleles already, default
        // the sequence to the next one which will be used only
        // if there is not a good match to an existing allele
        if (existingAlleles != null)
        {
            // Loop through the existing alleles counting them up
            Iterator alleleSetIt = existingAlleles.iterator();
            while (alleleSetIt.hasNext())
            {
                Integer nextKey = (Integer) alleleSetIt.next();
                KnockoutAllele existingKoAllele = koAlleleLookup.lookup(nextKey);
                String allSymbol = existingKoAllele.getSymbol();

                // Get the hashmap version of the allele
                HashMap alleles = alleleLookupByProjectId.lookup(in.getProjectId());
                HashMap allele = null;

                if (alleles != null)
                {
                    Set entries = alleles.entrySet();
                    Iterator aIt = entries.iterator();
                    while (aIt.hasNext())
                    {
                        Map.Entry entry = (Map.Entry) aIt.next();
                        HashMap tmpAllele = (HashMap) entry.getValue();
                        if (((String) tmpAllele.get("symbol")).equals(allSymbol))
                        {
                            allele = tmpAllele;
                            break;
                        }
                    }


                    if (allele != null)
                    {
                        // Check if the project ID and the parental is the same as 
                        // the allele being constructed
                        if (existingKoAllele.getProjectId().equals(in.getProjectId()) && 
                            allele.get("parentCellLineKey").equals(cfg.getParentalKey(in.getParentCellLine())))
                        {
                            // If the project IDs and parental match, then 
                            // use this allele sequence number as the default.
                            regexMatcher = alleleSequencePattern.matcher(allSymbol);
                            if (regexMatcher.find())
                            {
                                seq = Integer.parseInt(regexMatcher.group(1));
                                break;
                            }
                        }                        
                    }
                }

                // String thisAlleleType = "";
                // // Get type of allele being checked
                // regexMatcher = alleleTypePattern.matcher(allSymbol);
                // if (regexMatcher.find())
                // {
                //     thisAlleleType = regexMatcher.group(1);
                // }
                // 
                // // If the allele being compared is not of the correct
                // // "type", then don't use it to increment the sequence
                // // only alleles of the same type should bump the default
                // // seqence
                // if (!thisAlleleType.equals(let))
                // {
                //     continue;
                // }

                // Ok, same type, reset the sequence if it's larger and the project matches
                regexMatcher = alleleSequencePattern.matcher(allSymbol);
                if (regexMatcher.find())
                {
                    if (Integer.parseInt(regexMatcher.group(1)) >= seq)
                    {
                        seq = Integer.parseInt(regexMatcher.group(1))+1;
                    }
                }

            }
        }


        String finalSequence = new Integer(seq).toString() + let;

        // // Now that we have a sensible default for the allele 
        // // sequence and letter ... see if we can find an actual 
        // // allele that exists with matching Parental Cell Line
        // // and Project ID (this encompasses the vector since projects are
        // // granular to the targeting vector)
        // HashMap alleles = alleleLookupByProjectId.lookup(in.getProjectId());
        // HashMap matchingAllele = null;
        // 
        // if (alleles != null && alleles.size() > 0)
        // {
        //     Boolean alleleFound = Boolean.FALSE;
        //     Set entries = alleles.entrySet();
        //     Iterator it = entries.iterator();
        // 
        //     while (it.hasNext() && alleleFound != Boolean.TRUE)
        //     {
        //         Map.Entry entry = (Map.Entry) it.next();
        //         HashMap allele = (HashMap)entry.getValue();
        // 
        //         String allSymbol = (String)allele.get("symbol");
        //         String thisAlleleType = "";
        // 
        //         //Compare type
        //         regexMatcher = alleleTypePattern.matcher(allSymbol);
        //         if (regexMatcher.find())
        //         {
        //             thisAlleleType = regexMatcher.group(1);
        //         }
        // 
        //         // Compare parental cell lines
        //         Integer fromFile = cfg.getParentalKey(in.getParentCellLine());
        //         Integer fromData = (Integer)allele.get("parentCellLineKey");
        // 
        //         // If Project and parental match use the seq 
        //         // If Project, parental and type match use seq and let
        //         if (fromData.equals(fromFile))
        //         {
        //             // MATCHES! reset the sequence to match the current allele and
        //             // short circuit the loop since we found the correct allele
        //             
        //             regexMatcher = alleleSequencePattern.matcher(allSymbol);
        //             if (regexMatcher.find())
        //             {
        //                 finalSequence = regexMatcher.group(1) + let;
        //                 alleleFound = Boolean.TRUE;
        //             }
        //         }
        //     }
        // }
        // 

        // Set the clone's constructed values
        String alleleName = cfg.getNameTemplate(in.getMutantCellLine());
        alleleName = alleleName.replaceAll("~~SYMBOL~~", marker.getSymbol()); 
        alleleName = alleleName.replaceAll("~~SEQUENCE~~", finalSequence); 
        koAllele.setName(alleleName);

        String alleleSymbol = cfg.getSymbolTemplate(in.getMutantCellLine());
        alleleSymbol = alleleSymbol.replaceAll("~~SYMBOL~~", marker.getSymbol()); 
        alleleSymbol = alleleSymbol.replaceAll("~~SEQUENCE~~", finalSequence); 
        koAllele.setSymbol(alleleSymbol);
        

        String jNumber = cfg.getJNumber();
        koAllele.setJNumber(jNumber);


        if (note == "")
        {
            qcStatistics.record("ERROR", "Number of records that can't generate a molecular note");
            throw new MGIException(
                "Missing note\n"+koAllele
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
            note = note.replaceAll("~~PROMOTER~~", cfg.getPromoter(in.getCassette().toUpperCase()));
        }
        koAllele.setNote(note);

        // Return the populated clone object.
        //
        return koAllele;
    }

}
