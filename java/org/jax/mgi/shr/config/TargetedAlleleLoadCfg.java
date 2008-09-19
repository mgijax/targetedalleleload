package org.jax.mgi.shr.config;

import java.lang.*;
import java.lang.reflect.Constructor;
import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.dla.log.DLALoggingException;


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

public class TargetedAlleleLoadCfg extends InputDataCfg {
    /**
     * Sole constructor. Constructs a targeted allele load configurator.
     * @assumes Nothing
     * @effects Nothing
     * @throws ConfigException if a configuration manager cannot be obtained
     * @throws DLALoggingException if a logger instance cannot be obtained
     */

    private DLALogger logger = null;

    public TargetedAlleleLoadCfg() throws ConfigException, DLALoggingException {
        logger = DLALogger.getInstance();
    }


    /**
     * Get the appropriate interpreter for the file
     * @assumes The "INTERPRETER" constant is defined in the config file
     * @effects Nothing
     * @return An appropriate Interpreter for the data being read
     * @throws ConfigException
     */
/*
    public RecordDataInterpreter getInterpreter() throws ConfigException {
        String interpClassName = getConfigString("INTERPRETER");
        RecordDataInterpreter interp = null;
        try {
            Class cls = Class.forName(interpClassName);
            interp = (RecordDataInterpreter)cls.newInstance();
        } catch (ClassNotFoundException e) {
            logger.logInfo("Interpreter class not found");
            interp = null;
        } catch (InstantiationException e) {
            logger.logInfo("Error instantiating interpreter class");
            interp = null;
        } catch (IllegalAccessException e) {
            logger.logInfo("Illegal access trying to instantiate the interpreter class");
            interp = null;
        }
        return interp;
    }
*/

    /**
     * Get the mutation types string - a string indicating a _LogicalDB_Key
     * @assumes The "LOGICAL_DB" constant is defined in the config file and
     * that the logical DB specifed there aligns with the database
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getLogicalDB() throws ConfigException {
        return getConfigString("LOGICAL_DB");
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
    public String getMutationTypes() throws ConfigException {
        return getConfigString("MUTATION_TYPES");
    }


    /**
     * Get the provider string
     * @assumes The "ALLELE_PROVIDER" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getProvider() throws ConfigException {
        return getConfigString("ALLELE_PROVIDER");
    }
    
    /**
     * Get the Allele molecular note template string
     * @assumes The "NOTE_TEMPLATE" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value, default is empty string
     * @throws ConfigException
     */
    public String getNoteTemplate() throws ConfigException {
        return getConfigString("NOTE_TEMPLATE");
    }

    /**
     * Get the Allele name template string
     * @assumes The "NAME_TEMPLATE" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getNameTemplate() throws ConfigException {
        return getConfigString("NAME_TEMPLATE");
    }

    /**
     * Get the Allele Symbol template string
     * @assumes The "SYMBOL_TEMPLATE" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getSymbolTemplate() throws ConfigException {
        return getConfigString("SYMBOL_TEMPLATE");
    }

    /**
     * Get the Reference JNUMBER
     * @assumes The "JNUMBER" constant is defined in the config file
     * @effects Nothing
     * @return The configuration value
     * @throws ConfigException if the value is not found
     */
    public String getJNumber() throws ConfigException {
        return getConfigString("JNUMBER");
    }

}


/**************************************************************************
*
* Warranty Disclaimer and Copyright Notice
*
*  THE JACKSON LABORATORY MAKES NO REPRESENTATION ABOUT THE SUITABILITY OR
*  ACCURACY OF THIS SOFTWARE OR DATA FOR ANY PURPOSE, AND MAKES NO WARRANTIES,
*  EITHER EXPRESS OR IMPLIED, INCLUDING MERCHANTABILITY AND FITNESS FOR A
*  PARTICULAR PURPOSE OR THAT THE USE OF THIS SOFTWARE OR DATA WILL NOT
*  INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS, OR OTHER RIGHTS.
*  THE SOFTWARE AND DATA ARE PROVIDED "AS IS".
*
*  This software and data are provided to enhance knowledge and encourage
*  progress in the scientific community and are to be used only for research
*  and educational purposes.  Any reproduction or use for commercial purpose
*  is prohibited without the prior express written permission of The Jackson
*  Laboratory.
*
* Copyright \251 1996, 1999, 2002, 2004 by The Jackson Laboratory
*
* All Rights Reserved
*
**************************************************************************/
