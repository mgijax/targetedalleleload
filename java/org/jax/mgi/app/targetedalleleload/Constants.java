package org.jax.mgi.app.targetedalleleload;

/**
 * Constants defined for the TargetedAlleleLoad
 * @has constant definitions
 * @does nothing
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class Constants
{

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
    public static final int[] REFERENCE_ASSOC = {1011, 1012};

}
