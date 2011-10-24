package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.dbs.mgd.dao.ALL_CellLine_DerivationDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_CellLine_DerivationState;
import org.jax.mgi.shr.dbutils.dao.SQLStream;
import org.jax.mgi.shr.exception.MGIException;

/**
 * 
 * Extension to the standard MGI derivation class to allow for saving new
 * derivations.
 * 
 * @author jmason
 * 
 */
public class Derivation 
extends org.jax.mgi.dbs.mgd.loads.Alo.Derivation 
{

	LookupDerivationByVectorCreatorParentType lookup;

	public Derivation() 
	throws MGIException 
	{
		super();
		lookup = LookupDerivationByVectorCreatorParentType.getInstance();
	}

	/**
	 * Insert this Derivation object into the stream to persist the record.
	 * 
	 * @param stream
	 *            The sql stream to write the record to.
	 * @return Nothing
	 */
	public void insert(SQLStream stream) 
	throws MGIException 
	{

		// Create the Accession entry
		ALL_CellLine_DerivationState state = 
			new ALL_CellLine_DerivationState();

		state.setName(this.getName());
		state.setDescription(this.getDescription());
		state.setVectorKey(this.getVectorKey());
		state.setVectorTypeKey(this.getVectorTypeKey());
		state.setParentCellLineKey(this.getParentCellLineKey());
		state.setDerivationTypeKey(this.getDerivationTypeKey());
		state.setCreatorKey(this.getCreatorKey());
		state.setRefsKey(this.getRefsKey());

		ALL_CellLine_DerivationDAO dao = 
			new ALL_CellLine_DerivationDAO(state);
		stream.insert(dao);

		// Store the key that gets generated when this object is instead
		// into the database
		this.setDerivationKey(dao.getKey().getKey());

		// Add this new derivation to the lookup
		lookup.addToCache(this);
	}

	/**
	 * override of equals method from Object class to just compare keys
	 * 
	 * @param o
	 *            the object to compare to
	 * @return true if the two objects are equal, false otherwise
	 */
	public boolean equals(Object o) 
	{
		if (!(o instanceof Derivation))
			return false;
		Derivation s = (Derivation) o;
		if (this.getDerivationKey() == s.getDerivationKey())
			return true;
		else
			return false;
	}

	/**
	 * override of toString method from Object class
	 * 
	 * @return the string representation of this instance
	 */
	public String toString() 
	{
		return "<Derivation: " + this.getName() + 
			" (key: " + this.getDerivationKey() + ")>";
	}

}
