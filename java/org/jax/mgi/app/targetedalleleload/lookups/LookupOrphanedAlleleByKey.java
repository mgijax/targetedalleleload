	package org.jax.mgi.app.targetedalleleload.lookups;

import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.jax.mgi.app.targetedalleleload.KnockoutAllele;
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

public class LookupOrphanedAlleleByKey 
extends FullCachedLookup 
{

	private static LookupOrphanedAlleleByKey _instance;

	private static LookupJNumbersByAlleleKey lookupJNumbersByAlleleKey;
	private static LookupMarkerByMGIID lookupMarkerByMGIID;
	private static DLALogger logger;

    public static LookupOrphanedAlleleByKey getInstance() 
    throws MGIException 
    {
            logger = DLALogger.getInstance();
            if (_instance == null) {
                    _instance = new LookupOrphanedAlleleByKey();
            }
            return _instance;
    }

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
	private LookupOrphanedAlleleByKey() 
	throws MGIException 
	{
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));

		lookupMarkerByMGIID = LookupMarkerByMGIID.getInstance();
		lookupJNumbersByAlleleKey = LookupJNumbersByAlleleKey.getInstance();

		logger = DLALogger.getInstance();
		this.initCache();
	}

	/**
	 * look up an associated KnockoutAllele by a given name
	 * 
	 * @param key the KnockoutAllele key to lookup
	 * @return the associated KnockoutAllele
	 * @throws DBException thrown if there is an error accessing the database
	 * @throws CacheException thrown if there is an error accessing the configuration
	 */
	public KnockoutAllele lookup(Integer key) 
	throws DBException, CacheException 
	{
		return (KnockoutAllele) super.lookupNullsOk(key);
	}


	/**
	 * add a new map to the cache
	 * 
	 * @assumes nothing
	 * @effects the value identified by 'cellLine' will be added 
	 * 			or replaced
	 * @param key the cellLine key to lookup
	 * @param koAllele the knockout allele
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	protected void addToCache(Integer key, KnockoutAllele koAllele)
	throws DBException, CacheException 
	{
		// Replace the current value if it exists
		super.cache.put(key, koAllele);
	}

	/**
	 * returns the set of keys from the cache
	 * 
	 * @assumes nothing
	 * @return a Set of keys for this cache
	 */
	public Set getKeySet() 
	{
		return cache.keySet();
	}

	/**
	 * the lookup initialization query is constructed to fulfill 
	 * the requirement that the "tm" (targeted mutation) allele 
	 * number be incremented with respect to all "tm" alleles that 
	 * already existing MGD from this PROVIDER
	 * 
	 * @return the initialization query
	 */
	public String getFullInitQuery() 
	{
		TargetedAlleleLoadCfg cfg;
		String provider;
		try {
			cfg = new TargetedAlleleLoadCfg();
			provider = cfg.getProviderLabcode();
		} catch (MGIException e) {
			// Cannot get load provider lab code.  Bail!
			return "";
		}
		return "SELECT a._Allele_key as alleleKey, a.name as alleleName, " +
		"a.symbol as alleleSymbol, a._Allele_Type_key as alleleType, " +
		"mrk.symbol as geneSymbol, mrk.chromosome as chr, " +
		"mrk._Marker_key as geneKey, acc.accID as geneMgiid, " +
		"nc.note as alleleNote, a._Transmission_key as alleleTrans, " +
		"nc.sequenceNum as alleleNoteSeq, " +
		"nc._note_key as alleleNoteKey, " +
		"n._ModifiedBy_key as alleleNoteModifiedBy, " +
		"n._CreatedBy_key as alleleNoteCreatedBy, " +
		"acc2.accId as projectId, " +
		"aac._MutantCellLine_key, ac.cellLine " +
		"FROM MRK_Marker mrk, ALL_Allele_CellLine aac, " +
		"ALL_Cellline ac, " +
		"ACC_Accession acc, ACC_Accession acc2, " +
		"ALL_Allele a  " +
                "LEFT OUTER JOIN MGI_Note n on  " +
                "    (a._Allele_key = n._Object_key " +
                "    and n._MGIType_key = 11 " +
                "    and n._NoteType_key = 1021) " +
                "LEFT OUTER JOIN MGI_NoteChunk nc on " +
                "    (nc._note_key = n._note_key) " +
		"WHERE a.symbol like '%tm%" + provider + ">' " +
		"AND ac.cellLine = 'Orphaned' " +
		"AND aac._Allele_key = a._Allele_key " +
		"AND aac._MutantCellLine_key = ac._cellline_key " +
		"AND a._Marker_key = mrk._Marker_key " +
		"AND acc.preferred=1 " +
		"AND acc._Object_key = mrk._Marker_key " +
		"AND acc.prefixpart='MGI:' " +
		"AND acc._LogicalDB_key=1 " +
		"AND acc._MGIType_key=2 " +
		"AND acc2.preferred=1 " +
		"AND acc2.private=1 " +
		"AND acc2._Object_key = a._Allele_key " +
		"AND acc2._LogicalDB_key in (125,126,138,143,166) " +
		"AND acc2._MGIType_key=11 " +
		"ORDER BY alleleKey, cellLine, alleleNoteSeq " ;
	}

	/**
	 * return the RowDataInterpreter for creating KeyValue objects from the
	 * query results
	 * 
	 * @return the RowDataInterpreter for this query
	 */
	public RowDataInterpreter getRowDataInterpreter() 
	{
		class Interpreter 
		implements MultiRowInterpreter 
		{
			public Object interpret(RowReference ref) 
			throws DBException 
			{
				return new RowData(ref);
			}

			public Object interpretKey(RowReference row) 
			throws DBException 
			{
				return row.getString("alleleKey");
			}

			public Object interpretRows(Vector v) 
			{
				// All rows return the same values for every columns 
				// EXCEPT for the alleleNote column 
				// (one row per note chunk)
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

				// Lookup the jnumber in the database, if we can't find
				// any there, this allele is BAD, report the exception
				// and skip
				try {
					koAllele.setJNumbers(lookupJNumbersByAlleleKey
							.lookup(rd.alleleKey));
				} catch (MGIException e) {
					logger.logdInfo(e.getMessage(), true);
					return null;
				}

				for (Iterator it = v.iterator(); it.hasNext();) {
					rd = (RowData) it.next();

					// combine all the note chunks together in the 
					// allele note
					completeNote += rd.alleleNote;
				}

				koAllele.setNote(completeNote.trim());

				try {
					koAllele.setMarkerKey(lookupMarkerByMGIID.lookup(
							rd.geneMgiid).getKey());
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
	class RowData 
	{
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
		protected String geneMgiid;
		protected Integer mutantCellLineKey;

		public RowData(RowReference row) 
		throws DBException 
		{
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
			geneMgiid = row.getString("geneMgiid");
			mutantCellLineKey = row.getInt("_MutantCellLine_key");
		}
	}
}
