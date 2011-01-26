package org.jax.mgi.app.targetedalleleload;

import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

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

public class AlleleLookupByCellLine extends FullCachedLookup {

	private MarkerLookupByMGIID markerLookup;
	private TargetedAlleleLoadCfg cfg;
	private DLALogger logger;

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
	public AlleleLookupByCellLine() throws MGIException {
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
		markerLookup = new MarkerLookupByMGIID();
		markerLookup.initCache();

		logger = DLALogger.getInstance();
		cfg = new TargetedAlleleLoadCfg();
	}

	/**
	 * look up an associated KnockoutAllele by a given name
	 * 
	 * @param name
	 *            the KnockoutAllele name
	 * @return the associated KnockoutAllele
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public KnockoutAllele lookup(String name) throws DBException,
			CacheException {
		return (KnockoutAllele) super.lookupNullsOk(name);
	}

	/**
	 * returns the set of keys from the cache
	 * 
	 * @assumes nothing
	 * @effects nothing
	 */
	public Set getKeySet() {
		return cache.keySet();
	}

	/**
	 * add a new map to the cache
	 * 
	 * @assumes nothing
	 * @effects the value identified by 'cellLine' will be added or replaced
	 * @param cellLine
	 *            the cellLine name
	 * @param koAllele
	 *            the knockout allele
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	protected void addToCache(String cellLine, KnockoutAllele koAllele)
			throws DBException, CacheException {
		// Replace the current value if it exists
		super.cache.put(cellLine.toLowerCase(), koAllele);
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
			provider = cfg.getProvider();
		} catch (ConfigException e) {
			System.out.println("Config Exception retrieving JNUMBER");
		}
		return "SELECT alleleKey=a._Allele_key, alleleName=a.name, "
				+ "alleleSymbol=a.symbol, alleleType=a._Allele_Type_key, "
				+ "geneSymbol=mrk.symbol, chr=mrk.chromosome, "
				+ "geneKey=mrk._Marker_key, geneMgiid=acc.accID, "
				+ "alleleNote=nc.note, alleleTrans=a._Transmission_key, "
				+ "alleleNoteSeq=nc.sequenceNum, alleleNoteKey=nc._note_key, "
				+ "alleleNoteModifiedBy=n._ModifiedBy_key, "
				+ "alleleNoteCreatedBy=n._CreatedBy_key, "
				+ "jNumber=bc.jnumID, projectId=acc2.accId, "
				+ "aacv._MutantCellLine_key, aacv.cellLine "
				+ "FROM ALL_Allele a, BIB_Citation_Cache bc, "
				+ "MGI_Reference_Assoc ra,MGI_RefAssocType rat, "
				+ "MRK_Marker mrk, ALL_Allele_CellLine_View aacv, "
				+ "MGI_Note n, MGI_NoteChunk nc, ACC_Accession acc, "
				+ "ACC_Accession acc2 " + "WHERE a.symbol like '%<tm%"
				+ provider
				+ ">' "
				+ "AND aacv._Allele_key = a._Allele_key "
				+ "AND ra._Refs_key = bc._Refs_key "
				+ "AND ra._Object_key = a._Allele_key "
				+ "and ra._RefAssocType_key = rat._RefAssocType_key "
				+ "and ra._MGIType_key = rat._MGIType_key "
				+ "and rat.assocType = 'Original' "
				+ "and a._Marker_key = mrk._Marker_key "
				+ "and acc.preferred=1 "
				+ "and acc._Object_key = mrk._Marker_key "
				+ "and acc.prefixpart='MGI:' "
				+ "and acc._LogicalDB_key=1 "
				+ "and acc._MGIType_key=2 "
				+ "and acc2.preferred=1 "
				+ "and acc2.private=1 "
				+ "and acc2._Object_key = a._Allele_key "
				+ "and acc2._LogicalDB_key in (125,126,138,143) "
				+ "and acc2._MGIType_key=11 "
				+ "and n._Object_key =* a._Allele_key "
				+ "and n._MGIType_key = 11 "
				+ "and n._NoteType_key = 1021 "
				+ "and n._Note_key *= nc._Note_key "
				+ "order by alleleKey, cellLine, alleleNoteSeq ";
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
				return row.getString("cellLine");
			}

			public Object interpretRows(Vector v) {
				// All rows return the same values for every columns EXCEPT
				// for the alleleNote column (one row per note chunk)
				RowData rd = (RowData) v.get(0);

				String completeNote = "";

				KnockoutAllele koAllele = null;

				try {
					koAllele = new KnockoutAllele();
				} catch (MGIException e) {
					logger.logdInfo(e.getMessage(), true);
					return null;
				}

				koAllele.setName(rd.alleleName);
				koAllele.setSymbol(rd.alleleSymbol);
				koAllele.setKey(rd.alleleKey);
				koAllele.setTypeKey(rd.alleleType);
				koAllele.setTransmissionKey(rd.alleleTrans);
				koAllele.setProjectId(rd.projectId);
				koAllele.setNote(rd.alleleNote);
				koAllele.setNoteKey(rd.alleleNoteKey);
				koAllele.setNoteModifiedByKey(rd.alleleNoteModifiedBy);
				koAllele.setJNumber(rd.jNumber);
				koAllele.setCellLineKey(rd.mutantCellLineKey);

				for (Iterator it = v.iterator(); it.hasNext();) {
					rd = (RowData) it.next();

					// Concat all the notechunks together in the allele note
					completeNote += rd.alleleNote;
				}

				koAllele.setNote(completeNote.trim());

				try {
					koAllele.setMarkerKey(markerLookup.lookup(rd.geneMgiid)
							.getKey());
				} catch (MGIException e) {
					logger.logdInfo(e.getMessage(), true);
					return null;
				}

				return new KeyValue(rd.cellLine, koAllele);
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
		protected Integer alleleTrans;
		protected String alleleName;
		protected String alleleSymbol;
		protected String alleleNote;
		protected Integer alleleNoteKey;
		protected Integer alleleNoteCreatedBy;
		protected Integer alleleNoteModifiedBy;
		protected String jNumber;
		protected String geneMgiid;
		protected Integer mutantCellLineKey;
		private String cellLine;

		public RowData(RowReference row) throws DBException {
			alleleKey = row.getInt("alleleKey");
			projectId = row.getString("projectId");
			alleleType = row.getInt("alleleType");
			alleleTrans = row.getInt("alleleTrans");
			alleleName = row.getString("alleleName");
			alleleSymbol = row.getString("alleleSymbol");
			alleleNote = row.getString("alleleNote");
			alleleNoteKey = row.getInt("alleleNoteKey");
			alleleNoteCreatedBy = row.getInt("alleleNoteCreatedBy");
			alleleNoteModifiedBy = row.getInt("alleleNoteModifiedBy");
			jNumber = row.getString("jNumber");
			geneMgiid = row.getString("geneMgiid");
			mutantCellLineKey = row.getInt("_MutantCellLine_key");
			cellLine = row.getString("cellLine");
		}
	}
}
