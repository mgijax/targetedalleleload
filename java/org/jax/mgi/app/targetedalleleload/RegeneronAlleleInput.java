package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;

/**
 * @is An object that represents a Regeneron Knockout Allele Input record.
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

public class RegeneronAlleleInput implements KnockoutAlleleInput
{
    /////////////////
    //  Variables  //
    /////////////////

    // fields parsed from an input record:
    // 0 - alleleId
    // 1 - Parental ES Cell
    // 2 - Mouse Strain 
    // 3 - REGN Allele ID
    // 4 - Gene Symbol
    // 5 - MGI ID 
    // 6 - Del Start
    // 7 - Del End
    // 8 - Del Size
    // 9 - Genome Build
    // 10 - Cassette

    // From file
    private String alleleId = null;
    private String parentalESCellName = null;
    private String strainName = null;
    private String projectName = null;
    private String geneSymbol = null;
    private String geneMgiId = null;
    private Integer delStart = new Integer(0);
    private Integer delEnd = new Integer(0);
    private Integer delSize = new Integer(0);
    private String build = null;
    private String cassette = null;

    /**
     * Constructs a Knockout Allele Input object
     * @assumes Nothing
     * @effects Set the class variables.
     */
    public RegeneronAlleleInput ()
    {
    }

	public String getAlleleId() {
		return alleleId;
	}

	public String getParentalESCellName() {
		return parentalESCellName;
	}

	public String getStrainName() {
		return strainName;
	}

	public String getProjectName() {
		return projectName;
	}

	public String getGeneSymbol() {
		return geneSymbol;
	}

	public String getGeneMgiId() {
		return geneMgiId;
	}

	public Integer getDelStart() {
		return delStart;
	}

	public Integer getDelEnd() {
		return delEnd;
	}

	public Integer getDelSize() {
		return delSize;
	}

	public String getBuild() {
		return build;
	}

	public String getCassette() {
		return cassette;
	}

	public void setAlleleId(String alleleId) {
		this.alleleId = alleleId;
	}

	public void setParentalESCellName(String parentalESCellName) {
		this.parentalESCellName = parentalESCellName;
	}

	public void setStrainName(String strainName) {
		this.strainName = strainName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public void setGeneSymbol(String geneSymbol) {
		this.geneSymbol = geneSymbol;
	}

	public void setGeneMgiId(String geneMgiId) {
		this.geneMgiId = geneMgiId;
	}

	public void setDelStart(Integer delStart) {
		this.delStart = delStart;
	}

	public void setDelEnd(Integer delEnd) {
		this.delEnd = delEnd;
	}

	public void setDelSize(Integer delSize) {
		this.delSize = delSize;
	}

	public void setBuild(String build) {
		this.build = build;
	}

	public void setCassette(String cassette) {
		this.cassette = cassette;
	}


}