package org.jax.mgi.shr.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.exception.MGIException;

/**
 * @is An object that is used to retrieve targeted allele load specific
 *     configuration parameters.
 * @has <UL>
 *      <LI>A reference to a configuration manager
 *      </UL>
 * @does <UL>
 *       <LI>Provides methods for retrieving configuration parameters that are
 *       specific to the targeted allele load.
 *       </UL>
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */

public class TargetedAlleleLoadCfg extends InputDataCfg {
	private static String UserNotFound = ConfigExceptionFactory.UserNotFound;
	private static HashMap cache = new HashMap();

	public TargetedAlleleLoadCfg() throws ConfigException, DLALoggingException {
		DLALogger.getInstance();
	}

	/**
	 * Is the load allowed to overwrite the molecular notes?
	 * 
	 * @assumes The "MGD_BCP_PREVENT_EXECUTE" constant is defined
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public boolean getPreventBcpExecute() throws ConfigException {
		return getConfigBoolean("MGD_BCP_PREVENT_EXECUTE").booleanValue();
	}

	/**
	 * Is the load allowed to overwrite the molecular notes?
	 * 
	 * @assumes The "OVERWRITE_NOTE" constant is defined in the config file and
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public boolean getOverwriteNote() throws ConfigException {
		return getConfigBoolean("OVERWRITE_NOTE").booleanValue();
	}

	/**
	 * Get the mutation types string - a string indicating the project's logical
	 * db for cell lines
	 * 
	 * @assumes The "ESCELL_LOGICAL_DB" constant is defined in the config file
	 *          and that the logical DB specified there aligns with the database
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public Integer getEsCellLogicalDb() throws ConfigException {
		return getConfigInteger("ESCELL_LOGICAL_DB");
	}

	/**
	 * Get the project logical database id - indicates the private logical DB
	 * for the project identifiers
	 * 
	 * @assumes The "PROJECT_LOGICAL_DB" constant is defined in the config file
	 *          and that the logical DB specified there aligns with the database
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public Integer getProjectLogicalDb() throws ConfigException {
		return getConfigInteger("PROJECT_LOGICAL_DB");
	}

	/**
	 * Celllines that the load knows about
	 * 
	 * @assumes The "KNOWN_CELLLINES" constant is defined in the config file and
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public List getKnownCelllines() throws ConfigException {
		List a = Arrays.asList(getConfigString("KNOWN_CELLLINES").split(","));
		for (Iterator it = a.iterator(); it.hasNext();) {
			// Strip the spaces out of the elements
			String i = (String) it.next();
			i.replaceAll(" ", "");
		}
		return a;
	}

	/**
	 * Celllines that this instance of the load is allowed to load
	 * 
	 * @assumes The "ALLOWED_CELLLINES" constant is defined in the config file
	 *          and
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public List getAllowedCelllines() throws ConfigException {
		List a = Arrays.asList(getConfigString("ALLOWED_CELLLINES").split(","));
		for (Iterator it = a.iterator(); it.hasNext();) {
			// Strip the spaces out of the elements
			String i = (String) it.next();
			i.replaceAll(" ", "");
		}
		return a;

	}

	/**
	 * The pipeline this load is loading alleles for
	 * 
	 * @assumes The "PIPELINE" constant is defined in the config file and
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getPipeline() throws ConfigException {
		return getConfigString("PIPELINE");
	}

	/**
	 * The provider this load is loading alleles for
	 * 
	 * @assumes The "PROVIDER" constant is defined in the config file and
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getProvider() throws ConfigException {
		return getConfigString("PROVIDER");
	}

	/**
	 * Get the promotor for a given cassette
	 * 
	 * @assumes The "CASSETTE_<cassette>" constant is defined in the config file
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getPromoter(String cassette) throws ConfigException {
		return getConfigString("CASSETTE_" + cassette);
	}

	/**
	 * Get the allele type Integer from the config file
	 * 
	 * @assumes The "ALLELE_TYPE_*" values are the keys of the VOC_Terms for the
	 *          allele types
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public Integer getAlleleType(String type) throws ConfigException {
		return getConfigInteger("ALLELE_TYPE_" + type);
	}

	/**
	 * Get the mutation types string from the config file - a comma separated
	 * list of mutation types
	 * 
	 * @assumes The "MUTATION_TYPES" constant is defined in the config file as a
	 *          comma separated list of names of mutation types that exist in
	 *          the MGI VOC_Term table for _vocab_key = 36 (Allele Molecular
	 *          Mutation)
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getMutationTypes() throws ConfigException {
		return getConfigString("MUTATION_TYPES");
	}

	/**
	 * Get the mutation types string from the config file - a comma separated
	 * list of mutation types
	 * 
	 * @assumes The "MUTATION_TYPES" constant is defined in the config file as a
	 *          comma separated list of names of mutation types that exist in
	 *          the MGI VOC_Term table for _vocab_key = 36 (Allele Molecular
	 *          Mutation) an optional "type" is included, with the default type
	 *          being returned if the "type" is not specified here
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getMutationTypes(String type) throws ConfigException {
		if (type.equals("Deletion")) {
			return getConfigString("MUTATION_TYPES_DELETION");
		}
		return getConfigString("MUTATION_TYPES");
	}

	/**
	 * Get the database key for a VOC_Term identifying who created the alleles
	 * associated to this derivation
	 * 
	 * @assumes The "DERIVATION_CREATOR_KEY" constant is defined in the config
	 *          file
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getCreatorKey() throws ConfigException {
		return getConfigString("DERIVATION_CREATOR_KEY");
	}

	/**
	 * Get the database key for an ALL_CellLine identifying a parental ES Cell
	 * key for alleles associated to this derivation
	 * 
	 * @assumes The "DERIVATION_PARENTAL_KEY_[ALL_CellLine.cellLine]" constant
	 *          is defined in the config file. We use cfg object for this
	 *          instead of a direct cell line lookup so we can override values
	 *          for parental es cell name supplied in the input file with
	 *          standardized names in MGD. The cell line name must be stripped
	 *          of all "funny" characters (funny meaning non-alphanumeric)
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public Integer getParentalKey(String p) throws ConfigException {
		String q = "DERIVATION_PARENTAL_KEY_"
				+ p.replaceAll(" ", "").toUpperCase();
		return getConfigInteger(q);
	}

	/**
	 * Get the Allele molecular note template string
	 * 
	 * @assumes The appropriate constant is defined in the config file
	 * @effects Nothing
	 * @return The configuration value, default is empty string
	 * @throws ConfigException
	 */
	public String getNoteTemplate() throws ConfigException {
		return getConfigString("NOTE_TEMPLATE");
	}

	public String getNoteTemplateCondDel_BactPneo_FFL() throws ConfigException {
		return getConfigString("NOTE_TEMPLATE_CONDITIONAL_DELBACTPNEOFFL");
	}

	public String getNoteTemplateNonCondDel_BactPneo_FFL()
			throws ConfigException {
		return getConfigString("NOTE_TEMPLATE_NONCONDITIONAL_DELBACTPNEOFFL");
	}

	public String getNoteTemplateMissingCoords() throws ConfigException {
		return getConfigString("MISSINGCOORDS_NOTE_TEMPLATE");
	}

	public String getNoteTemplateCondPromoter() throws ConfigException {
		return getConfigString("NOTE_TEMPLATE_CONDITIONAL_PROMOTERDRIVEN");
	}

	public String getNoteTemplateCondPromoterless() throws ConfigException {
		return getConfigString("NOTE_TEMPLATE_CONDITIONAL_PROMOTERLESS");
	}

	public String getNoteTemplateNonCondPromoter() throws ConfigException {
		return getConfigString("NOTE_TEMPLATE_NONCONDITIONAL_PROMOTERDRIVEN");
	}

	public String getNoteTemplateNonCondPromoterless() throws ConfigException {
		return getConfigString("NOTE_TEMPLATE_NONCONDITIONAL_PROMOTERLESS");
	}

	public String getNoteTemplateDeletionPromoter() throws ConfigException {
		return getConfigString("NOTE_TEMPLATE_DELETION_PROMOTERDRIVEN");
	}

	public String getNoteTemplateDeletionPromoterless() throws ConfigException {
		return getConfigString("NOTE_TEMPLATE_DELETION_PROMOTERLESS");
	}

	/**
	 * Get the string identifying promoter driven cassettes
	 * 
	 * @assumes The appropriate constant is defined in the config file
	 * @effects Nothing
	 * @return The configuration value, default is empty string
	 * @throws ConfigException
	 */
	public String getPromoterDrivenCassettes() throws ConfigException {
		return getConfigString("PROMOTERDRIVEN_CASSETTES");
	}

	/**
	 * Get the string identifying promoter driven cassettes
	 * 
	 * @assumes The appropriate constant is defined in the config file
	 * @effects Nothing
	 * @return The configuration value, default is empty string
	 * @throws ConfigException
	 */
	public String getPromoterLessCassettes() throws ConfigException {
		return getConfigString("PROMOTERLESS_CASSETTES");
	}

	/**
	 * Get the Allele name template string
	 * 
	 * @assumes The "NAME_TEMPLATE" constant is defined in the config file
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getNameTemplate() throws ConfigException {
		return getConfigString("NAME_TEMPLATE");
	}

	/**
	 * Get the Allele Symbol template string
	 * 
	 * @assumes The "SYMBOL_TEMPLATE" constant is defined in the config file
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getSymbolTemplate() throws ConfigException {
		return getConfigString("SYMBOL_TEMPLATE");
	}

	/**
	 * Get the Reference JNUMBER
	 * 
	 * @assumes The "JNUMBER" constant is defined in the config file
	 * @effects Nothing
	 * @return The configuration value
	 * @throws ConfigException
	 *             if the value is not found
	 */
	public String getJNumber() throws ConfigException {
		return getConfigString("JNUMBER");
	}

	/**
	 * lookup the job stream key using the configured job stream name
	 * 
	 * @assumes nothing
	 * @effects nothing
	 * @return the job stream key
	 * @throws ConfigException
	 *             thrown if the name could not be found or there was an error
	 *             accessing the database or configuration file
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
			} catch (MGIException e) {
				ConfigExceptionFactory eFactory = new ConfigExceptionFactory();
				ConfigException e2 = (ConfigException) eFactory.getException(
						UserNotFound, e);
				e2.bind(name);
				throw e2;
			}
			if (key == null) {
				ConfigExceptionFactory eFactory = new ConfigExceptionFactory();
				ConfigException e2 = (ConfigException) eFactory
						.getException(UserNotFound);
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
	 * A class for looking up key values in the database for a given user of the
	 * system
	 * 
	 * @has an internal cache of user keys to user names
	 * @does maintains the internal cache and provides lookup functionality
	 * @company The Jackson Laboratory
	 * @author M Walker
	 * 
	 */
	public class UserLookup extends FullCachedLookup {
		/**
		 * the default constructor
		 * 
		 * @assumes nothing
		 * @effects nothing
		 * @throws ConfigException
		 *             thrown if there is an error accessing the configuration
		 *             file
		 * @throws DBException
		 *             thrown if there is an error accessing the database
		 * @throws CacheException
		 *             thrown if there is an error accessing the cache
		 */
		public UserLookup() throws ConfigException, DBException, CacheException {
			super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
		}

		/**
		 * look up a user id for the given user name from the MGI_USER table
		 * 
		 * @param name
		 *            the name to lookup
		 * @assumes nothing
		 * @effects nothing
		 * @return the user id
		 * @throws DBException
		 *             thrown if there is an error accessing the database
		 * @throws CacheException
		 *             thrown if there is an error accessing the cache
		 * @throws KeyNotFoundException
		 *             thrown if the lookup fails to find a value
		 */
		public Integer lookupByName(String name) throws DBException,
				CacheException, KeyNotFoundException {
			return (Integer) lookup(name);
		}

		/**
		 * get the query to fully initialize the cache
		 * 
		 * @assumes nothing
		 * @effects nothing
		 * @return the query to fully initialize the cache
		 */
		public String getFullInitQuery() {
			return new String("SELECT _User_key, login FROM MGI_USER");
		}

		/**
		 * get a RowDataInterpreter for creating
		 * 
		 * @return the RowDataInterpreter
		 */
		public RowDataInterpreter getRowDataInterpreter() {
			class Interpreter implements RowDataInterpreter {
				public Object interpret(RowReference row) throws DBException {
					return new KeyValue(row.getString(2), row.getInt(1));
				}
			}

			return new Interpreter();
		}
	}

}
