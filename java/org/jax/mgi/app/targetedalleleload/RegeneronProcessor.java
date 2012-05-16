package org.jax.mgi.app.targetedalleleload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jax.mgi.app.targetedalleleload.lookups.LookupAllelesByMarker;
import org.jax.mgi.app.targetedalleleload.lookups.LookupAllelesByProjectId;
import org.jax.mgi.app.targetedalleleload.lookups.LookupMarkerByMGIID;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.TranslationException;
import org.jax.mgi.dbs.mgd.lookup.VocabKeyLookup;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.RecordFormatException;

/**
 * An object that knows how to create a KnockoutAllele object from 
 * a RegeneronAlleleInput
 */

public class RegeneronProcessor 
extends KnockoutAlleleProcessor 
{
	
	private TargetedAlleleLoadCfg cfg;
	private LookupMarkerByMGIID lookupMarkerByMGIID;
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
	 * @throws MGIException if something goes wrong
	 */
	public RegeneronProcessor() 
	throws MGIException 
	{
		cfg = new TargetedAlleleLoadCfg();

		lookupAllelesByProjectId = LookupAllelesByProjectId.getInstance();
		lookupAllelesByMarker = LookupAllelesByMarker.getInstance();
		lookupMarkerByMGIID = LookupMarkerByMGIID.getInstance();
		vocabLookup = new VocabKeyLookup(Constants.ALLELE_VOCABULARY);
		strainKeyLookup = new StrainKeyLookup();
	}

	/**
	 * Set all the attributes of the clone object by parsing the given 
	 * input record and providing Regeneron Specific constant values.
	 * 
	 * @assumes Nothing
	 * @effects Loads the clone object.
	 * @param inputData
	 *            A record from the Regeneron allele input file
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
		// Cast the input to a Regeneron specific allele input type
		RegeneronAlleleInput in = (RegeneronAlleleInput) inputData;

		KnockoutAllele koAllele = new KnockoutAllele();

		// Get the external dependencies referenced in this row
		Marker marker = lookupMarkerByMGIID.lookup(in.getGeneId());
		Integer strainKey = strainKeyLookup.lookup(in.getStrainName());

		koAllele.setMarkerKey(marker.getKey());
		koAllele.setProjectId(in.getProjectId());
		koAllele.setProjectLogicalDb(cfg.getProjectLogicalDb());
		koAllele.setStrainKey(strainKey);

		// Regeneron alleles are all DELETION type alleles 
		// (Targeted Knockout)
		koAllele.setTypeKey(cfg.getAlleleType("DELETION"));

		// Regeneron Specific Mutation types
		List mutationTypeKeys = new ArrayList();
		String[] types = cfg.getMutationTypes().split(",");
		for (int i = 0; i < types.length; i++) {
			Integer key = vocabLookup.lookup(types[i].trim());
			mutationTypeKeys.add(key);
		}
		koAllele.setMutationTypes(mutationTypeKeys);

		////////////////////////////////////////////////////////////
		// Get the sequence number for the allele
		////////////////////////////////////////////////////////////

		int	seq = 1;
		
		HashSet existingAlleles = lookupAllelesByMarker.lookup(
			marker.getSymbol());

		if (existingAlleles != null) {
			// This lab has created alleles for this marker previously.
			// Set the sequence number to one higher than the count of 
			// all alleles made by this lab for this marker
			seq = (existingAlleles.size()) + 1;
		}

		// If there is already an allele created that has this IKMC 
		// project ID, associate that allele sequence number.  
		// All alleles produced by the same project should have the 
		// same sequence number.
		Map alleles = lookupAllelesByProjectId.lookup(in.getProjectId());

		if (alleles != null && alleles.size() > 0) {

			boolean alleleFound = false;

			// Go through all the targeted alleles that belong to this
			// IKMC project.  There will most likely be zero or one 
			// alleles
			Set entries = alleles.entrySet();
			Iterator it = entries.iterator();

			while (it.hasNext() && !alleleFound) {
				Map.Entry entry = (Map.Entry) it.next();
				Map a = (Map) entry.getValue();

				String allSymbol = (String) a.get("symbol");
				regexMatcher = sequencePattern.matcher(allSymbol);
				if (regexMatcher.find()) {
					seq = Integer.parseInt(regexMatcher.group(1));
					alleleFound = true;
					qcStatistics.record(
						"SUMMARY",
						"Number of records matching an existing allele");
				}
			}
		}

		////////////////////////////////////////////////////////////
		// Populate the koAllele object
		////////////////////////////////////////////////////////////

		String alleleName = cfg.getNameTemplate()
			.replaceAll("~~SYMBOL~~", marker.getSymbol())
			.replaceAll("~~SEQUENCE~~",	Integer.toString(seq));
		koAllele.setName(alleleName);

		String alleleSymbol = cfg.getSymbolTemplate()
			.replaceAll("~~SYMBOL~~", marker.getSymbol())
			.replaceAll("~~SEQUENCE~~", Integer.toString(seq));
		koAllele.setSymbol(alleleSymbol);

		koAllele.setJNumbers(cfg.getJNumbers());

		String note = cfg.getNoteTemplate();

		// If coordinates are missing, use the incomplete note template
		if (in.getDelSize().toString().equals("0") ||
			in.getDelStart().toString().equals("0") ||
			in.getDelEnd().toString().equals("0")
			) {
			note = cfg.getNoteTemplateMissingCoords();
		}

		// Build the molecular note
		note = note.replaceAll("~~CASSETTE~~", in.getCassette());
		note = note.replaceAll("~~SIZE~~", in.getDelSize().toString());
		note = note.replaceAll("~~START~~", in.getDelStart().toString());
		note = note.replaceAll("~~END~~", in.getDelEnd().toString());
		note = note.replaceAll("~~CHROMOSOME~~", marker.getChromosome());
		note = note.replaceAll("~~BUILD~~", in.getBuild());
		koAllele.setNote(note);

		return koAllele;
	}

}
