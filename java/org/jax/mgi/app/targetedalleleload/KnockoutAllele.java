package org.jax.mgi.app.targetedalleleload;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;

import org.jax.mgi.dbs.mgd.dao.ALL_AlleleDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_AlleleState;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_MutationDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_MutationState;
import org.jax.mgi.dbs.mgd.dao.ALL_Marker_AssocDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_Marker_AssocState;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteChunkDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteChunkState;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteState;
import org.jax.mgi.dbs.mgd.dao.MGI_Reference_AssocDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_Reference_AssocState;
import org.jax.mgi.dbs.mgd.lookup.JNumberLookup;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.RecordStampCfg;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.dao.SQLStream;

/**
 * A plain old java object for representing an allele record in MGD
 * (specifically used for loading the knockout alleles created by the targeted
 * allele load).
 */

public class KnockoutAllele implements Comparable {
	private final int NOTECHUNKSIZE = 255;
	private RecordStampCfg rdCfg = null;
	private JNumberLookup jnumLookup = null;
	private Timestamp currentTime = new Timestamp(new Date().getTime());

	// We will need to compare and save these types of objects to the
	// database. Here are the minimum required fields for saving to MGD
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
	private String[] jNumbers = null;
	private Integer projectLogicalDb = null;
	private Vector mutationTypes = new Vector();

	private Integer modeKey = new Integer(Constants.ALLELE_MODE);
	private Integer typeKey = new Integer(Constants.ALLELE_TYPE);
	private Integer statusKey = new Integer(Constants.ALLELE_STATUS_APPROVED);
	private Integer transmissionKey = new Integer(Constants.ALLELE_TRANSMISSION_CELLLINE);
	private Boolean isWildType = new Boolean(false);
	private Boolean isExtinct = new Boolean(false);
	private Boolean isMixed = new Boolean(false);
	private Integer mkrAssocQualKey = new Integer(
			Constants.MKR_ASSOC_QUAL_NS_KEY);
	private Integer mkrAssocStatusKey = new Integer(
			Constants.MKR_ASSOC_STAT_CURATED_KEY);

	/**
	 * Constructs a Knockout Allele object
	 * 
	 * @assumes Nothing
	 * @effects Set the class variables.
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 */
	public KnockoutAllele() throws ConfigException, DBException, CacheException {
		// To lookup the JNumber Key from the database
		jnumLookup = new JNumberLookup();

		// To get the approvedBy user key from the database
		rdCfg = new RecordStampCfg();
	}

	// Getters / Setters
	public void setStatus(Integer key) {
		this.statusKey = key;
	}
	public Integer getStatus() {
		return this.statusKey;
	}

	public void setTransmissionKey(Integer key) {
		this.transmissionKey = key;
	}

	public Integer getTransmissionKey() {
		return this.transmissionKey;
	}

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	public void setTypeKey(Integer key) {
		this.typeKey = key;
	}

	public Integer getTypeKey() {
		return this.typeKey;
	}

	public Integer getCellLineKey() {
		return cellLineKey;
	}

	public void setCellLineKey(Integer key) {
		this.cellLineKey = key;
	}

	public Integer getMarkerKey() {
		return markerKey;
	}

	public void setMarkerKey(Integer key) {
		this.markerKey = key;
	}

	public Integer getStrainKey() {
		return strainKey;
	}

	public void setStrainKey(Integer key) {
		this.strainKey = key;
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

	public String[] getJNumbers() {
		return jNumbers;
	}

	public void setJNumbers(String[] jNumbers) {
		this.jNumbers = jNumbers;
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

	public void setNoteKey(Integer key) {
		this.noteKey = key;
	}

	public Integer getNoteModifiedByKey() {
		return noteModifiedByKey;
	}

	public void setNoteModifiedByKey(Integer noteModifiedByKey) {
		this.noteModifiedByKey = noteModifiedByKey;
	}

	// @Override
	public String toString() {
		return "Allele key: " + this.key + "\n" + "name: " + this.name + "\n"
				+ "symbol: " + this.symbol + "\n" + "note: " + this.note + "\n"
				+ "J Numbers: " + this.jNumbers.toString() + "\n";
	}

	// @Override
	public boolean equals(Object that) throws ClassCastException {
		if (!(that instanceof KnockoutAllele)) {
			throw new ClassCastException("A KnockoutAllele object expected.");
		}

		String thatSymbol = ((KnockoutAllele) that).getSymbol();
		return this.getSymbol().equals(thatSymbol);
	}

	// @Override
	public int compareTo(Object that) throws ClassCastException {
		if (!(that instanceof KnockoutAllele)) {
			throw new ClassCastException("A KnockoutAllele object expected.");
		}

		String thatSymbol = ((KnockoutAllele) that).getSymbol();
		return this.getSymbol().compareTo(thatSymbol);
	}

	/**
	 * Insert this molecular note object into the stream to create the required
	 * bcp records.
	 * 
	 * @assumes There is no existing molecular note (either deleted, or never
	 *          created
	 * @effects Nothing
	 * @param stream
	 *            The bcp stream to write the bcp records to.
	 * @return Nothing
	 * @throws ConfigException
	 *             if it can't lookup the JobStreamKey or
	 * @throws DBException
	 */
	public void updateNote(SQLStream stream, String newNote)
			throws ConfigException, DBException, CacheException {
		// Save this note as the allele note
		this.setNote(newNote);

		// Create the Note and attach it to this Allele
		MGI_NoteState nState = new MGI_NoteState();
		nState.setObjectKey(key);
		nState.setMGITypeKey(new Integer(Constants.ALLELE_MGI_TYPE));
		nState.setNoteTypeKey(new Integer(Constants.NOTE_TYPE_MOLECULAR));

		MGI_NoteDAO nDAO = new MGI_NoteDAO(nState);
		stream.insert(nDAO);

		// Set this note key to the newly created note key from the DB
		noteKey = new Integer(nDAO.getKey().getKey().intValue());

		// Insert the note chunks for this allele note
		for (int i = 0; i < newNote.length(); i += NOTECHUNKSIZE) {
			// Create the NoteChunk
			MGI_NoteChunkState ncState = new MGI_NoteChunkState();
			ncState.setNoteKey(noteKey);

			// Calculate THIS note chunk sequence number based on how many
			// times we've gone through the loop already
			ncState.setSequenceNum(new Integer((i / NOTECHUNKSIZE) + 1));

			// Set the note chunk to the current 255 characters if that will
			// not overflow the note, otherwise, just add whatever's left
			// to the notechunk.
			String thisNoteChunk = "";
			if (i + NOTECHUNKSIZE < newNote.length()) {
				thisNoteChunk = newNote.substring(i, i + NOTECHUNKSIZE);
			} else {
				thisNoteChunk = newNote.substring(i, note.length());
			}
			ncState.setNote(thisNoteChunk);

			MGI_NoteChunkDAO ncDAO = new MGI_NoteChunkDAO(ncState);
			stream.insert(ncDAO);
		}
	}

	/**
	 * Insert this Knockout Allele object into the stream to create the required
	 * bcp records.
	 * 
	 * @assumes Nothing
	 * @effects Nothing
	 * @param stream
	 *            The bcp stream to write the bcp records to.
	 * @return Nothing
	 * @throws ConfigException
	 *             if it can't lookup the JobStreamKey or
	 * @throws DBException
	 */
	public void insert(SQLStream stream) throws ConfigException, DBException,
			CacheException {
		// Verify that the logical DB has been set
		if (projectLogicalDb.equals(new Integer(0))) {
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
		for (Iterator i = mutationTypes.iterator(); i.hasNext();) {
			Integer typeKey = (Integer) i.next();

			ALL_Allele_MutationState amState = new ALL_Allele_MutationState();
			amState.setAlleleKey(key);
			amState.setMutationKey(typeKey);

			ALL_Allele_MutationDAO amDAO = new ALL_Allele_MutationDAO(amState);
			stream.insert(amDAO);
		}

		// Create the associations between the Allele and the reference
		// Original and Molecular

		/*
		 * Create Original and Molecular reference associations to the 
		 * first J-Number in the list
		 */
		for (int i=0; i<jNumbers.length; i++) {
			
			String jNumber = jNumbers[i];

			if (i==0) {
				// Add Original reference for the first jNumber
				createReference(stream, Constants.ORIGINAL_REFERENCE, jNumber);
			}
			
			// Create Molecular references for all j numbers in the list
			createReference(stream, Constants.MOLECULAR_REFERENCE, jNumber);
			
		}
//		int[] REFERENCE_ASSOC = { 1011, 1012 };
//		for (int i = 0; i < REFERENCE_ASSOC.length; i++) {
//			MGI_Reference_AssocState raState = new MGI_Reference_AssocState();
//			raState.setRefsKey(jnumLookup.lookup(jNumber));
//			raState.setObjectKey(key);
//			raState.setMGITypeKey(new Integer(Constants.ALLELE_MGI_TYPE));
//			raState.setRefAssocTypeKey(new Integer(Constants.REFERENCE_ASSOC[i]));
//
//			MGI_Reference_AssocDAO raDAO = new MGI_Reference_AssocDAO(raState);
//			stream.insert(raDAO);
//		}

		// Save the note to the stream
		this.updateNote(stream, note);

		// Create the Allele Accession object
		// note the missing AccID parameter which indicates this is an MGI ID
		AccessionId alleleAccId = new AccessionId(new Integer(
				Constants.LOGICALDB_MGI), // Logical DB
				key, // Allele object key
				new Integer(Constants.ALLELE_MGI_TYPE), // MGI type
				Boolean.FALSE, // Private?
				Boolean.TRUE // Preferred?
		);

		alleleAccId.insert(stream);

		// Create the Project (private) Accession object
		AccessionId projectAccId = new AccessionId(projectId,
				projectLogicalDb, // Logical DB for these project IDs
				key, // Allele object key
				new Integer(Constants.ALLELE_MGI_TYPE), // MGI type
				Boolean.TRUE, // Private?
				Boolean.TRUE // Preferred?
		);

		projectAccId.insert(stream);

	}

	/**
	 * @param stream
	 * @param type type of reference
	 * @param jNumber the reference identifier
	 * @throws DBException
	 * @throws CacheException
	 * @throws ConfigException
	 */
	private void createReference(SQLStream stream, int type, String jNumber)
			throws DBException, CacheException, ConfigException {
		MGI_Reference_AssocState raState = new MGI_Reference_AssocState();
		raState.setRefsKey(jnumLookup.lookup(jNumber));
		raState.setObjectKey(key);
		raState.setMGITypeKey(new Integer(Constants.ALLELE_MGI_TYPE));
		raState.setRefAssocTypeKey(new Integer(type));

		MGI_Reference_AssocDAO raDAO = new MGI_Reference_AssocDAO(raState);
		stream.insert(raDAO);
	}

}
