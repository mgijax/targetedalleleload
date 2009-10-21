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
import org.jax.mgi.dbs.mgd.dao.ALL_Marker_AssocState;
import org.jax.mgi.dbs.mgd.dao.ALL_Marker_AssocDAO;
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
 * @is An object that represents a Knockout Allele record in MGD
 * @has
 *   <UL>
 *   <LI> Configuration parameters that are needed to compare and 
     populate the allele object
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Provides methods for setting all its attributes.
 *   <LI> Provides a method for inserting an allele record into a BCP stream
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
    private Timestamp currentTime = new Timestamp(new Date().getTime());
    
    // We will need to compare and save these types of objects to the
    // database.  Here are the minimum required fields for saving to MGD
    private Integer key = new Integer(0);
    private Integer markerKey = new Integer(0);
    private Integer strainKey = new Integer(0);
    private String symbol = null;
    private String name = null;
    private String note = null;
    private Integer noteKey = null;
    private Integer noteModifiedByKey = null;
    private String projectId = null;
    private Integer cellLineKey = null;

    // From cfg file
    private String jNumber = null;
    private Integer projectLogicalDb = null;
    private Vector mutationTypes = new Vector();

    // Default values for all KO Alleles
    private String nomenSymbol = null;
    private Integer modeKey = new Integer(Constants.ALLELE_MODE);
    private Integer typeKey = new Integer(Constants.ALLELE_TYPE);
    private Integer statusKey = new Integer(Constants.ALLELE_STATUS);
    private Integer transmissionKey = new Integer(Constants.TRANSMISSION_KEY);
    private Boolean isWildType = new Boolean(false);
    private Boolean isExtinct = new Boolean(false);
    private Boolean isMixed = new Boolean(false);
    private Integer mkrAssocQualKey = new Integer(Constants.MKR_ASSOC_QUAL_NS_KEY);
    private Integer mkrAssocStatusKey = new Integer(Constants.MKR_ASSOC_STAT_CURATED_KEY);



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

    // Getters / Setters
    public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

    public void setTypeKey(Integer typeKey)
    {
        this.typeKey = typeKey;
    }
    
    public Integer getTypeKey()
    {
        return this.typeKey;
    }
    
    public Integer getCellLineKey() {
		return cellLineKey;
	}

	public void setCellLineKey(Integer cellLineKey) {
		this.cellLineKey = cellLineKey;
	}
	public Integer getMarkerKey() {
		return markerKey;
	}

	public void setMarkerKey(Integer markerKey) {
		this.markerKey = markerKey;
	}

	public Integer getStrainKey() {
		return strainKey;
	}

	public void setStrainKey(Integer strainKey) {
		this.strainKey = strainKey;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getJNumber() {
		return jNumber;
	}

	public void setJNumber(String jNumber) {
		this.jNumber = jNumber;
	}

	public Vector getMutationTypes() {
		return mutationTypes;
	}

	public void setMutationTypes(Vector mutationTypes) {
		this.mutationTypes = mutationTypes;
	}

	public void setProjectLogicalDb(Integer projectLogicalDb) {
		this.projectLogicalDb = projectLogicalDb;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Integer getNoteKey() {
	    return noteKey;
	}
	
	public void setNoteKey(Integer noteKey) {
	    this.noteKey = noteKey;
	}

	public Integer getNoteModifiedByKey() {
	    return noteModifiedByKey;
	}

	public void setNoteModifiedByKey(Integer noteModifiedByKey) {
	    this.noteModifiedByKey = noteModifiedByKey;
	}




    // Builting overrides for toString and compareTo
    public String toString()
    {
        return "Allele key: " + this.key+"\n"+
            "name: " + this.name+"\n"+
            "symbol: " + this.symbol+"\n"+
            "note: " + this.note+"\n"+
            "J Number: "+this.jNumber+"\n";
    }

    public int compareTo(Object that) throws ClassCastException
    {
        if (!(that instanceof KnockoutAllele))
        {
            throw new ClassCastException("A KnockoutAllele object expected.");
        }
        
        String thatSymbol = ((KnockoutAllele) that).getSymbol();
//        String thatNote = ((KnockoutAllele) that).getNote();
        return this.getSymbol().compareTo(thatSymbol);
    }



    /**
     * Insert this molucular note object into the stream to create the 
     * required bcp records.
     * @assumes There is no existing molecular note (either deleted, or
     *          never created
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
        this.setNote(newNote);

        // Create the Note and attach it to this Allele
        MGI_NoteState nState =  new MGI_NoteState();
        nState.setObjectKey(key);
        nState.setMGITypeKey(new Integer(Constants.ALLELE_MGI_TYPE));
        nState.setNoteTypeKey(new Integer(Constants.NOTE_TYPE));
        
        MGI_NoteDAO nDAO = new MGI_NoteDAO(nState);
        stream.insert(nDAO);
        
        // Set this note key to the newly created note key from the DB
        noteKey = new Integer(nDAO.getKey().getKey().intValue());

        // Insert the note chunks for this allele note
        for (int i=0; i<newNote.length(); i+=NOTECHUNKSIZE)
        {
            // Create the NoteChunk
            MGI_NoteChunkState ncState = new MGI_NoteChunkState();
            ncState.setNoteKey(noteKey);

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
                thisNoteChunk = newNote.substring(i, note.length());
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
        if (projectLogicalDb.equals(new Integer(0)))
        {
            throw new ConfigException("Project Logical DB not configured", true);
        }

        // Create this allele in the database, attaching the (possibly new)
        // mutant es cell to the allele
        ALL_AlleleState aState = new ALL_AlleleState();
        aState.setMarkerKey(markerKey);
        aState.setStrainKey(strainKey);
        aState.setModeKey(modeKey);
        aState.setAlleleTypeKey(typeKey);
        aState.setAlleleStatusKey(statusKey);
        aState.setTransmissionKey(transmissionKey);
        aState.setSymbol(symbol);
        aState.setName(name);
        aState.setNomenSymbol(null);
        aState.setIsWildType(isWildType);
        aState.setIsExtinct(isExtinct);
        aState.setIsMixed(isMixed);
        aState.setApprovedByKey(rdCfg.getJobStreamKey());
        aState.setApprovalDate(currentTime);

        ALL_AlleleDAO aDAO = new ALL_AlleleDAO(aState);
        stream.insert(aDAO);
        
        // Set this object key to the newly created allele key from the DB
        key = new Integer(aDAO.getKey().getKey().intValue());

        // Create the marker associations
        ALL_Marker_AssocState amaState = new ALL_Marker_AssocState();
        amaState.setAlleleKey(key);
        amaState.setMarkerKey(markerKey);
        amaState.setQualifierKey(mkrAssocQualKey);
        amaState.setStatusKey(mkrAssocStatusKey);

        ALL_Marker_AssocDAO amaDAO = new ALL_Marker_AssocDAO(amaState);
         stream.insert(amaDAO);
        // Create the mutation type references in the database
        for (Iterator i = mutationTypes.iterator(); i.hasNext();)
        {
            Integer typeKey = (Integer)i.next();

            ALL_Allele_MutationState amState = new ALL_Allele_MutationState();
            amState.setAlleleKey(key);
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
            raState.setObjectKey(key);
            raState.setMGITypeKey(new Integer(Constants.ALLELE_MGI_TYPE));
            raState.setRefAssocTypeKey(new Integer(Constants.REFERENCE_ASSOC[i]));
            
            MGI_Reference_AssocDAO raDAO = new MGI_Reference_AssocDAO(raState);
            stream.insert(raDAO);
        }

        // Save the note to the stream
        this.updateNote(stream, note);

        // Create the Allele Accession object
        // note the missing AccID parameter which indicates this is an MGI ID
        AccessionId alleleAccId = new AccessionId(
            new Integer(Constants.LOGICALDB_MGI),    // Logical DB
            key,      // Allele object key
            new Integer(Constants.ALLELE_MGI_TYPE),  // MGI type
            Boolean.FALSE,  // Private?
            Boolean.TRUE    // Preferred?
            );
        
        alleleAccId.insert(stream);

        // Create the Project (private) Accession object
        AccessionId projectAccId = new AccessionId(
            projectId,    // Create the private project ID for this allele
            projectLogicalDb, // Logical DB for these tpyes of project IDs
            key,    // Allele object key
            new Integer(Constants.ALLELE_MGI_TYPE),        // MGI type
            Boolean.TRUE,  // Private?
            Boolean.TRUE    // Preferred?
            );
        
        projectAccId.insert(stream);

    }

}
