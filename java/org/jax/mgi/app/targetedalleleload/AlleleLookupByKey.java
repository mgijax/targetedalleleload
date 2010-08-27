package org.jax.mgi.app.targetedalleleload;

import java.util.Iterator;
import java.util.Vector;
import java.lang.StringBuilder;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.MultiRowInterpreter;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.exception.MGIException;

/**
 * 
 * is a FullCachedLookup storing knockout allele mutant es cell line name
 * associations to knockout allele objects
 * 
 * @has internal cache of knockout alleles
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class AlleleLookupByKey extends FullCachedLookup {

	private static AlleleLookupByKey _instance;

	public static AlleleLookupByKey getInstance() throws MGIException {
		if (_instance == null) {
			_instance = new AlleleLookupByKey();
		}
		return _instance;
	}

	private MarkerLookupByMGIID markerLookup;

	/**
	 * constructor
	 * 
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 */
	public AlleleLookupByKey() throws MGIException {
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));

		markerLookup = new MarkerLookupByMGIID();
		markerLookup.initCache();
	}

	/**
	 * look up an associated KnockoutAllele by a given name
	 * 
	 * @param alleleKey
	 *            the KnockoutAllele key
	 * @return the associated KnockoutAllele
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public KnockoutAllele lookup(Integer alleleKey) throws DBException,
			CacheException {
		return (KnockoutAllele) lookupNullsOk(alleleKey);
	}

	/**
	 * add a new allele to the cache
	 * 
	 * @assumes nothing
	 * @effects the value identified by 'projectId' will be added or replaced
	 * @param alleleKey
	 *            the key of an existing allele
	 * @param koAllele
	 *            a knockout allele
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	protected void addToCache(Integer alleleKey, KnockoutAllele koAllele)
			throws DBException, CacheException {
		// Replace the current value if it exists
		cache.put(alleleKey, koAllele);
	}

	/**
	 * get the query for fully initializing the cache mouse KnockoutAlleles by
	 * name
	 * 
	 * @return the initialization query
	 */
	public String getFullInitQuery() {
		String provider = null;
		try {
			TargetedAlleleLoadCfg cfg = new TargetedAlleleLoadCfg();
			provider = cfg.getProvider();
		} catch (MGIException e) {
			System.out.println("Config exception retrieving JNUMBER");
			return null;
		}

		return "SELECT DISTINCT alleleKey=a._Allele_key, alleleName=a.name, "
				+ "alleleSymbol=a.symbol, alleleType=a._Allele_Type_key, "
				+ "geneSymbol=mrk.symbol, chr=mrk.chromosome, "
				+ "geneKey=mrk._Marker_key, geneMgiid=acc.accID, "
				+ "alleleNote=nc.note, "
				+ "alleleNoteSeq=nc.sequenceNum, alleleNoteKey=nc._note_key, "
				+ "alleleNoteModifiedBy=n._ModifiedBy_key, "
				+ "alleleNoteCreatedBy=n._CreatedBy_key, "
				+ "jNumber=bc.jnumID, projectId=acc2.accId  "
				+ "FROM ALL_Allele a, BIB_Citation_Cache bc, "
				+ "MGI_Reference_Assoc ra,MGI_RefAssocType rat, "
				+ "MRK_Marker mrk, "
				+ "MGI_Note n, MGI_NoteChunk nc, ACC_Accession acc, "
				+ "ACC_Accession acc2 " + "WHERE a.symbol LIKE '%<tm%"
				+ provider
				+ ">' AND ra._Refs_key = bc._Refs_key "
				+ "AND ra._Object_key = a._Allele_key "
				+ "AND ra._RefAssocType_key = rat._RefAssocType_key "
				+ "AND ra._MGIType_key = rat._MGIType_key "
				+ "AND rat.assocType = 'Original' "
				+ "AND a._Marker_key = mrk._Marker_key "
				+ "AND acc.preferred=1 "
				+ "AND acc._Object_key = mrk._Marker_key "
				+ "AND acc.prefixpart='MGI:' "
				+ "AND acc._LogicalDB_key=1 "
				+ "AND acc._MGIType_key=2 "
				+ "AND acc2.preferred=1 "
				+ "AND acc2.private=1 "
				+ "AND acc2._Object_key = a._Allele_key "
				+ "AND acc2._LogicalDB_key in (125,126,138) "
				+ "AND acc2._MGIType_key=11 "
				+ "AND n._Object_key = a._Allele_key "
				+ "AND n._MGIType_key = 11 "
				+ "AND n._NoteType_key = 1021 "
				+ "AND n._Note_key = nc._Note_key "
				+ "ORDER BY alleleKey, alleleNoteSeq ";
	}

	/**
	 * return the RowDataInterpreter for creating KeyValue objects from the
	 * query results
	 * 
	 * @return the RowDataInterpreter for this query
	 */
	public RowDataInterpreter getRowDataInterpreter() {
		class Interpreter implements MultiRowInterpreter {
			public Object interpret(RowReference ref) throws DBException {
				return new RowData(ref);
			}

			public Object interpretKey(RowReference row) throws DBException {
				return row.getString("alleleKey");
			}

			public Object interpretRows(Vector v) {
				// All rows return the same values for every columns EXCEPT
				// for the alleleNote column (one row per note chunk)
				RowData rd = (RowData) v.get(0);

				StringBuilder note = new StringBuilder();
				DLALogger logger;
				KnockoutAllele koAllele;

				try {
					logger = DLALogger.getInstance();
				} catch (DLALoggingException e) {
					// logger.logdInfo(e.getMessage(), true);
					return null;
				}

				try {
					koAllele = new KnockoutAllele();
				} catch (MGIException e) {
					logger.logdInfo(e.getMessage(), true);
					return null;
				}

				koAllele.setName(rd.alleleName);
				koAllele.setSymbol(rd.alleleSymbol);
				koAllele.setKey(rd.alleleKey);
				koAllele.setProjectId(rd.projectId);
				koAllele.setNote(rd.alleleNote);
				koAllele.setNoteKey(rd.alleleNoteKey);
				koAllele.setNoteModifiedByKey(rd.alleleNoteModifiedBy);
				koAllele.setJNumber(rd.jNumber);

				for (Iterator it = v.iterator(); it.hasNext();) {
					rd = (RowData) it.next();

					// Concat all the notechunks together in the allele note
					note.append(rd.alleleNote.trim());
				}

				koAllele.setNote(note.toString().trim());

				try {
					koAllele.setMarkerKey(markerLookup.lookup(rd.geneMgiid)
							.getKey());
				} catch (MGIException e) {
					logger.logdInfo(e.getMessage(), true);
					return null;
				}

				return new KeyValue(rd.alleleKey, koAllele);
			}
		}

		return new Interpreter();
	}

	/**
	 * Simple data object representing a row of data from the query
	 */
	class RowData {
		protected Integer alleleKey;
		protected String projectId;
		protected Integer alleleType;
		protected String alleleName;
		protected String alleleSymbol;
		protected String alleleNote;
		protected Integer alleleNoteKey;
		protected Integer alleleNoteCreatedBy;
		protected Integer alleleNoteModifiedBy;
		protected String jNumber;
		protected String geneMgiid;

		public String toString() {
			return "alleleKey:" + alleleKey + "\nprojectId:" + projectId
					+ "\nalleleType:" + alleleType + "\njnum:" + jNumber
					+ "\ngenemgi:" + geneMgiid + "\nalleleName:" + alleleName
					+ "\nalleleSymbol:" + alleleSymbol + "\nalleleNote:"
					+ alleleNote + "\nalleleNoteKey:" + alleleNoteKey
					+ "\nalleleNoteCreatedBy:" + alleleNoteCreatedBy
					+ "\nalleleNoteModifiedBy:" + alleleNoteModifiedBy;
		}

		public RowData(RowReference row) throws DBException {
			alleleKey = row.getInt("alleleKey");
			projectId = row.getString("projectId");
			alleleType = row.getInt("alleleType");
			alleleName = row.getString("alleleName");
			alleleSymbol = row.getString("alleleSymbol");
			alleleNote = row.getString("alleleNote");
			alleleNoteKey = row.getInt("alleleNoteKey");
			alleleNoteCreatedBy = row.getInt("alleleNoteCreatedBy");
			alleleNoteModifiedBy = row.getInt("alleleNoteModifiedBy");
			jNumber = row.getString("jNumber");
			geneMgiid = row.getString("geneMgiid");
		}
	}
}
