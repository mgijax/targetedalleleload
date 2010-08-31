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
	public static final int NOTE_TYPE = 1021;

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
	public static final int ALLELE_STATUS = 847114;

	/*
	 * VOC_Term key for transmission (Cell Line)
	 */
	public static final int TRANSMISSION_KEY = 3982953;

	/*
	 * MGI_RefAssocType keys for reference types (Original, Molecular)
	 */
	public static final int[] REFERENCE_ASSOC = { 1011, 1012 };

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
		MUTATION_TYPE_KEYS.put("Deletion", "847116");
		MUTATION_TYPE_KEYS.put("Conditional", "847118");
		MUTATION_TYPE_KEYS.put("Targeted non-conditional", "847119");
	}
	
	public static final int VECTOR_TYPE_KEY = 3982979;

}
