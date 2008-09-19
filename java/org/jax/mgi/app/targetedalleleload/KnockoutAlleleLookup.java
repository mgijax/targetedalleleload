package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.dbs.mgd.LogicalDBConstants;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;

import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.cache.KeyNotFoundException;

import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;

/**
 *
 * is a FullCachedLookup storing knockout allele mutant es cell line name 
 * associations to knockout allele objects
 * @has internal cache of knockout alleles
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class KnockoutAlleleLookup extends FullCachedLookup {

    private TargetedAlleleLoadCfg cfg = null;
    private MarkerLookup markerLookup = null;
    private StrainLookup strainLookup = null;
    private ESCellLookup escellLookup = null;

    /**
     * constructor
     * @throws ConfigException thrown if there is an error accessing the
     * configuration
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * cache
     */
    public KnockoutAlleleLookup() 
    throws CacheException,ConfigException,DBException
    {
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
        try {
            cfg = new TargetedAlleleLoadCfg();
            markerLookup = new MarkerLookup();
            markerLookup.initCache();
            
            strainLookup = new StrainLookup();
            strainLookup.initCache();

		    escellLookup = new ESCellLookup(strainLookup);
            escellLookup.initCache();
		} catch (DLALoggingException e) {
		    System.out.println("KnockoutAlleleLookup DLALoggingException exception");
	    
		}
    }

    /**
     * look up an associated KnockoutAllele by a given name
     * @param name the KnockoutAllele name
     * @return the associated KnockoutAllele
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public KnockoutAllele lookup(String name)
    throws DBException, CacheException
    {
        return (KnockoutAllele)super.lookupNullsOk(name);
    }

    /**
     * get the query for fully initializing the cache
     * mouse KnockoutAlleles by name
     * @return the initialization query
     */
    public String getFullInitQuery()
    {
        String jNumber = null;
        try
        {
            jNumber = cfg.getJNumber();
        }
        catch( ConfigException e)
        {
            System.out.println("Config Exception while trying to retrieve JNUMBER");
        }

        return "SELECT alleleKey=a._Allele_key, alleleName=a.name, alleleSymbol=a.symbol, " +
               "alleleType=a._Allele_Type_key, geneSymbol=mrk.symbol, chr=mrk.chromosome, " +
               "geneKey=mrk._Marker_key, geneMgiid=acc.accID, " +
               "mescKey=mesc._CellLine_key, mescName = mesc.cellline, " +
               "pescKey=pesc._CellLine_key, pescName = pesc.cellline, " +
               "provider=mesc.provider, alleleNote=nc.note, " +
               "jNumber=bc.jnumID " +
               "FROM ALL_Allele a, BIB_Citation_Cache bc, " +
               "MGI_Reference_Assoc ra,MGI_RefAssocType rat, " +
               "MRK_Marker mrk, ALL_CellLine mesc, ALL_CellLine pesc, " +
               "MGI_Note n, MGI_NoteChunk nc, ACC_Accession acc " +
               "WHERE bc.jnumID = '"+jNumber+"' " +
               "AND ra._Refs_key = bc._Refs_key " +
               "AND ra._Object_key = a._Allele_key " +
               "and ra._RefAssocType_key = rat._RefAssocType_key " +
               "and ra._MGIType_key = rat._MGIType_key " +
               "and rat.assocType = 'Original' " +
               "and a._Marker_key = mrk._Marker_key " +
               "and a._MutantESCellLine_key = mesc._CellLine_key " +
               "and a._ESCellLine_key = pesc._CellLine_key " +
               "and acc.preferred=1 " +
               "and acc._Object_key = mrk._Marker_key " +
               "and acc.prefixpart='MGI:' " +
               "and acc._LogicalDB_key=1 " +
               "and acc._MGIType_key=2 " +
               "and n._Object_key = a._Allele_key " + 
               "and n._MGIType_key = 11 " + 
               "and n._NoteType_key = 1021 " + 
               "and n._Note_key = nc._Note_key " ;
    }

    /**
     * get the RowDataInterpreter for interpreting initialization query
     * @return the RowDataInterpreter
     */
    public RowDataInterpreter getRowDataInterpreter()
    {
        return new Interpreter();
    }

    private class Interpreter implements RowDataInterpreter
    {
        public Object interpret(RowReference row)
        throws DBException
        {
            DLALogger logger = null;
            try
            {
                logger = DLALogger.getInstance();
            }
            catch (DLALoggingException e)
            {
                logger.logdInfo(e.getMessage(), true);
                return null;
            }
            
            KnockoutAllele koAllele = null;
            try
            {
                koAllele = new KnockoutAllele();
            }
            catch (ConfigException e)
            {
                logger.logdInfo(e.getMessage(), true);
                return null;
            }
            catch (CacheException e)
            {
                logger.logdInfo(e.getMessage(), true);
                return null;
            }
            String jNumber = null;
            try
            {
                jNumber = cfg.getJNumber();
            }
            catch( ConfigException e)
            {
                logger.logdInfo(e.getMessage(), true);
            }
            koAllele.setAlleleId(row.getString("mescName"));
            koAllele.setAlleleType(new Integer(row.getInt("alleleType").intValue()));
            koAllele.setAlleleName(row.getString("alleleName"));
            koAllele.setAlleleSymbol(row.getString("alleleSymbol"));
            koAllele.setAlleleKey(row.getInt("alleleKey").intValue());
            koAllele.setAlleleName(row.getString("alleleName"));
            koAllele.setProvider(row.getString("provider"));
            koAllele.setAlleleNote(row.getString("alleleNote"));
            koAllele.setJNumber(jNumber);

            try
            {
                koAllele.setGene(markerLookup.lookup(row.getString("geneMgiid")));
            }
            catch (CacheException e)
            {
                logger.logdInfo(e.getMessage(), true);
                return null;
            }
            catch (KeyNotFoundException e)
            {
                logger.logdInfo(e.getMessage(), true);
                return null;
            }
            
            try
            {
                koAllele.setParental(escellLookup.lookup(row.getString("pescName")));
            }
            catch (CacheException e)
            {
                logger.logdInfo(e.getMessage(), true);
                return null;
            }

            try
            {
                koAllele.setMutant(escellLookup.lookup(row.getString("mescName")));
            }
            catch (CacheException e)
            {
                logger.logdInfo(e.getMessage(), true);
                return null;
            }

            return new KeyValue(row.getString("mescName"), koAllele);
        }
    }

}

