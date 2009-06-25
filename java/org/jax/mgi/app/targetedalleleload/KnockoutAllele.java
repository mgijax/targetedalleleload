package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import java.lang.Math;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashMap;
import java.util.Comparator;
import java.sql.Timestamp;
import java.util.Date;

import org.jax.mgi.shr.config.RecordStampCfg;
import org.jax.mgi.dbs.mgd.lookup.JNumberLookup;
import org.jax.mgi.shr.dbutils.dao.SQLStream;
import org.jax.mgi.dbs.mgd.AccessionLib;

import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.dbutils.DBException;

import org.jax.mgi.dbs.mgd.dao.ALL_AlleleState;
import org.jax.mgi.dbs.mgd.dao.ALL_AlleleDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_MutationState;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_MutationDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteState;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_Reference_AssocState;
import org.jax.mgi.dbs.mgd.dao.MGI_Reference_AssocDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteChunkState;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteChunkDAO;
import org.jax.mgi.dbs.mgd.dao.ACC_AccessionState;
import org.jax.mgi.dbs.mgd.dao.ACC_AccessionDAO;



/**
 * @is An object that represents a KnockOut Allele record.
 * @has
 *   <UL>
 *   <LI> Configuration parameters that are needed to populate the allele object.
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Provides methods for setting all its attributes.
 *   <LI> Provides a method for inserting an allele record into a BCP stream.
 *   <LI> Provides a method to clear its attributes.
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class KnockoutAllele implements Comparable
{
    /////////////////
    //  Variables  //
    /////////////////

    private final int NOTECHUNKSIZE = 255;
    
    private RecordStampCfg rdCfg = null;
    private JNumberLookup jnumLookup = null;

    private ESCell parental = null;
    private ESCell mutant = null;
    private Strain strain = null;
    private Marker gene = null;
    private Vector mutationTypes = new Vector();

    // From file
    private String esCellName = null;
    private Integer alleleType = new Integer(Constants.ALLELE_TYPE);
    private Boolean isWildType = new Boolean(false);
    private String build = null;
    private String cassette = null;
    private String provider = null;
    private String projectId = null;
    private int projectLogicalDb = 0;

    // From cfg file
    private String jNumber = null;          // Reference J Number

	// Parameters derived from file or read from database
    private String alleleSymbol = null;
    private String alleleName = null;
    private String alleleNote = null;
    private String alleleNoteKey = null;
    private Integer alleleNoteCreatedBy = null;
    private Integer alleleNoteModifiedBy = null;
    private int alleleKey = 0;

    /**
     * Constructs a Knockout Allele object
     * @assumes Nothing
     * @effects Set the class variables.
     * @throws ConfigException thrown if there is an error accessing the
     * configuration
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * cache
     */
    public KnockoutAllele ()
    throws ConfigException,DBException,CacheException
    {
        // To lookup the JNumber Key from the database
        jnumLookup = new JNumberLookup();

        // To get the approvedBy user key from the database
        rdCfg = new RecordStampCfg();
    }

    public int compareTo(Object that) throws ClassCastException
    {
        if (!(that instanceof KnockoutAllele))
        {
            throw new ClassCastException("A KnockoutAllele object expected.");
        }
        
        String thatESCellName = ((KnockoutAllele) that).getMutant().getName();  
        return this.getMutant().getName().compareTo(thatESCellName);
    }

    /////////////////////////////////////////////////////////////////////
    // Setters
    public void setMutationTypes(Vector mutationTypes)
    {
        this.mutationTypes = mutationTypes;
    }

    public void setProjectId(String projectId)
    {
        this.projectId = projectId;
    }

    public void setProjectLogicalDb(String projectLogicalDb)
    {
        this.projectLogicalDb = Integer.parseInt(projectLogicalDb);
    }

    public void setParental(ESCell parental)
    {
        this.parental = parental;
    }
    
    public void setMutant(ESCell mutant)
    {
        this.mutant = mutant;
    }
    
    public void setStrain(Strain strain)
    {
        this.strain = strain;
    }
    
    public void setGene(Marker gene){
        this.gene = gene;
    }
    
    public void setESCellName(String esCellName)
    {
        this.esCellName = esCellName;
    }

    public void setAlleleType(Integer alleleType)
    {
        this.alleleType = alleleType;
    }

    public void setGenomeBuild(String build)
    {
        this.build = build;
    }

    public void setCassette(String cassette)
    {
        this.cassette = cassette;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public void setJNumber(String jNumber)
    {
        this.jNumber = jNumber;
    }

    public void setAlleleKey(int key)
    {
        this.alleleKey = key;
    }

    public void setAlleleSymbol(String alleleSymbol)
    {
        this.alleleSymbol = alleleSymbol;
    }

    public void setAlleleSymbol(String template, int sequence)
    {
        String seq = Integer.toString(sequence);
        String symbol = template.replaceAll("~~SEQUENCE~~", seq); 
        this.alleleSymbol = symbol;
    }

    public void setAlleleName(String alleleName)
    {
        this.alleleName = alleleName;
    }

    public void setAlleleName(String template, int sequence)
    {
        String seq = Integer.toString(sequence);
        String name = template.replaceAll("~~SEQUENCE~~", seq); 
        this.alleleName = name;
    }

    public void setAlleleNote(String alleleNote)
    {
        this.alleleNote = alleleNote;
    }

    public void setAlleleNoteKey(String alleleNoteKey)
    {
        this.alleleNoteKey = alleleNoteKey;
    }

    public void setAlleleNoteCreatedBy(String createdBy)
    {
        this.alleleNoteCreatedBy = Integer.valueOf(createdBy);
    }

    public void setAlleleNoteModifiedBy(String modifiedBy)
    {
        this.alleleNoteModifiedBy = Integer.valueOf(modifiedBy);
    }



    /////////////////////////////////////////////////////////////////////
    // Getters

    public int getAlleleKey()
    {
        return this.alleleKey;
    }

	public Marker getGene()
	{
	    return this.gene;
	}

	public ESCell getMutant()
	{
	    return this.mutant;
	}

	public Strain getStrain()
	{
	    return this.strain;
	}
	
	public String getProjectId()
	{
	    return this.projectId;
	}

	public int getProjectLogicalDb()
	{
	    return this.projectLogicalDb;
	}

	public ESCell getParental()
	{
	    return this.parental;
	}

	public String getGeneSymbol()
	{
	    return this.gene.getSymbol();
	}

	public String getChromosome()
	{
	    return this.gene.getChromosome();
	}

	public String getCassette()
	{
	    return this.cassette;
	}

	public String getGenomeBuild()
	{
	    return this.build;
	}

	public String getESCellName()
	{
	    return this.esCellName;
	}

	public Integer getAlleleType()
	{
	    return this.alleleType;
	}

	public String getAlleleSymbol()
	{
	    return this.alleleSymbol;
	}

	public String getAlleleName()
	{
	    return this.alleleName;
	}

	public String getAlleleNote()
	{
	    return this.alleleNote;
	}

	public String getAlleleNoteKey()
	{
	    return this.alleleNoteKey;
	}

    public Integer getAlleleNoteCreatedBy()
    {
        return this.alleleNoteCreatedBy;
    }

    public Integer getAlleleNoteModifiedBy()
    {
        return this.alleleNoteModifiedBy;
    }


    public String toString()
    {
        return "Allele key: " + this.alleleKey+"\n"+
            "id: " + this.esCellName+"\n"+
            "name: " + this.alleleName+"\n"+
            "symbol: " + this.alleleSymbol+"\n"+
            "note: " + this.alleleNote+"\n"+
            "J Number: "+this.jNumber+"\n";
    }


    /**
     * Insert this Knockout Allele object into the stream to create the 
     * required bcp records.
     * @assumes Nothing
     * @effects Nothing
     * @param stream The bcp stream to write the bcp records to.
     * @return Nothing
     * @throws ConfigException if it can't lookup the JobStreamKey or
     * @throws DBException
     */
    public void updateNote (SQLStream stream, String newNote)
    throws ConfigException,DBException,CacheException
    {
        // Save this note as the allele note
        this.setAlleleNote(newNote);

        // Create the Note and attach it to this Allele
        MGI_NoteState nState =  new MGI_NoteState();
        nState.setObjectKey(new Integer(alleleKey));
        nState.setMGITypeKey(new Integer(Constants.ALLELE_MGI_TYPE));
        nState.setNoteTypeKey(new Integer(Constants.NOTE_TYPE));
        
        MGI_NoteDAO nDAO = new MGI_NoteDAO(nState);
        stream.insert(nDAO);
        
        // Set this note key to the newly created note key from the DB
        int noteKey = nDAO.getKey().getKey().intValue();

        // Insert the note chunks for this allele note
        for (int i=0; i<newNote.length(); i+=NOTECHUNKSIZE)
        {
            // Create the NoteChunk
            MGI_NoteChunkState ncState = new MGI_NoteChunkState();
            ncState.setNoteKey(new Integer(noteKey));

            // Calculate THIS note chunk sequence number based on how many
            // times we've gone through the loop already
            ncState.setSequenceNum(new Integer((i/NOTECHUNKSIZE)+1));

            // Set the note chunk to the current 255 characters if that will
            // not overflow the note, otherwise, just add whatever's left
            // to the notechunk.
            String thisNoteChunk = "";
            if (i + NOTECHUNKSIZE < newNote.length())
            {
                thisNoteChunk = newNote.substring(i, i + NOTECHUNKSIZE);
            }
            else
            {
                thisNoteChunk = newNote.substring(i, alleleNote.length());
            }
            ncState.setNote(thisNoteChunk);

            MGI_NoteChunkDAO ncDAO = new MGI_NoteChunkDAO(ncState);
            stream.insert(ncDAO);            
        }
    }

    /**
     * Insert this Knockout Allele object into the stream to create the 
     * required bcp records.
     * @assumes Nothing
     * @effects Nothing
     * @param stream The bcp stream to write the bcp records to.
     * @return Nothing
     * @throws ConfigException if it can't lookup the JobStreamKey or
     * @throws DBException
     */
    public void insert (SQLStream stream)
    throws ConfigException,DBException,CacheException
    {
        // Verify that the logical DB has been set
        if (projectLogicalDb == 0)
        {
            throw new ConfigException("Project Logical DB not configured", true);
        }

        // if the mutant cell line doesn't already exist, then
        //Create the mutant es cell line and insert it into the database
        if (mutant.getKey() == 0)
        {
            mutant.insert(stream);            
        }

        // Create this allele in the database, attaching the (possibly new)
        // mutant es cell to the allele
        ALL_AlleleState aState = new ALL_AlleleState();
        aState.setMarkerKey(new Integer(gene.getKey()));
        aState.setStrainKey(new Integer(strain.getKey()));
        aState.setModeKey(new Integer(Constants.ALLELE_MODE));
        aState.setAlleleTypeKey(alleleType);
        aState.setAlleleStatusKey(new Integer(Constants.ALLELE_STATUS));
        aState.setESCellLineKey(new Integer(parental.getKey()));
        aState.setMutantESCellLineKey(new Integer(mutant.getKey()));
        aState.setSymbol(alleleSymbol);
        aState.setName(alleleName);
        aState.setNomenSymbol(null);
        aState.setIsWildType(isWildType);
        aState.setApprovedByKey(rdCfg.getJobStreamKey());
        Timestamp t = new Timestamp(new Date().getTime());
        aState.setApprovalDate(t);

        ALL_AlleleDAO aDAO = new ALL_AlleleDAO(aState);
        stream.insert(aDAO);
        
        // Set this object key to the newly created allele key from the DB
        alleleKey = aDAO.getKey().getKey().intValue();

        // Create the mutation type references in the database
        for (Iterator i = mutationTypes.iterator(); i.hasNext();)
        {
            Integer typeKey = (Integer)i.next();

            ALL_Allele_MutationState amState = new ALL_Allele_MutationState();
            amState.setAlleleKey(new Integer(alleleKey));
            amState.setMutationKey(typeKey);
            
            ALL_Allele_MutationDAO amDAO = new ALL_Allele_MutationDAO(amState);
            stream.insert(amDAO);
        }

        // Create the associations btwn the Allele and the reference
        // Original and Molecular
        for (int i=0; i<Constants.REFERENCE_ASSOC.length; i++)
        {
            MGI_Reference_AssocState raState = new MGI_Reference_AssocState();
            raState.setRefsKey(jnumLookup.lookup(jNumber));
            raState.setObjectKey(new Integer(alleleKey));
            raState.setMGITypeKey(new Integer(Constants.ALLELE_MGI_TYPE));
            raState.setRefAssocTypeKey(new Integer(Constants.REFERENCE_ASSOC[i]));
            
            MGI_Reference_AssocDAO raDAO = new MGI_Reference_AssocDAO(raState);
            stream.insert(raDAO);
        }

        // Save the note to the stream
        this.updateNote(stream, alleleNote);

        // Create the Allele Accession object
        // note the missing AccID parameter which indicates this is an MGI ID
        AccessionId alleleAccId = new AccessionId(
            Constants.LOGICALDB_MGI,    // Logical DB
            alleleKey,      // Allele object key
            Constants.ALLELE_MGI_TYPE,  // MGI type
            Boolean.FALSE,  // Private?
            Boolean.TRUE    // Preferred?
            );
        
        alleleAccId.insert(stream);

        // Create the Project (private) Accession object
        AccessionId projectAccId = new AccessionId(
            projectId,    // Create the private project ID for this allele
            projectLogicalDb, // Logical DB
            alleleKey,    // Allele object key
            Constants.ALLELE_MGI_TYPE,        // MGI type
            Boolean.TRUE,  // Private?
            Boolean.TRUE    // Preferred?
            );
        
        projectAccId.insert(stream);

    }

    /**
     * Clear the attributes of this object.
     * @assumes Nothing
     * @effects Resets the attributes of this object.
     * @return Nothing
     * @throws Nothing
     */
    public void clear ()
    {
        this.strain = null;
        this.gene = null;
        this.parental = null;
        this.mutant = null;
        this.alleleNote = null;

        this.esCellName = null;
        this.provider = null;
        this.cassette = null;
        this.build = null;
        this.projectId = null;

        this.alleleSymbol = null;
        this.alleleName = null;
        this.alleleKey = 0;
    }
}


/**************************************************************************
*
* Warranty Disclaimer and Copyright Notice
*
*  THE JACKSON LABORATORY MAKES NO REPRESENTATION ABOUT THE SUITABILITY OR
*  ACCURACY OF THIS SOFTWARE OR DATA FOR ANY PURPOSE, AND MAKES NO WARRANTIES,
*  EITHER EXPRESS OR IMPLIED, INCLUDING MERCHANTABILITY AND FITNESS FOR A
*  PARTICULAR PURPOSE OR THAT THE USE OF THIS SOFTWARE OR DATA WILL NOT
*  INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS, OR OTHER RIGHTS.
*  THE SOFTWARE AND DATA ARE PROVIDED "AS IS".
*
*  This software and data are provided to enhance knowledge and encourage
*  progress in the scientific community and are to be used only for research
*  and educational purposes.  Any reproduction or use for commercial purpose
*  is prohibited without the prior express written permission of The Jackson
*  Laboratory.
*
* Copyright \251 1996, 1999, 2002, 2004 by The Jackson Laboratory
*
* All Rights Reserved
*
**************************************************************************/
