package org.jax.mgi.app.targetedalleleload;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jax.mgi.dbs.mgd.lookup.ParentStrainLookupByParentKey;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.TranslationException;
import org.jax.mgi.dbs.mgd.lookup.VocabKeyLookup;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.exception.MGIException;

/**
 * An object that knows how to create a KnockoutAllele object from 
 * a NorcommAlleleInput
 */

public class NorcommProcessor 
extends KnockoutAlleleProcessor 
{
	
	private TargetedAlleleLoadCfg cfg;
	private LookupMarkerByMGIID lookupMarkerByMGIID;
	private ParentStrainLookupByParentKey parentStrainLookupByParentKey;
	private StrainKeyLookup strainKeyLookup;
	private VocabKeyLookup vocabLookup;
	private LookupAllelesByProjectId lookupAllelesByProjectId;
	private LookupAllelesByMarker lookupAllelesByMarker;

	private Matcher regexMatcher;
	private Pattern sequencePattern = Pattern.compile(".*tm(\\d{1,2}).*");

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
	public NorcommProcessor() 
	throws MGIException 
	{
		cfg = new TargetedAlleleLoadCfg();

		parentStrainLookupByParentKey = new ParentStrainLookupByParentKey();
		lookupAllelesByMarker = LookupAllelesByMarker.getInstance();
		lookupAllelesByProjectId = LookupAllelesByProjectId.getInstance();
		lookupMarkerByMGIID = LookupMarkerByMGIID.getInstance();
		vocabLookup = new VocabKeyLookup(Constants.ALLELE_VOCABULARY);
		strainKeyLookup = new StrainKeyLookup();
	}

	/**
	 * Set all the attributes of the clone object by parsing the given input
	 * record and providing NorCOMM Specific constant values.
	 */
	public KnockoutAllele process(KnockoutAlleleInput inputData)
	throws MGIException 
	{
		// Cast the input to a NorCOMM specific allele input type
		NorcommAlleleInput in = (NorcommAlleleInput) inputData;

		KnockoutAllele allele = new KnockoutAllele();

		// Get the external dependencies referenced in this row
		Marker marker = lookupMarkerByMGIID.lookup(in.getGeneId());
		Integer strainKey = strainKeyLookup
			.lookup(parentStrainLookupByParentKey.lookup(
				cfg.getParentalKey(in.getParentCellLine())));

		allele.setMarkerKey(marker.getKey());
		allele.setProjectId(in.getProjectId());
		allele.setProjectLogicalDb(cfg.getProjectLogicalDb());
		allele.setStrainKey(strainKey);

		// NorCOMM alleles are all DELETION type alleles (Targeted Knockout)
		allele.setTypeKey(cfg.getAlleleType("DELETION"));

		// NorCOMM Specific Mutation types
		Vector mutationTypeKeys = new Vector();
		String[] types = cfg.getMutationTypes().split(",");
		for (int i = 0; i < types.length; i++) {
			Integer key = vocabLookup.lookup(types[i].trim());
			mutationTypeKeys.add(key);
		}
		allele.setMutationTypes(mutationTypeKeys);

		// get the NEXT allele symbol sequence number which is one higher 
		// than the highest sequence numbered targeted allele attached 
		// to this marker BY THIS lab (identified by ILAR lab code)
		int seq = 1;
		HashSet existingAlleles = lookupAllelesByMarker.lookup(marker
			.getSymbol());

		if (existingAlleles != null) {
			// This lab has created alleles for this marker previously.
			// Set the sequence number to one higher than the count of 
			// all alleles made by this lab for this marker
			seq = (existingAlleles.size()) + 1;
		}

		// If there is already an allele created that has this IKMC project
		// ID, associate that allele sequence number.  All alleles produced
		// by the same project should have the same sequence number.
		Map alleles = lookupAllelesByProjectId.lookup(in.getProjectId());
		if (alleles != null && alleles.size() > 0) {
			Boolean alleleFound = Boolean.FALSE;
			Set entries = alleles.entrySet();
			Iterator it = entries.iterator();

			// Go through all the targeted alleles that belong to this
			// IKMC project.  There will most likely be zero or one 
			// alleles
			while (it.hasNext() && alleleFound != Boolean.TRUE) {
				Map.Entry entry = (Map.Entry) it.next();
				Map a = (Map) entry.getValue();

				String allSymbol = (String) a.get("symbol");
				regexMatcher = sequencePattern.matcher(allSymbol);
				if (regexMatcher.find()) {
					seq = Integer.parseInt(regexMatcher.group(1));
					alleleFound = Boolean.TRUE;
					qcStatistics.record("SUMMARY",
							"Number of records that match an existing allele");
				}
			}
		}

		// Set the allele name
		String alleleName = cfg.getNameTemplate();
		alleleName = alleleName.replaceAll("~~SYMBOL~~", marker.getSymbol());
		alleleName = alleleName.replaceAll("~~SEQUENCE~~",
				Integer.toString(seq));
		allele.setName(alleleName);

		// Set the allele symbol
		String alleleSymbol = cfg.getSymbolTemplate();
		alleleSymbol = alleleSymbol
				.replaceAll("~~SYMBOL~~", marker.getSymbol());
		alleleSymbol = alleleSymbol.replaceAll("~~SEQUENCE~~",
				Integer.toString(seq));
		allele.setSymbol(alleleSymbol);

		// Set the allele reference number
		allele.setJNumbers(cfg.getJNumbers());

		String note = cfg.getNoteTemplateDeletionPromoterless();

		String cassette = in.getCassette();
		// Promotor driven clones all use the same "L1L2_GOHANU" vector
		if (cassette.equals("L1L2_GOHANU")) {
			// Promotor driven cassette
			note = cfg.getNoteTemplateDeletionPromoter();
		} else {
			// Promoterless cassette
			// The cassette name always starts with "pNTARU" and ends with 
			// a number or letter that is the last character of the vector.
			// "pNTARU" is included into the note template, insert just 
			// the last letter of the vector
			note = note.replaceAll(
				"~~CASSETTE~~", 
				cassette.substring(cassette.length())
				);			
		}
		
		// Set the allele molecular note
		note = note.replaceAll("~~SIZE~~", in.getDelSize().toString());
		note = note.replaceAll("~~START~~", in.getStart().toString());
		note = note.replaceAll("~~END~~", in.getEnd().toString());
		note = note.replaceAll("~~CHROMOSOME~~", marker.getChromosome());
		note = note.replaceAll("~~BUILD~~", in.getBuild());
		allele.setNote(note);

		// Return the constructed KnockoutAllele
		return allele;
	}

}
