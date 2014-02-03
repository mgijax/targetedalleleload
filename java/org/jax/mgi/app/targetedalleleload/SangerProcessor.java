package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.*;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.app.targetedalleleload.lookups.LookupAlleleByKey;
import org.jax.mgi.app.targetedalleleload.lookups.LookupAllelesByMarker;
import org.jax.mgi.app.targetedalleleload.lookups.LookupAllelesByProjectId;
import org.jax.mgi.app.targetedalleleload.lookups.LookupMarkerByMGIID;
import org.jax.mgi.dbs.mgd.lookup.VocabKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.ParentStrainLookupByParentKey;

import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.dbs.mgd.lookup.TranslationException;
import org.jax.mgi.shr.exception.MGIException;

/**
 * @is An object that knows how to create KOMP Clone objects from the CSD allele
 *     file
 * @has <UL>
 *      <LI>KOMP Clone object.
 *      </UL>
 * @does <UL>
 *       <LI>Parses a CSD Allele file record into a KOMP Clone object
 *       <LI>
 *       </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class SangerProcessor 
extends KnockoutAlleleProcessor 
{

    private TargetedAlleleLoadCfg cfg;
    private LookupMarkerByMGIID lookupMarkerByMGIID;
    private VocabKeyLookup vocabLookup;
    private LookupAllelesByProjectId lookupAllelesByProjectId;
    private LookupAllelesByMarker lookupAllelesByMarker;
    private ParentStrainLookupByParentKey parentStrainLookupByParentKey;
    private StrainKeyLookup strainKeyLookup;
    private LookupAlleleByKey lookupAlleleByKey;

    private String PROMOTER_DRIVEN = "";
    private String PROMOTER_LESS = "";

    private Pattern alleleSequencePattern = Pattern.compile(".*tm(\\d{1,2})[ae]{0,1}.*");
    private Matcher regexMatcher;

    /**
     * Constructs a KnockoutAllele processor object.
     * 
     * @assumes Nothing
     * @effects Nothing
     * @throws ConfigException
     * @throws DLALoggingException
     *             if a logger instance cannot be obtained
     * @throws DBException
     *             if a database access error occurs
     * @throws CacheException
     * @throws TranslationException
     */
    public SangerProcessor() 
    throws MGIException 
    {
	cfg = new TargetedAlleleLoadCfg();

	PROMOTER_DRIVEN = cfg.getPromoterDrivenCassettes();
	PROMOTER_LESS = cfg.getPromoterLessCassettes();

	lookupAllelesByProjectId = LookupAllelesByProjectId.getInstance();
	lookupAllelesByMarker = LookupAllelesByMarker.getInstance();
	lookupMarkerByMGIID = LookupMarkerByMGIID.getInstance();
	vocabLookup = new VocabKeyLookup(Constants.ALLELE_VOCABULARY);
	parentStrainLookupByParentKey = new ParentStrainLookupByParentKey();
	strainKeyLookup = new StrainKeyLookup();
	lookupAlleleByKey = LookupAlleleByKey.getInstance();
    }

    public SangerProcessor(TargetedAlleleLoadCfg cfg,
	String PROMOTER_DRIVEN,
	String PROMOTER_LESS,
	LookupAllelesByProjectId lookupAllelesByProjectId,
	LookupAllelesByMarker lookupAllelesByMarker,
	LookupMarkerByMGIID lookupMarkerByMGIID,
	VocabKeyLookup vocabLookup,
	ParentStrainLookupByParentKey parentStrainLookupByParentKey,
	StrainKeyLookup strainKeyLookup,
	LookupAlleleByKey lookupAlleleByKey
	) 
    throws MGIException 
    {
	    
	// Stub for testing
	this.PROMOTER_DRIVEN = PROMOTER_DRIVEN;
	this.PROMOTER_LESS = PROMOTER_LESS;

    }

    /**
     * Set all the attributes of the clone object by parsing the given input
     * record and providing Sanger Specific constant values.
     * 
     * @assumes Nothing
     * @effects Loads the clone object.
     * @param inputData
     *            A record from the Sanger allele input file
     * @return An KnockoutAllele object
     * @throws RecordFormatException
     * @throws ConfigException
     * @throws DBException
     * @throws CacheException
     * @throws TranslationException
     */
    public KnockoutAllele process(KnockoutAlleleInput inputData)
    throws MGIException 
    {

	SangerAlleleInput in = (SangerAlleleInput) inputData;

	KnockoutAllele koAllele = new KnockoutAllele();

	// Get the external dependencies referenced in this row
	Marker marker = lookupMarkerByMGIID.lookup(in.getGeneId());
	Integer strainKey = strainKeyLookup
	    .lookup(parentStrainLookupByParentKey.lookup(cfg
		.getParentalKey(in.getParentCellLine())));

	koAllele.setMarkerKey(marker.getKey());
	koAllele.setProjectId(in.getProjectId());
	koAllele.setProjectLogicalDb(cfg.getProjectLogicalDb());
	koAllele.setStrainKey(strainKey);

	// CSD Specific Mutation types
	List mutationTypeKeys = new ArrayList();
	String[] types = cfg.getMutationTypes(in.getMutationType()).split(",");
	for (int i = 0; i < types.length; i++) {
	    Integer key = vocabLookup.lookup(types[i].trim());
	    mutationTypeKeys.add(key);
	}
	koAllele.setMutationTypes(mutationTypeKeys);
	koAllele.setCollection(Constants.COLLECTION_EUCOMM);
	// //////////////////////////////////////////////////////////////
	// get the NEXT allele symbol sequence number and letter
	// //////////////////////////////////////////////////////////////

	// Setup the default allele string
	// Default the allele sequence letter, the allele note, the deletion
	// size (only used for deletion alleles) and cache the mutation type
	String let = "";
	String note = getNoteTemplate(in);
	int delSize = getDeletionSize(in); // not used for conditional alleles
	String mutType = in.getMutationType();
	// Determine the "type" of the allele based on the entry in
	// the configuration file and set the rest of the mutation type
	// specific values
	if (mutType.equals("Conditional")) {
	    let = "a";
	    koAllele.setTypeKey(cfg.getAlleleType("CONDITIONAL"));
	    qcStatistics.record("SUMMARY",
		"Number of conditional input record(s)");
	} else if (mutType.equals("Targeted non-conditional")) {
	    let = "e";

	    // Default value
	    koAllele.setTypeKey(cfg.getAlleleType("NONCONDITIONAL"));

	    if (in.getCassette().equals("L1L2_Del_BactPneo_FFL")) {
		// SPECIAL CASE:
		// Per C.Smith and H.Dene 2010-11-03, alleles with this
		// cassette don't have a reporter, so they should not be
		// of type "reporter" but they do have a region flanked
		// by LoxP so allele type = "Targeted (Floxed/Frt)" (same
		// as regular conditional alleles)
		koAllele.setTypeKey(cfg.getAlleleType("CONDITIONAL"));
	    }

	    qcStatistics.record("SUMMARY",
			    "Number of targeted non-conditional input record(s)");
	} else if (mutType.equals("Deletion")) {
	    let = ""; // Empty string for Deletion alleles
	    koAllele.setTypeKey(cfg.getAlleleType("DELETION"));
	    qcStatistics.record(
		"SUMMARY", 
		"Number of deletion input record(s)");
	} else {
	    qcStatistics.record("ERROR",
		"Number of records with unknown mutation type");
	    throw new MGIException(
		"Unknown mutation type\n" + mutType + " | " + koAllele);
	}

	int seq = 1;
	HashSet existingAlleles = lookupAllelesByMarker.lookup(marker
	    .getSymbol());

	// If this marker has existing alleles already, default
	// the sequence to the next one which will be used only
	// if there is not a good match to an existing allele
	if (existingAlleles != null) {
	    // Loop through the existing alleles counting them up
	    Iterator alleleSetIt = existingAlleles.iterator();
	    while (alleleSetIt.hasNext()) {
		Integer nextKey = (Integer) alleleSetIt.next();
		KnockoutAllele existingKoAllele = lookupAlleleByKey
		    .lookup(nextKey);
		if (existingKoAllele == null) 
		{
		    String m = "Unable to find allele for key ";
		    m += nextKey;
		    m += " (for cellline ";
		    m += in.getMutantCellLine();
		    m += ")";
		    throw new MGIException(m);
		}
		String allSymbol = existingKoAllele.getSymbol();

		// Get the map version of the allele
		Map alleles = lookupAllelesByProjectId.lookup(in.getProjectId());
		Map allele = null;

		// Find the matching allele record in the alleleByProject
		// set
		if (alleles != null) {
		    Iterator aIt = alleles.entrySet().iterator();
		    while (aIt.hasNext() && allele == null) {
			Map.Entry entry = (Map.Entry) aIt.next();
			Map tmpAllele = (HashMap) entry.getValue();
			if (((String) tmpAllele.get("symbol"))
				.equals(allSymbol)) {
			    allele = tmpAllele;
			}
		    }

		    if (allele != null) {
			String extProjID = existingKoAllele.getProjectId();
			String inProjID = in.getProjectId();
			Integer extParentKey = (Integer) allele
			    .get("parentCellLineKey");
			Integer inParentKey = cfg.getParentalKey(in
			    .getParentCellLine());

			// Check if the project ID and the parental is the
			// same as the allele being constructed
			if (extProjID.equals(inProjID)
				&& extParentKey.equals(inParentKey)) {
			    // If the project IDs and parental match, then
			    // use this allele sequence number as the default
			    regexMatcher = alleleSequencePattern
				.matcher(allSymbol);
			    if (regexMatcher.find()) {
				seq = Integer.parseInt(regexMatcher.group(1));
				break;
			    }
			}
		    }
		}

		// bump up the sequence if this allele is larger than the
		// largest seen so far
		regexMatcher = alleleSequencePattern.matcher(allSymbol);
		if (regexMatcher.find()) {
		    if (Integer.parseInt(regexMatcher.group(1)) >= seq) {
			seq = Integer.parseInt(regexMatcher.group(1)) + 1;
		    }
		}
	    }
	}

	String finalSequence = new Integer(seq).toString() + let;

	// Set the clone's constructed values
	String alleleName = cfg.getNameTemplate();
	alleleName = alleleName.replaceAll("~~SYMBOL~~", marker.getSymbol());
	alleleName = alleleName.replaceAll("~~SEQUENCE~~", finalSequence);
	koAllele.setName(alleleName);

	String alleleSymbol = cfg.getSymbolTemplate();
	alleleSymbol = alleleSymbol
	    .replaceAll("~~SYMBOL~~", marker.getSymbol());
	alleleSymbol = alleleSymbol.replaceAll("~~SEQUENCE~~", finalSequence);
	koAllele.setSymbol(alleleSymbol);

	koAllele.setJNumbers(cfg.getJNumbers());

	if (note == "") {
	    qcStatistics.record("ERROR",
		"Number of records that can't generate a molecular note");
	    throw new MGIException("Missing note\n" + koAllele);
	}

	note = note.replaceAll("~~CASSETTE~~", in.getCassette());
	note = note.replaceAll("~~LOCUS1~~", in.getLocus1());
	note = note.replaceAll("~~LOCUS2~~", in.getLocus2());
	note = note.replaceAll("~~CHROMOSOME~~", marker.getChromosome());
	note = note.replaceAll("~~DELSIZE~~", Integer.toString(delSize));
	note = note.replaceAll("~~BUILD~~", in.getBuild());
	if (in.getCassette().matches(PROMOTER_DRIVEN)) {
	    note = note.replaceAll("~~PROMOTER~~",
		cfg.getPromoter(in.getCassette().toUpperCase()));
	}
	koAllele.setNote(note);

	// Return the populated clone object.
	//
	return koAllele;
    }

    protected int getDeletionSize(SangerAlleleInput in) 
    throws MGIException 
    {
	int delSize = 0;

	// Calculate the deletion size
	if (in.getLocus1().compareTo("0") != 0
		&& in.getLocus2().compareTo("0") != 0) {
	    int delStart = Integer.parseInt(in.getLocus1());
	    int delEnd = Integer.parseInt(in.getLocus2());
	    delSize = Math.abs(delEnd - delStart);
	} else {
	    qcStatistics.record("ERROR",
		"Number of records missing coordinates");
	    throw new MGIException("Missing coordinates: "
		+ in.getMutantCellLine());
	}
	return delSize;
    }

    protected String getNoteTemplate(SangerAlleleInput in)
    throws MGIException 
    {
	String note = "";

	// There is a special case for L1L2_Del_BactPneo_FFL cassettes
	// There is a speciatl case for mutation sub-type = 'Artificial Intron'
	// all other cassettes are treated equally
	
	String cassette = in.getCassette();

	if (in.getMutationType().equals("Conditional")) {
	    if (cassette.equals("L1L2_Del_BactPneo_FFL")) {
		note = cfg.getNoteTemplateCondDel_BactPneo_FFL();
	    } else if (cassette.matches(PROMOTER_DRIVEN)) {
		if (in.getMutationSubType().equals("Artificial Intron")) {
		    note = cfg.getNoteTemplateCondAIPromoter();
		}
		else {
		    note = cfg.getNoteTemplateCondPromoter();
		}
	    } else if (cassette.matches(PROMOTER_LESS)) {
		note = cfg.getNoteTemplateCondPromoterless();
	    } else {
		qcStatistics.record(
		    "ERROR",
		    "Number of records missing cassette in CFG file");
		throw new MGIException(
		    "Missing cassette type in CFG file: " + cassette);
	    }
	} else if (in.getMutationType().equals("Targeted non-conditional")) {
	    if (cassette.equals("L1L2_Del_BactPneo_FFL")) {
		note = cfg.getNoteTemplateNonCondDel_BactPneo_FFL();
	    } else if (cassette.matches(PROMOTER_DRIVEN)) {
		if (in.getMutationSubType().equals("Artificial Intron")) {
		    note = cfg.getNoteTemplateNonCondAIPromoter();
		}
		else {
		    note = cfg.getNoteTemplateNonCondPromoter();
		}
	    } else if (cassette.matches(PROMOTER_LESS)) {
		note = cfg.getNoteTemplateNonCondPromoterless();
	    } else {
		qcStatistics.record(
		    "ERROR",
		    "Number of records missing cassette in CFG file");
		throw new MGIException(
		    "Missing cassette type in CFG file: " + cassette);
	    }
	} else if (in.getMutationType().equals("Deletion")) {
	    if (cassette.matches(PROMOTER_DRIVEN)) {
		note = cfg.getNoteTemplateDeletionPromoter();
	    } else if (cassette.matches(PROMOTER_LESS)) {
		note = cfg.getNoteTemplateDeletionPromoterless();
	    } else {
		qcStatistics.record(
		    "ERROR",
		    "Number of records missing cassette in CFG file");
		throw new MGIException(
		    "Missing cassette type in CFG file: " + cassette);
	    }
	}
	return note;
    }
}
