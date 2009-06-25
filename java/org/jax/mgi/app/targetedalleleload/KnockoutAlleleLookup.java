package org.jax.mgi.app.targetedalleleload;

import java.util.Vector;
import java.util.Iterator;

import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.dbs.mgd.LogicalDBConstants;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.MultiRowInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;

import org.jax.mgi.shr.exception.MGIException;
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

public class KnockoutAlleleLookup
extends FullCachedLookup
{

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
        try
        {
            cfg = new TargetedAlleleLoadCfg();
            markerLookup = new MarkerLookup();
            markerLookup.initCache();
            
            strainLookup = new StrainLookup();
            strainLookup.initCache();

            escellLookup = new ESCellLookup();
            escellLookup.initCache();
        }
        catch (DLALoggingException e)
        {
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
            System.out.println("Config Exception retrieving JNUMBER");
        }

        return "SELECT alleleKey=a._Allele_key, alleleName=a.name, " +
               "alleleSymbol=a.symbol, alleleType=a._Allele_Type_key, " +
               "geneSymbol=mrk.symbol, chr=mrk.chromosome, " +
               "geneKey=mrk._Marker_key, geneMgiid=acc.accID, " +
               "mescKey=mesc._CellLine_key, mescName = mesc.cellline, " +
               "pescKey=pesc._CellLine_key, pescName = pesc.cellline, " +
               "provider=mesc.provider, alleleNote=nc.note, " +
               "alleleNoteSeq=nc.sequenceNum, alleleNoteKey=nc._note_key, " +
               "alleleNoteModifiedBy=n._ModifiedBy_key, " +
               "alleleNoteCreatedBy=n._CreatedBy_key, " +
               "jNumber=bc.jnumID, projectId=acc2.accId  " +
               "FROM ALL_Allele a, BIB_Citation_Cache bc, " +
               "MGI_Reference_Assoc ra,MGI_RefAssocType rat, " +
               "MRK_Marker mrk, ALL_CellLine mesc, ALL_CellLine pesc, " +
               "MGI_Note n, MGI_NoteChunk nc, ACC_Accession acc, " +
               "ACC_Accession acc2 " +
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
               "and acc2.preferred=1 " +
               "and acc2.private=1 " +
               "and acc2._Object_key = a._Allele_key " +
               "and acc2._LogicalDB_key in (125,126) " +
               "and acc2._MGIType_key=11 " +
               "and n._Object_key = a._Allele_key " +
               "and n._MGIType_key = 11 " +
               "and n._NoteType_key = 1021 " +
               "and n._Note_key = nc._Note_key " +
               "order by alleleKey, alleleNoteSeq " ;
    }

    /**
     * return the RowDataInterpreter for creating KeyValue objects from the 
     *        query results
     * @return the RowDataInterpreter for this query
     */
    public RowDataInterpreter getRowDataInterpreter()
    {
        class Interpreter implements MultiRowInterpreter
        {
            public Object interpret(RowReference ref)
            throws DBException
            {
                return new RowData(ref);
            }

            public Object interpretKey(RowReference row)
            throws DBException
            {
                return row.getString("projectId");
            }

            public Object interpretRows(Vector v)
            {
                // All rows return the same values for every columns EXCEPT
                // for the alleleNote column (one row per note chunk)
                RowData rd = (RowData)v.get(0);
                
                String projectId = rd.projectId;
                String completeNote = "";

                DLALogger logger = null;
                KnockoutAllele koAllele = null;
                
                try
                {
                    logger = DLALogger.getInstance();
                }
                catch (DLALoggingException e)
                {
                    logger.logdInfo(e.getMessage(), true);
                    return null;
                }

                try
                {
                    koAllele = new KnockoutAllele();
                }
                catch (MGIException e)
                {
                    logger.logdInfo(e.getMessage(), true);
                    return null;
                }

                koAllele.setESCellName(rd.esCellName);
                koAllele.setAlleleType(rd.alleleType);
                koAllele.setAlleleName(rd.alleleName);
                koAllele.setAlleleSymbol(rd.alleleSymbol);
                koAllele.setAlleleKey(rd.alleleKey);
                koAllele.setAlleleName(rd.alleleName);
                koAllele.setProvider(rd.provider);
                koAllele.setProjectId(rd.projectId);
                koAllele.setAlleleNote(rd.alleleNote);
                koAllele.setAlleleNoteKey(rd.alleleNoteKey);
                koAllele.setAlleleNoteCreatedBy(rd.alleleNoteCreatedBy);
                koAllele.setAlleleNoteModifiedBy(rd.alleleNoteModifiedBy);
                koAllele.setJNumber(rd.jNumber);

                for (Iterator it = v.iterator(); it.hasNext();)
                {
                    rd = (RowData)it.next();

                    // Concat all the notechunks together in the allele note
                    completeNote += rd.alleleNote;
                }
                
                koAllele.setAlleleNote(completeNote.trim());

                try
                {
                    koAllele.setGene(markerLookup.lookup(rd.geneMgiid));
                    koAllele.setParental(escellLookup.lookup(rd.pescName));
                    koAllele.setMutant(escellLookup.lookup(rd.mescName));
                }
                catch (MGIException e)
                {
                    logger.logdInfo(e.getMessage(), true);
                    return null;
                }

                return new KeyValue(projectId, koAllele);
            }
        }

        return new Interpreter();
    }

    /**
     * Simple data object representing a row of data from the query
     */
    class RowData
    {
        protected String esCellName;
        protected Integer alleleType;
        protected String alleleName;
        protected String alleleSymbol;
        protected int alleleKey;
        protected String provider;
        protected String projectId;
        protected String alleleNote;
        protected String alleleNoteKey;
        protected String alleleNoteCreatedBy;
        protected String alleleNoteModifiedBy;
        protected String jNumber;
        protected Marker gene;
        protected String pescName;
        protected String mescName;
        protected String geneMgiid;

        public RowData (RowReference row) throws DBException
        {
            esCellName = row.getString("mescName");
            mescName = row.getString("mescName");
            pescName = row.getString("pescName");
            alleleType = new Integer(row.getInt("alleleType").intValue());
            alleleName = row.getString("alleleName");
            alleleSymbol = row.getString("alleleSymbol");
            alleleKey = row.getInt("alleleKey").intValue();
            provider = row.getString("provider");
            projectId = row.getString("projectId");
            alleleNote = row.getString("alleleNote");
            alleleNoteKey = row.getString("alleleNoteKey");
            alleleNoteCreatedBy = row.getString("alleleNoteCreatedBy");
            alleleNoteModifiedBy = row.getString("alleleNoteModifiedBy");
            jNumber = row.getString("jNumber");
            geneMgiid = row.getString("geneMgiid");
        }
    }
}

