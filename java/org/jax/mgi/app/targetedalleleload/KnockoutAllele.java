package org.jax.mgi.app.targetedalleleload;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.jax.mgi.app.targetedalleleload.lookups.LookupAlleleByKey;
import org.jax.mgi.app.targetedalleleload.lookups.LookupJNumbersByAlleleKey;
import org.jax.mgi.dbs.mgd.dao.ALL_AlleleDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_AlleleState;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_MutationDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_MutationState;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteChunkDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteChunkState;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_NoteState;
import org.jax.mgi.dbs.mgd.dao.MGI_Reference_AssocDAO;
import org.jax.mgi.dbs.mgd.dao.MGI_Reference_AssocState;
import org.jax.mgi.dbs.mgd.dao.VOC_AnnotDAO;
import org.jax.mgi.dbs.mgd.dao.VOC_AnnotState;
import org.jax.mgi.dbs.mgd.lookup.JNumberLookup;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.RecordStampCfg;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.dao.SQLStream;
import org.jax.mgi.shr.exception.MGIException;

/**
 * A plain old java object for representing an allele record in MGD
 * (specifically used for loading the knockout alleles created by the targeted
 * allele load).
 */

public class KnockoutAllele 
implements Comparable 
{
	private RecordStampCfg rdCfg;
	private JNumberLookup jnumLookup;
	private LookupJNumbersByAlleleKey lookupJNumbersByAlleleKey;
	private LookupAlleleByKey lookupAlleleByKey;
	private Timestamp currentTime = new Timestamp(new Date().getTime());

	// We will need to compare and save these types of objects to the
	// database. Here are the minimum required fields for saving to MGD
	private Integer key = new Integer(0);
	private Integer markerKey = new Integer(0);
	private Integer strainKey = new Integer(0);
	private String symbol;
	private String name;
	private String note;
	private Integer noteKey;
	private Integer noteModifiedByKey;
	private String projectId;

	// From cfg file
	private String[] jNumbers;
	private Integer projectLogicalDb;
	private List mutationTypes = new ArrayList();

	private Integer modeKey = new Integer(Constants.ALLELE_MODE);
	private Integer typeKey;
	private Integer statusKey = new Integer(Constants.ALLELE_STATUS_APPROVED);
	private Integer transmissionKey = new Integer(Constants.ALLELE_TRANSMISSION_CELLLINE);
	private Integer markeralleleStatusKey = new Integer(Constants.MARKER_ALLELE_STATUS);
	private Boolean isWildType = new Boolean(false);
	private Boolean isExtinct = new Boolean(false);
	private Boolean isMixed = new Boolean(false);

	// TR11515 new attributes
	// new allele attribute
	private Integer collectionKey;

	// for VOC_Annot allele subType annotation
        private HashSet subTypeKeySet = new HashSet();
	private Integer annotTypeKey = Constants.SUBTYPE_ANNOT_TYPE_KEY;
	private Integer qualifierKey = Constants.SUBTYPE_QUAL_KEY;

	/**
	 * Constructs a Knockout Allele object
	 * @throws MGIException 
	 * 
	 * @assumes Nothing
	 * @effects Set the class variables.
	 */
	public KnockoutAllele() 
	throws MGIException 
	{
		// To lookup the JNumber Key from the database
		jnumLookup = new JNumberLookup();
		lookupJNumbersByAlleleKey = LookupJNumbersByAlleleKey.getInstance();
		
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
        public void setCollection(Integer key) {
                this.collectionKey = key;
        }
        public Integer getCollection() {
                return this.collectionKey;
        }

	public void setTransmissionKey(Integer key) {
		this.transmissionKey = key;
	}

	public Integer getTransmissionKey() {
		return this.transmissionKey;
	}

	public void setMarkeralleleStatusKey(Integer key) {
		this.markeralleleStatusKey = key;
	}

	public Integer getMarkeralleleStatusKey() {
		return this.markeralleleStatusKey;
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

        public void addSubTypeKey(Integer key) {
                this.subTypeKeySet.add(key);
        }

        public HashSet getSubTypeKeySet() {
                return this.subTypeKeySet;
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

	public List getMutationTypes() {
		return mutationTypes;
	}

	public void setMutationTypes(List mutationTypes) {
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
		String jnumString = "";
		if (this.jNumbers != null) {
			jnumString = this.jNumbers.toString();
		}
		return "Allele key: " + this.key + "\n" + "name: " + this.name + "\n"
				+ "symbol: " + this.symbol + "\n" + "note: " + this.note + "\n"
				+ "J Numbers: " + jnumString + "\n";
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
	public int compareTo(Object that) 
	throws ClassCastException 
	{
		if (!(that instanceof KnockoutAllele)) {
			throw new ClassCastException("A KnockoutAllele object expected.");
		}

		String thatSymbol = ((KnockoutAllele) that).getSymbol();
		return this.getSymbol().compareTo(thatSymbol);
	}

	/**
	 * This method returns the difference between jnumbers indicated
	 * in the configuration that should be associated to this allele
	 * and actual jnumber associations
	 * 
	 * to this allele already, and  
	 * @return set of strings indicating which jnumbers
	 * 			need to be associated to this allele but aren't 
	 * @throws ClassCastException
	 * @throws MGIException
	 */
	public Set getJNumSetDifference() 
	throws MGIException 
	{
		TargetedAlleleLoadCfg cfg = new TargetedAlleleLoadCfg();
		
		Set difference = new HashSet(Arrays.asList(cfg.getJNumbers()));
		Set theseJNums = new HashSet(Arrays.asList(this.getJNumbers()));
		difference.removeAll(theseJNums);
		return difference;
	}
	
	/**
	 * This method enforce that all the references indicated
	 * in the CFG are associated to this allele
	 * @param stream SQL stream to use to create the missing associations
	 * @throws MGIException
	 */
	public void normalizeReferences(SQLStream stream) 
	throws MGIException
	{
		Set jnumbers = getJNumSetDifference();
		if (jnumbers.size() != 0)
		{
			for (Iterator it = jnumbers.iterator(); it.hasNext();) {
				String jNumber = (String) it.next();
				createReference(stream, Constants.MOLECULAR_REFERENCE, jNumber);
				
				// Add the new reference association to the lookup 
				// to make sure the it's only recorded the first time 
				List update = new ArrayList(Arrays.asList(this.jNumbers));
				update.add(jNumber);
				this.setJNumbers((String [])update.toArray(new String [0]));

				lookupJNumbersByAlleleKey.addToCache(
					key, 
					this.getJNumbers()
					);
			}
			
			// Do NOT initialize this in the constructor or it leads to
			// a circular initialization death spiral.
			if (lookupAlleleByKey == null)
			{
				lookupAlleleByKey = LookupAlleleByKey.getInstance();				
			}
			lookupAlleleByKey.addToCache(key, this);			
		}
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
		nState.setNotetypeKey(new Integer(Constants.NOTE_TYPE_MOLECULAR));

		MGI_NoteDAO nDAO = new MGI_NoteDAO(nState);
		stream.insert(nDAO);

		// get note key for the newly created note 
		noteKey = new Integer(nDAO.getKey().getKey().intValue());

		// Create the note chunk
		MGI_NoteChunkState ncState = new MGI_NoteChunkState();
		ncState.setNoteKey(noteKey);
		ncState.setSequenceNum(new Integer(1));
		ncState.setNote(newNote);
		MGI_NoteChunkDAO ncDAO = new MGI_NoteChunkDAO(ncState);
		stream.insert(ncDAO);

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
		aState.setCollectionKey(collectionKey);
		aState.setTransmissionKey(transmissionKey);
		aState.setMarkeralleleStatusKey(markeralleleStatusKey);
		aState.setSymbol(symbol);
		aState.setName(name);
		aState.setIsWildType(isWildType);
		aState.setIsExtinct(isExtinct);
		aState.setIsMixed(isMixed);
		aState.setApprovedbyKey(rdCfg.getJobStreamKey());
		aState.setApprovalDate(currentTime);

		ALL_AlleleDAO aDAO = new ALL_AlleleDAO(aState);
		stream.insert(aDAO);

		// Set this object key to the newly created allele key from the DB
		key = new Integer(aDAO.getKey().getKey().intValue());

		// create the subtype annotations
		for (Iterator i = subTypeKeySet.iterator(); i.hasNext();) {
		    Integer subTypeKey = (Integer) i.next();
		    VOC_AnnotState annotState = new VOC_AnnotState();
		    annotState.setObjectKey(key);
		    annotState.setAnnottypeKey(annotTypeKey);
		    annotState.setQualifierKey(qualifierKey);
		    annotState.setTermKey(subTypeKey);

		    VOC_AnnotDAO annotDAO = new VOC_AnnotDAO(annotState);
		    stream.insert(annotDAO);
		}

		// Create the mutation type references
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
