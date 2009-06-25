package org.jax.mgi.shr.config;

import java.lang.*;
import java.lang.reflect.Constructor;
import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;

import java.util.HashMap;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.dbs.SchemaConstants;

/**
 * @is An object that is used to retrieve targeted allele load specific
 *     configuration parameters.
 * @has
 *   <UL>
 *   <LI> A reference to a configuration manager
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Provides methods for retrieving configuration parameters that
 *        are specific to the targeted allele load.
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class TargetedAlleleLoadCfg extends InputDataCfg
{
    /**
     * Sole constructor. Constructs a targeted allele load configurator.
     * @assumes Nothing
     * @effects Nothing
     * @throws ConfigException if a configuration manager cannot be obtained
     * @throws DLALoggingException if a logger instance cannot be obtained
     */

    private DLALogger logger = null;
    private static String UserNotFound = ConfigExceptionFactory.UserNotFound;
    private static HashMap cache = new HashMap();

    public TargetedAlleleLoadCfg()
    throws ConfigException, DLALoggingException
    {
        logger = DLALogger.getInstance();
    }

    /**
     * Get the mutation types string - a string indicating the project's logical db for cell lines
     * @assumes The "ESCELL_LOGICAL_DB" constant is defined in the config file and
     * that the logical DB specifed there aligns with the database
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getEsCellLogicalDb() throws ConfigException
    {
        return getConfigString("ESCELL_LOGICAL_DB");
    }

    /**
     * Get the mutation types string - a string indicating the private logical DB for the project identifiers
     * @assumes The "PROJECT_LOGICAL_DB" constant is defined in the config file and
     * that the logical DB specifed there aligns with the database
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getProjectLogicalDb() throws ConfigException
    {
        return getConfigString("PROJECT_LOGICAL_DB");
    }


    /**
     * Get the promotor for a given cassette
     * @assumes The "CASSETTE_<cassette>" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getPromoter(String cassette) throws ConfigException
    {
        return getConfigString("CASSETTE_"+cassette);
    }

    /**
     * Get the mutation types string - a comma separated list of mutation
     * types
     * @assumes The "MUTATION_TYPES" constant is defined in the config file
     * as a comma seperated list of names of mutation types that exist in
     * the MGI VOC_Term table for _vocab_key = 36 (Allele Molecular Mutation)
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getMutationTypes() throws ConfigException
    {
        return getConfigString("MUTATION_TYPES");
    }


    /**
     * Get the provider string
     * @assumes The "ALLELE_PROVIDER" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getProvider() throws ConfigException
    {
        return getConfigString("ALLELE_PROVIDER");
    }
    
    /**
     * Get the Allele molecular note template string
     * @assumes The appropriate constant is defined in the config file
     * @effects Nothing
     * @return The configuration value, default is empty string
     * @throws ConfigException
     */
    public String getNoteTemplate() throws ConfigException
    {
        return getConfigString("NOTE_TEMPLATE");
    }
    public String getNoteTemplateCondPromoter() throws ConfigException
    {
        return getConfigString("NOTE_TEMPLATE_CONDITIONAL_PROMOTERDRIVEN");
    }
    public String getNoteTemplateCondPromoterless() throws ConfigException
    {
        return getConfigString("NOTE_TEMPLATE_CONDITIONAL_PROMOTERLESS");
    }
    public String getNoteTemplateNonCondPromoter() throws ConfigException
    {
        return getConfigString("NOTE_TEMPLATE_NONCONDITIONAL_PROMOTERDRIVEN");
    }
    public String getNoteTemplateNonCondPromoterless() throws ConfigException
    {
        return getConfigString("NOTE_TEMPLATE_NONCONDITIONAL_PROMOTERLESS");
    }
    public String getNoteTemplateDeletionPromoter() throws ConfigException
    {
        return getConfigString("NOTE_TEMPLATE_DELETION_PROMOTERDRIVEN");
    }
    public String getNoteTemplateDeletionPromoterless() throws ConfigException
    {
        return getConfigString("NOTE_TEMPLATE_DELETION_PROMOTERLESS");
    }



    /**
     * Get the Allele name template string
     * @assumes The "NAME_TEMPLATE" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getNameTemplate() throws ConfigException
    {
        return getConfigString("NAME_TEMPLATE");
    }

    /**
     * Get the Allele Symbol template string
     * @assumes The "SYMBOL_TEMPLATE" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getSymbolTemplate() throws ConfigException
    {
        return getConfigString("SYMBOL_TEMPLATE");
    }

    /**
     * Get the Reference JNUMBER
     * @assumes The "JNUMBER" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getJNumber() throws ConfigException
    {
        return getConfigString("JNUMBER");
    }


    /**
     * lookup the job stream key using the configured job stream name
     * @assumes nothing
     * @effects nothing
     * @return the job stream key
     * @throws ConfigException thrown if the name could not be found
     * or there was an error accessing the database or configuration file
     */
    public Integer getJobStreamKey() throws ConfigException {
        String name = getConfigString("JOBSTREAM");
        UserLookup lookup = null;
        /**
         * first check the cache
         */
        Integer key = (Integer) cache.get(name);
        if (key == null) {
            try {
                lookup = new UserLookup();
                key = lookup.lookupByName(name);
            }
            catch (MGIException e) {
                ConfigExceptionFactory eFactory = new ConfigExceptionFactory();
                ConfigException e2 = (ConfigException)
                    eFactory.getException(UserNotFound, e);
                e2.bind(name);
                throw e2;
            }
            if (key == null) {
                ConfigExceptionFactory eFactory = new ConfigExceptionFactory();
                ConfigException e2 = (ConfigException)
                    eFactory.getException(UserNotFound);
                e2.bind(name);
                throw e2;
            }
        }
        /**
         * store results in cache and return the value
         */
        cache.put(name, key);
        return key;
    }

    /**
     * A class for looking up key values in the database for a given user of
     * the system
     * @has an internal cache of user keys to user names
     * @does maintains the internal cache and provoides lookup functiomality
     * @company The Jackson Laboratory
     * @author M Walker
     *
     */
    public class UserLookup
        extends FullCachedLookup {
        // internal cache
        private HashMap cache = new HashMap();
        /**
         * the default constructor
         * @assumes nothing
         * @effects nothing
         * @throws ConfigException thrown if there is an error accessing the
         * configuration file
             * @throws DBException thrown if there is an error accessing the database
             * @throws CacheException thrown if there is an error accessing the cache
         */
        public UserLookup() throws ConfigException, DBException, CacheException {
            super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
        }

        /**
         * look up a userid for the given user name from the MGI_USER table
         * @param name the name to lookup
         * @assumes nothing
         * @effects nothing
         * @return the userid
         * @throws DBException thrown if there is an error accessing the
         * database
         * @throws CacheException thrown if there is an error accessing the
         * cache
         * @throws KeyNotFoundException thrown if the lookup fails to find a
         * value
         */
        public Integer lookupByName(String name) throws DBException,
            CacheException, KeyNotFoundException {
            return (Integer) lookup(name);
        }

        /**
         * get the query to fully initialize the cache
         * @assumes nothing
         * @effects nothing
         * @return the query to fully initialize the cache
         */
        public String getFullInitQuery() {
            return new String("SELECT _User_key, login FROM MGI_USER");
        }

        /**
         * get a RowDataInterpreter for creating
         * @return the RowDataInterpreter
         */
        public RowDataInterpreter getRowDataInterpreter() {
            class Interpreter
                implements RowDataInterpreter {
                public Object interpret(RowReference row) throws DBException {
                    return new KeyValue(row.getString(2), row.getInt(1));
                }
            }

            return new Interpreter();
        }
    }
}
