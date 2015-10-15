package org.jax.mgi.app.targetedalleleload;

/**
 * @is An object that represents a CSD Knockout Allele Input record.
 * @has <UL>
 *      <LI>Configuration parameters that are needed to populate a
 *      KnockoutAllele object.
 *      </UL>
 * @does <UL>
 *       <LI>Provides methods for setting all its attributes.
 *       <LI>Encapsulates a single row of data from the input file</LI>
 *       </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

public class SangerAlleleInput implements KnockoutAlleleInput {
	// ///////////////
	// Variables //
	// ///////////////

	// Set the attributes of the inputData object using the fields parsed
	// from the input record.
	// 0 - mgi_accession_id
	// 1 - assembly
	// 2 - cassette
	// 3 - pipeline
	// 4 - ikmc_project_id
	// 5 - es_cell_clone
	// 6 - parent_cell_line
	// 7 - allele_symbol_superscript
	// 8 - mutation_type
	// 9 - mutation_subtype
	// 10 - cassette_start
	// 11 - cassette_end
	// 12 - loxp_start
	// 13 - loxp_end
	// 14 - is_mixed

	// From file
	private String geneId = null;
	private String build = null;
	private String cassette = null;
	private String inputPipeline = null;
	private String projectId = null;
	private String esCellName = null;
	private String parentESCellName = null;
	private String mutationType = null;
	private String mutationSubType = null;
	private String locus1 = null;
	private String locus2 = null;

	/**
	 * Constructs a Knockout Allele Input object
	 * 
	 * @assumes Nothing
	 * @effects Set the class variables.
	 */
	public SangerAlleleInput() {
	}

	public String getGeneId() {
		return geneId;
	}

	public String getMutantCellLine() {
		return esCellName;
	}

	public String getParentCellLine() {
		return parentESCellName;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getLocus1() {
		return locus1;
	}

	public String getLocus2() {
		return locus2;
	}

	public String getBuild() {
		return build;
	}

	public String getCassette() {
		return cassette;
	}

	public String getMutationType() {
		return mutationType;
	}
        public String getMutationSubType() {
                return mutationSubType;
        }
	public void setESCellName(String esCellName) {
		this.esCellName = esCellName;
	}

	public void setMutationType(String mutationType) {
		this.mutationType = mutationType;
	}
        public void setMutationSubType(String mutationSubType) {
                this.mutationSubType = mutationSubType;
        }


	public void setParentESCellName(String parentESCellName) {
		// The Sanger parental cell names come in with all sorts
		// of strange characters. Strip them off before storing the
		// Parent ES Cell line name so we can do comparisons easier
		this.parentESCellName = parentESCellName.toUpperCase()
				.replaceAll("\\s+", "").replaceAll("\\(", "")
				.replaceAll("\\)", "").replaceAll("/", "")
				.replaceAll("\\?", "").replaceAll("\\n", "")
				.replaceAll(" ", "").replaceAll("\\.", "");
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public void setGeneId(String geneId) {
		this.geneId = geneId;
	}

	public void setLocus1(String locus1) {
		this.locus1 = locus1;
	}

	public void setLocus2(String locus2) {
		this.locus2 = locus2;
	}

	public void setBuild(String build) {
		this.build = build.replaceAll("NCBIM", "");
	}

	public void setCassette(String cassette) {
		this.cassette = cassette;
	}

	public void setInputPipeline(String inputPipeline) {
		this.inputPipeline = inputPipeline;
	}

	public String getInputPipeline() {
		return inputPipeline;
	}

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SangerAlleleInput other = (SangerAlleleInput) obj;
        if ((this.geneId == null) ? (other.geneId != null) : !this.geneId.equals(other.geneId)) {
            return false;
        }
        if ((this.build == null) ? (other.build != null) : !this.build.equals(other.build)) {
            return false;
        }
        if ((this.cassette == null) ? (other.cassette != null) : !this.cassette.equals(other.cassette)) {
            return false;
        }
        if ((this.inputPipeline == null) ? (other.inputPipeline != null) : !this.inputPipeline.equals(other.inputPipeline)) {
            return false;
        }
        if (this.projectId != other.projectId && (this.projectId == null || !this.projectId.equals(other.projectId))) {
            return false;
        }
        if (this.esCellName != other.esCellName && (this.esCellName == null || !this.esCellName.equals(other.esCellName))) {
            return false;
        }
        if (this.parentESCellName != other.parentESCellName && (this.parentESCellName == null || !this.parentESCellName.equals(other.parentESCellName))) {
            return false;
        }
        if ((this.mutationType == null) ? (other.mutationType != null) : !this.mutationType.equals(other.mutationType)) {
            return false;
        }
        if ((this.mutationSubType == null) ? (other.mutationSubType != null) : !this.mutationSubType.equals(other.mutationSubType)) {
            return false;
        }

        if (this.locus1 != other.locus1 && (this.locus1 == null || !this.locus1.equals(other.locus1))) {
            return false;
        }
        if (this.locus2 != other.locus2 && (this.locus2 == null || !this.locus2.equals(other.locus2))) {
            return false;
        }
        return true;
    }
	
    public String toString() {
    	String s = "SangerAlleleInput<"+geneId+", ";
    	s += build+", ";
    	s += cassette+", ";
    	s += inputPipeline+", ";
    	s += projectId+", ";
    	s += esCellName+", ";
    	s += parentESCellName+", ";
    	s += mutationType+", ";
        s += mutationSubType+", ";
    	s += locus1+", ";
    	s += locus2;
    	s += ">";
		return s;
    	
    }
	

}
