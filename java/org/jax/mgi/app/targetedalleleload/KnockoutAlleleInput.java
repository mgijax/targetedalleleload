package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import org.jax.mgi.shr.exception.MGIException;

/**
 * @is An object that represents a Regeneron Knockout Allele Input record.
 * @has
 *   <UL>
 *   <LI> Configuration parameters that are needed to populate a
 * KnockoutAllele object.
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Provides methods for setting all its attributes.
 *   <LI> Encapsulates a single row of data from the input file</LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

interface KnockoutAlleleInput
{
    String getAlleleSymbol() throws MGIException;
    String getProjectId();
    String getESCellName();
}