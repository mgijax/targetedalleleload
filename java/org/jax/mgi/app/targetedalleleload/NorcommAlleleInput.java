/**
 * 
 */
package org.jax.mgi.app.targetedalleleload;

/**
 * @author jmason
 *
 */
public class NorcommAlleleInput implements KnockoutAlleleInput {


	// The fields in the INPUT FILE from NorCOMM:
	// 0 - Gene MGI ID
	// 1 - IKMC Project ID
	// 2 - es cell ID
	// 3 - pipeline (NorCOMM)
	// 4 - mutation type
	// 5 - mutation subtype
	// 6 - parental (always C2)
	// 7 - cassette
	// 8 - start
	// 9 - end
	// 10 - chromosome
	// 11 - strand
	// 12 - genome build

	private String geneId;
	private String projectId;
	private String mutantCellLine;
	private String inputPipeline;
	private String parentCellLine;
	private String cassette;
	private Integer start = new Integer(0);
	private Integer end = new Integer(0);
	private Integer delSize = new Integer(0);
	private String chromosome;
	private String strand;
	private String build;
	
	/**
	 * empty constructor
	 */
	public NorcommAlleleInput() {}

	/**
	 * Getters and setters
	 */

	public String getGeneId() {
		return geneId;
	}

	public void setGeneId(String geneId) {
		this.geneId = geneId;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getMutantCellLine() {
		return mutantCellLine;
	}

	public void setMutantCellLine(String esCellName) {
		this.mutantCellLine = esCellName;
	}

	public String getInputPipeline() {
		return inputPipeline;
	}

	public void setInputPipeline(String pipeline) {
		this.inputPipeline = pipeline;
	}

	// All NorCOMM alleles are of type Deletion
	public String getMutationType() {
		return "Deletion";
	}

	public String getParentCellLine() {
		return parentCellLine;
	}

	public void setParentCellLine(String parentCellLine) {
		this.parentCellLine = parentCellLine;
	}

	public String getCassette() {
		return cassette;
	}

	public void setCassette(String cassette) {
		this.cassette = cassette;
	}

	public Integer getStart() {
		return start;
	}

	public void setStart(Integer start) {
		this.start = start;
	}

	public Integer getEnd() {
		return end;
	}

	public void setEnd(Integer end) {
		this.end = end;
	}

	public Integer getDelSize() {
		return delSize;
	}

	public void setDelSize(Integer delSize) {
		this.delSize = delSize;
	}

	public String getChromosome() {
		return chromosome;
	}

	public void setChromosome(String chromosome) {
		this.chromosome = chromosome;
	}

	public String getStrand() {
		return strand;
	}

	public void setStrand(String strand) {
		this.strand = strand;
	}

	public String getBuild() {
		return build;
	}

	public void setBuild(String build) {
		this.build = build;
	}

}
