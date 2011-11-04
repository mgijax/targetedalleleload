package org.jax.mgi.app.targetedalleleload.lookups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyValue;
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
 * is a FullCachedLookup storing alleles to project id
 * 
 * @has internal cache of set objects indexed by project id
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class LookupAllelesByProjectId extends FullCachedLookup {

	private TargetedAlleleLoadCfg cfg;
	private Integer logicalDb;
	private static DLALogger logger;

	private static LookupAllelesByProjectId _instance;

	/**
	 * If the lookup has not been initialized, initialize it and return,
	 * otherwise just return the previously initialized instance.
	 * 
	 * @return an instance of AlleleLookupByProjectId
	 * @throws MGIException
	 */
	public static LookupAllelesByProjectId getInstance() throws MGIException {
		logger = DLALogger.getInstance();
		if (_instance == null) {
			_instance = new LookupAllelesByProjectId();
		}
		return _instance;
	}

	/**
	 * This implements the singleton pattern, so the constructor is private and
	 * can only be called by the class method "getInstance" Only one instance of
	 * AlleleLookupByProjectId will ever be created in memory
	 * 
	 * @throws MGIException
	 */
	private LookupAllelesByProjectId() throws MGIException {
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));

		try {
			cfg = new TargetedAlleleLoadCfg();
		} catch (DLALoggingException e) {
			String msg = "AlleleLookupByProjectId DLALoggingException";
			throw new MGIException(msg);
		}

		this.logicalDb = cfg.getProjectLogicalDb();
		initCache(cache);
	}

	/**
	 * look up associated ES cell lines by production center project ID
	 * 
	 * @param projectID
	 *            the project ID
	 * @return the associated set of ES cell line names
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public Map lookup(String projectID) throws DBException, CacheException {
		return (Map) lookupNullsOk(projectID);
	}

	/**
	 * get the query for fully initializing the cache ES cell lines by
	 * production center project ID
	 * 
	 * @return the initialization query
	 */
	public String getFullInitQuery() {
		return "SELECT acc.accID 'projectid', a._Allele_key 'allelekey', "
				+ "c.cellLine 'mutantCellLine', aa.symbol 'symbol', "
				+ "p._CellLine_key 'parentCellLine_key', "
				+ "p.cellLine 'parentCellLine' FROM ACC_Accession acc, "
				+ "ALL_Allele_CellLine a, ALL_CellLine c, "
				+ "ALL_Allele aa, ALL_CellLine p, "
				+ "ALL_CellLine_Derivation d WHERE acc._logicaldb_key = "
				+ logicalDb + " AND acc._object_key = a._Allele_key "
				+ "AND a._MutantCellLine_key = c._CellLine_key "
				+ "AND a._Allele_key = aa._Allele_key "
				+ "AND d._ParentCellLine_key = p._CellLine_key "
				+ "AND c._Derivation_key = d._Derivation_key "
				+ "ORDER BY projectid, parentCellLine ";
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
	 * @effects the value identified by 'projectId' will be added or replaced
	 * @param projectId
	 *            the project ID
	 * @param alleleMap
	 *            map of all allele symbol to details for this project
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	public void addToCache(String projectId, Map alleleMap)
			throws DBException, CacheException {
		// Replace the current value if it exists
		cache.put(projectId.toLowerCase(), alleleMap);
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
				String key = row.getString("projectid") + ","
						+ row.getString("symbol") + ","
						+ row.getString("parentCellLine");
				return key;
			}

			public Object interpretRows(Vector v) {
				RowData rd = (RowData) v.get(0);
				String projectId = rd.projectId;
				String symbol = rd.symbol;
				Map alleles = null;

				try {
					alleles = lookup(projectId);
				} catch (Exception e) {
					logger.logdInfo(e.toString(), false);
				}

				if (alleles == null) {
					alleles = new HashMap();
				}

				Map allele = new HashMap();

				// Create the allele with all the data from this row
				allele.put("projectid", rd.projectId);
				allele.put("key", rd.key);
				allele.put("symbol", symbol);
				allele.put("parentCellLine", rd.parentCellLine);
				allele.put("parentCellLineKey", rd.parentCellLineKey);

				List mcls = new ArrayList();

				for (Iterator it = v.iterator(); it.hasNext();) {
					rd = (RowData) it.next();
					mcls.add(rd.mutantCellLine);
				}

				allele.put("mutantCellLines", mcls);

				// add the new allele to the map
				alleles.put(symbol, allele);

				return new KeyValue(projectId, alleles);
			}
		}

		return new Interpreter();
	}

	/**
	 * Simple data object representing a row of data from the query
	 */
	class RowData {
		protected String projectId;
		protected Integer key;
		protected String symbol;
		protected String mutantCellLine;
		protected String parentCellLine;
		protected Integer parentCellLineKey;

		public RowData(RowReference row) throws DBException {
			projectId = row.getString("projectid");
			symbol = row.getString("symbol");
			key = row.getInt("allelekey");
			mutantCellLine = row.getString("mutantCellLine");
			parentCellLineKey = row.getInt("parentCellLine_key");

			// do the same transform to the cell line name that
			// is done to the input record cell line name
			// See SangerAlleleInput.setParentESCellName
			parentCellLine = row.getString("parentCellLine").toUpperCase()
					.replaceAll("\\s+", "").replaceAll("\\(", "")
					.replaceAll("\\)", "").replaceAll("/", "")
					.replaceAll("\\?", "").replaceAll("\\n", "")
					.replaceAll(" ", "").replaceAll("\\.", "");
		}
	}
}
