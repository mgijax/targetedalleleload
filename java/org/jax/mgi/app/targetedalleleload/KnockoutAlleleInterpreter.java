package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import java.util.TreeMap;

import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.config.ConfigException;

/**
 * @is An abstract object that knows how to read clone records from
 * an input file.  Each input file must have a class that overrides this
 * method and provides specific handling for that file.
 * @has
 *   <UL>
 *   <LI> KOMP Clone object.
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Reads in a KnockoutAlleleInput record
 *   <LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

abstract class KnockoutAlleleInterpreter implements RecordDataInterpreter
{

    protected QualityControlStatistics qcStatistics = new QualityControlStatistics();

}