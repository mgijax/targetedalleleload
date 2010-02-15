package org.jax.mgi.app.targetedalleleload;

/**
 * @is An object that represents a CSD Knockout Allele Input record.
 * @has
 *   <UL>
 *   <LI> Configuration parameters that are needed to populate a
 * KnockoutAllele object.
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Provides methods for setting all its attributes.
 *   <LI> Encapsulates a single row of data from the input file</LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

public class SangerAlleleInput implements KnockoutAlleleInput
{
    private final String PROVIDER = "CSD";
    /////////////////
    //  Variables  //
    /////////////////

    // fields parsed from an input record:
    // 0 - Gene ID
    // 1 - Genome Build
    // 2 - Cassette
    // 3 - Project ID
    // 4 - Mutant ES cell line ID
    // 5 - Parent ES cell line name
    // 6 - Mutation type
    // 7 - Insertion point 1
    // 8 - Insertion point 2

    // From file
    private String geneId = null;
    private String build = null;
    private String cassette = null;
    private String projectId = null;
    private String esCellName = null;
    private String parentESCellName = null;
    private String mutationType = null;
    private String locus1 = null;
    private String locus2 = null;

    /**
     * Constructs a Knockout Allele Input object
     * @assumes Nothing
     * @effects Set the class variables.
     */
    public SangerAlleleInput()
    {}

    // Getters
    public String getProvider() {return PROVIDER;}
    public String getGeneId() {return geneId;}
	public String getMutantCellLine() {return esCellName;}
	public String getParentCellLine() {return parentESCellName;}
	public String getProjectId() {return projectId;}
	public String getLocus1() {return locus1;}
	public String getLocus2() {return locus2;}
	public String getBuild() {return build;}
	public String getCassette() {return cassette;}
	public String getMutationType() {return mutationType;}

    // Setters
	public void setESCellName(String esCellName)
	{
		this.esCellName = esCellName;
	}

	public void setMutationType(String mutationType)
	{
		this.mutationType = mutationType;
	}

	public void setParentESCellName(String parentESCellName)
	{
	    // The CSD parental cell names come in from CSD with all sorts
	    // of strange characters.  Strip them off before storing the 
	    // Parent ES Cell line name so we can do comparisions easier
		this.parentESCellName = parentESCellName.toUpperCase()
		    .replaceAll("\\s+", "")
		    .replaceAll("\\(", "")
		    .replaceAll("\\)", "")
		    .replaceAll("/", "")
		    .replaceAll("\\?", "")
		    .replaceAll("\\.","");
	}

	public void setProjectId(String projectId)
	{
		this.projectId = projectId;
	}

	public void setGeneId(String geneId)
	{
		this.geneId = geneId;
	}

	public void setLocus1(String locus1)
	{
		this.locus1 = locus1;
	}

	public void setLocus2(String locus2)
	{
		this.locus2 = locus2;
	}

	public void setBuild(String build)
	{
		this.build = build.replaceAll("NCBIM", "");
	}

	public void setCassette(String cassette)
	{
		this.cassette = cassette;
	}

}
