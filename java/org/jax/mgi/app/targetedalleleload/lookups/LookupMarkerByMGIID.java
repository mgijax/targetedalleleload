package org.jax.mgi.app.targetedalleleload.lookups;

import org.jax.mgi.app.targetedalleleload.Marker;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.LazyCachedLookup;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.exception.MGIException;

/**
 * 
 * is a FullCachedLookup storing MGI ID associations to markers
 * 
 * @has internal cache of MGI ID to marker associations
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class LookupMarkerByMGIID
extends LazyCachedLookup
{

	private static LookupMarkerByMGIID _instance;

	public static LookupMarkerByMGIID getInstance() 
	throws MGIException 
	{
		if (_instance == null) {
			_instance = new LookupMarkerByMGIID();
		}
		return _instance;
	}

	/**
	 * constructor uses the singleton pattern
	 * 
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 */
	private LookupMarkerByMGIID() 
	throws MGIException 
	{
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
	}

	/**
	 * look up an associated marker to a given MGI ID. The Marker MUST exist or
	 * the allele will not be loaded, throw an exception if the marker doesn't
	 * exist
	 * 
	 * @param mgiid
	 *            the primary MGI ID of the marker
	 * @return the associated marker
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 */
	public Marker lookup(String mgiid) 
	throws DBException, CacheException, KeyNotFoundException 
	{
		return (Marker) super.lookup(mgiid);
	}

	/**
	 * get the query for fully initializing the cache mouse markers annotated to
	 * MGI ID
	 * 
	 * @return the initialization query
	 */
//	public String getFullInitQuery() {
	public String getPartialInitQuery() 
	{
		return null;
	}

    /**
     * get the query to use when adding new entries to the cache
     * 
     * @param addObject the lookup identifier which triggers the cache add
     * 
     * @return the query string to add an allele to the cache based
     * 			on the given cellline
     */
    public String getAddQuery(Object addObject)
    {
    	String MGIID = (String)addObject;

    	return "SELECT  mgiid = a2.accID, markerKey = a2._Object_key, " +
		"symbol = m.symbol, chromosome = m.chromosome, " +
    	"statusKey = m._marker_status_key " +
		"FROM ACC_Accession a2, MRK_Marker m " +
		"WHERE a2._MGIType_key = 2 " + 
		"AND a2.accId = '" + MGIID + "' " +
		"AND a2._LogicalDB_key = 1 " +
		"AND a2.preferred = 1 " +
		"AND a2.prefixPart = 'MGI:' " +
		"AND a2._Object_key = m._Marker_key " +
		"AND m._Organism_key = 1 ";
    }
	
	/**
	 * get the RowDataInterpreter for interpreting initialization query
	 * 
	 * @return the RowDataInterpreter
	 */
	public RowDataInterpreter getRowDataInterpreter() 
	{
		return new Interpreter();
	}

	private class Interpreter 
	implements RowDataInterpreter 
	{
		public Object interpret(RowReference row) 
		throws DBException 
		{
			Marker marker = new Marker(row.getString("mgiid"),
					row.getString("symbol"), 
					row.getString("chromosome"),
					row.getInt("statusKey"),
					row.getInt("markerKey"));
			return new KeyValue(row.getString("mgiid"), marker);
		}
	}
}
