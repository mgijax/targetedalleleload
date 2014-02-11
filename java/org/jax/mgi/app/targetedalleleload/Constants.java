package org.jax.mgi.app.targetedalleleload;

import java.util.HashMap;
import java.util.Map;

/**
 * Constants defined for the TargetedAlleleLoad
 * 
 * @has constant definitions
 * @does nothing
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class Constants {

	/*
	 * ACC_LogicalDB key for MGI
	 */
	public static final int LOGICALDB_MGI = 1;

	/*
	 * ACC_MGIType key for Allele
	 */
	public static final int ALLELE_MGI_TYPE = 11;

	/**
	 * ACC_MGIType key for ES Cell type
	 */
	public static final int ESCELL_MGITYPE_KEY = 28;

	/**
	 * VOC_Term key for ES Cell type "Embryonic Stem Cell"
	 */
	public static final int ESCELL_TYPE_KEY = 3982968;

	/*
	 * MGI_NoteType key for note type (Molecular)
	 */
	public static final int NOTE_TYPE_MOLECULAR = 1021;

	/**
	 * VOC_Vocab key for allele vocabulary
	 */
	public static final int ALLELE_VOCABULARY = 36;

	/*
	 * VOC_Term key for Allele type (Targeted knock out)
	 */
	public static final int ALLELE_TYPE = 847116;

	/*
	 * VOC_Term key for Allele mode (N/A)
	 */
	public static final int ALLELE_MODE = 847095;

	/*
	 * VOC_Term key for Allele status (Approved)
	 */
	public static final int ALLELE_STATUS_APPROVED = 847114;
	public static final int ALLELE_STATUS_DELETED = 847112;

	/*
	 * VOC_Term key for transmission (Cell Line)
	 */
	public static final int ALLELE_TRANSMISSION_CELLLINE = 3982953;

	/*
	 * MGI_RefAssocType keys for reference types (Original, Molecular)
	 */
	public static final int ORIGINAL_REFERENCE = 1011;
	public static final int MOLECULAR_REFERENCE = 1012;

	/*
	 * VOC_Term key for Marker-Allele Association Qualifier "Not Specified"
	 */
	public static final int MKR_ASSOC_QUAL_NS_KEY = 4268547;

	/*
	 * VOC_Term key for Marker-Allele Association Status "Curated"
	 */
	public static final int MKR_ASSOC_STAT_CURATED_KEY = 4268545;

	/*
	 * VOC_Term key for Marker-Allele Association Status "Curated"
	 */
	public static final Map MUTATION_TYPE_KEYS = new HashMap();
	static {
		MUTATION_TYPE_KEYS.put("Deletion", new Integer(847116));
		MUTATION_TYPE_KEYS.put("Conditional", new Integer(847118));
		MUTATION_TYPE_KEYS.put("Targeted non-conditional", new Integer(847119));
	}
	
	public static final Integer ALLELE_TYPE_KEY = new Integer(847116);

	public static final int VECTOR_TYPE_KEY = 3982979;
	
	public static final Integer MARKER_OFFICIAL = new Integer(1);
	public static final Integer MARKER_WITHDRAWN = new Integer(2);

	/*
	 *  Collection Keys
	 */
	public static final Integer COLLECTION_KOMP_CSD = new Integer(11025570);
	public static final Integer COLLECTION_EUCOMM = new Integer(11025572);

}
