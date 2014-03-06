package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.dbs.mgd.lookup.TranslationException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.dla.log.DLALogger;

/**
 * @is An abstract object that knows how to create KOMP Clone objects from an
 *     input file. Each input file must have a class that overrides this method
 *     and provides specific handling for that file.
 * @has <UL>
 *      <LI>KOMP Clone object.
 *      </UL>
 * @does <UL>
 *       <LI>Parses a KnockoutAlleleInput record into a KnockoutAllele object
 *       <LI>
 *       </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

abstract class KnockoutAlleleProcessor {
	protected QualityControlStatistics qcStatistics = null;
        protected DLALogger logger =  null;

	public KnockoutAlleleProcessor() throws MGIException {
	    qcStatistics = new QualityControlStatistics();
	    logger = DLALogger.getInstance();

	}
	public abstract KnockoutAllele process(KnockoutAlleleInput in)
			throws RecordFormatException, ConfigException,
			KeyNotFoundException, DBException, CacheException,
			TranslationException, DLALoggingException, MGIException;

}
