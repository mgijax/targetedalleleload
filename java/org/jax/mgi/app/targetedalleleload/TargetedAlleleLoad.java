package org.jax.mgi.app.targetedalleleload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collection;

import org.jax.mgi.app.targetedalleleload.lookups.LookupAlleleByCellLine;
import org.jax.mgi.app.targetedalleleload.lookups.LookupAlleleByKey;
import org.jax.mgi.app.targetedalleleload.lookups.LookupAllelesByMarker;
import org.jax.mgi.app.targetedalleleload.lookups.LookupAllelesByProjectId;
import org.jax.mgi.app.targetedalleleload.lookups.LookupCellLineCountByAlleleSymbol;
import org.jax.mgi.app.targetedalleleload.lookups.LookupCelllinesByJnumber;
import org.jax.mgi.app.targetedalleleload.lookups.LookupDerivationByVectorCreatorParentType;
import org.jax.mgi.app.targetedalleleload.lookups.LookupMarkerByMGIID;
import org.jax.mgi.app.targetedalleleload.lookups.LookupMutantCelllineByName;
import org.jax.mgi.app.targetedalleleload.lookups.LookupStrainKeyByCellLineKey;
import org.jax.mgi.app.targetedalleleload.lookups.LookupVectorKeyByTerm;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.dbs.mgd.AccessionLib;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_CellLineDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_Allele_CellLineState;
import org.jax.mgi.dbs.mgd.dao.ALL_CellLineDAO;
import org.jax.mgi.dbs.mgd.lookup.CellLineNameLookupByKey;
import org.jax.mgi.dbs.mgd.lookup.ParentStrainLookupByParentKey;
import org.jax.mgi.dbs.mgd.lookup.StrainKeyLookup;
import org.jax.mgi.dbs.mgd.lookup.StrainNameLookup;
import org.jax.mgi.dbs.mgd.lookup.VocabTermLookup;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.dbutils.dao.BCP_Stream;
import org.jax.mgi.shr.dbutils.Table;
import org.jax.mgi.shr.dla.loader.DLALoader;
import org.jax.mgi.shr.dla.loader.DLALoaderException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.ioutils.InputDataFile;
import org.jax.mgi.shr.ioutils.RecordDataIterator;

/**
* The TargetedAlleleLoad class is the main entry for this load. It is a
* DLALoader style class that is responsible for reading an Allele input file
* (downloaded nightly from the KOMP production centers) and determining, for
* each line in the file, if an Allele already exists in the MGI database. If it
* does not exist, The TargetedAlleleLoad creates the allele. If it does exist,
* the Allele gets a series of quality control checks performed on it.
* 
* @is a DLALoader for loading KOMP produced Alleles into the database and
*     associating them with appropriate marker annotations, strains, molecular
*     notes, and J-number references. This process will also create the
*     official nomenclature and generate an official MGI ID for the allele.
* 
*/

public class TargetedAlleleLoad extends DLALoader {

    private QualityControlStatistics qcStats = new QualityControlStatistics();

    // String constants for QC reporting
    // ERROR
    private static final String NUM_DERIVATIONS_NOT_FOUND = "Number of derivations not found";
    // ERROR
    private static final String NUM_BAD_CELLLINE_PROCESSING = "Number of cell lines that were not able to be constructed";
    // SUMMARY
    private static final String NUM_ALLELES_NOTE_CHANGE = "Number of alleles that had molecular notes updated";
    // SUMMARY
    private static final String NUM_CELLLINES_CHANGE_TYPE = "Number of cell lines that changed type";
    // WARNING
    private static final String NUM_CELLLINES_CHANGED_DERIVATION = "Number of cell lines that changed derivation";
    // SUMMARY
    private static final String NUM_CELLLINES_CHANGED_PIPELINE = "Number of cell lines that changed IKMC groups";
    // SUMMARY
    private static final String NUM_CELLLINES_CHANGED_NUMBER = "Number of cell lines that changed sequence number";
    // ERROR
    private static final String NUM_CELLINES_MISSING_ALLELE = "Number of cell lines that cannot find associated allele";
    // SUMMARY
    private static final String NUM_CELLINES_CHANGED_MARKER = "Number of cell lines that changed marker, skipped";
    // SUMMARY
    private static final String NUM_ALLELES_CHANGED_TRANS = "Number of alleles that changed transmission type, not processed";
    // ERROR
    private static final String NUM_BAD_ALLELE_PROCESSING = "Number of alleles that were not able to be constructed";
    // ERROR
    private static final String NUM_MISSING_PARENT = "Number of input records missing parental cell line";
    // SUMMARY
    private static final String NUM_CELLLINES_CREATED = "Number of cell lines created";
    // SUMMARY
    private static final String NUM_ALLELES_CREATED = "Number of alleles created";
    // WARNING
    private static final String NUM_DUPLICATE_INPUT_REC = "Number of duplicate cell line records in input file";
    // WARNING
    private static final String NUM_BAD_INPUT_REC = "Number of input records that were unable to be processed";
    // SUMMARY
    private static final String NUM_CELLLINES_CHANGED_CREATOR = "Number of cell lines that changed creator";
    // SUMMARY
    private static final String NUM_CELLLINES_CHANGED_ALLELE = "Number of cell lines that changed allele associations";
    // WARNING
    private static final String NUM_ORPHANED_ALLELES = "Number of orphaned alleles created";
    // SUMMARY
    private static final String NUM_CELLLINES_PASSED_QC = "Number of cell lines that passed QC check";
    // ERROR
    private static final String NUM_WITHDRAWN_MARKER = "Number of cell lines that are associated to withdrawn markers";
    // ERROR
    private static final String BAD_MARKER_ID = "Number of cell lines that are associated to missing markers";

    // String constants for Log messages
    private static final String LOG_ALLELE_NOT_FOUND = "Cell line ~~INPUT_MCL~~ found in database, but cannot find associated allele\n";
    private static final String LOG_MARKER_CHANGED = "MUTANT ES CELL CHANGED MARKER\n"
		    + "Mutant Cell line: ~~INPUT_MCL~~\n"
		    + "Existing Marker: ~~EXISTING_MARKER~~\n"
		    + "Changed to: ~~INPUT_MARKER~~\n";
    private static final String LOG_ALLELE_TRANSMISSION_CHANGED = "ALLELE GERMLINE TRANSMISSION\n"
		    + "An attempt to change Mutant Cell line ~~INPUT_MCL~~ from allele\n"
		    + "~~EXISTING_ALLELE~~ to allele ~~CONSTRUCTED_ALLELE~~ failed because\n"
		    + "the transmission is germline.  No further automated action can be taken on ~~EXISTING_ALLELE~~.\n";
    private static final String LOG_CELLLINE_TYPE_CHANGED = "MUTANT ES CELL CHANGED TYPE\n"
		    + "Mutant Cell line: ~~INPUT_MCL~~\n"
		    + "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
		    + "New allele symbol: ~~INPUT_SYMBOL~~\n";
    private static final String LOG_CELLLINE_GROUP_CHANGED = "MUTANT ES CELL CHANGED GROUP\n"
		    + "Mutant Cell line: ~~INPUT_MCL~~\n"
		    + "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
		    + "New allele symbol: ~~INPUT_SYMBOL~~\n";
    private static final String LOG_CELLLINE_CREATOR_CHANGED = "MUTANT ES CELL CHANGED CREATOR\n"
		    + "Mutant Cell line: ~~INPUT_MCL~~\n"
		    + "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
		    + "New allele symbol: ~~INPUT_SYMBOL~~\n";
    private static final String LOG_CELLLINE_DERIVATION_CHANGED = "MUTANT ES CELL CHANGED DERIVATION\n"
		    + "Mutant Cell line: ~~INPUT_MCL~~\n"
		    + "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
		    + "Changed derivation from ~~EXISTING_DERIVATION~~ to ~~INPUT_DERIVATION~~\n";
    private static final String LOG_CELLLINE_NUMBER_CHANGED = "MUTANT ES CELL CHANGED SEQUENCE NUMBER\n"
		    + "Mutant Cell line: ~~INPUT_MCL~~\n"
		    + "Existing allele symbol: ~~EXISTING_SYMBOL~~\n"
		    + "New allele symbol: ~~INPUT_SYMBOL~~\n";
    private static final String LOG_CELLLINE_ALLELE_CHANGED = "MUTANT ES CELL CHANGED ALLELE\n"
		    + "Mutant Cell line: ~~INPUT_MCL~~\n"
		    + "Old allele symbol: ~~EXISTING_SYMBOL~~\n"
		    + "New allele symbol: ~~INPUT_SYMBOL~~\n"
		    + "Derivation changed from ~~EXISTING_DERIVATION~~ to ~~INPUT_DERIVATION~~\n";

    // Constant Regular expression patterns
    private static final Pattern pipelinePattern = Pattern
		    .compile(".*<tm\\d{1,2}[ae]{0,1}\\((.*)\\).*>");
    private static final Pattern alleleSequencePattern = Pattern
		    .compile(".*<tm(\\d{1,2})[ae]{0,1}.*>");
    private static final Pattern alleleTypePattern = Pattern
		    .compile(".*<tm\\d{1,2}([ae]{0,1}).*>");
    private static final Pattern alleleCreatorPattern = Pattern
		    .compile(".*<tm\\d{1,2}[ae]{0,1}\\(.*\\)(.*)>");

    // Standard DLA required classes
    private RecordDataIterator iter;
    private KnockoutAlleleProcessor processor;
    private KnockoutAlleleInterpreter interp;
    private KnockoutAlleleFactory alleleFactory;
    private TargetedAlleleLoadCfg cfg;
    private SQLDataManager sqlDBMgr;
    private Timestamp currentTime;

    // Cached DB Lookups
    private LookupMutantCelllineByName lookupMutantCelllineByName;
    private LookupAlleleByKey lookupAlleleByKey;
    private LookupAlleleByCellLine lookupAlleleByCellLine;
    private LookupAllelesByProjectId lookupAllelesByProjectId;
    private LookupAllelesByMarker lookupAllelesByMarker;
    private LookupDerivationByVectorCreatorParentType derivationLookup;
    private LookupVectorKeyByTerm lookupVectorKeyByTerm;
    private LookupMarkerByMGIID lookupMarkerByMGIID;
    private ParentStrainLookupByParentKey parentStrainLookupByParentKey;
    private StrainKeyLookup strainKeyLookup;
    private VocabTermLookup vocTermLookup;
    private CellLineNameLookupByKey cellLineNameLookupByKey;
    private LookupStrainKeyByCellLineKey lookupStrainKeyByCellLineKey;
    private StrainNameLookup strainNameLookup;
    private LookupCellLineCountByAlleleSymbol lookupCellLineCountByAlleleSymbol;

    // Class variables to hold global QC data
    private Map alleleProjects = new HashMap();
    private Map alleleNotes = new HashMap();
    private Set alleleProjectIdUpdated = new TreeSet();
    private Set databaseProjectIds = new HashSet();
    private Set databaseCellLines = new HashSet();

    /**
     * The
     * 
     * @throws DLALoaderException
     *             thrown if the super class cannot be instantiated
     */
    public TargetedAlleleLoad() throws MGIException {

	// Instance the configuration object
	sqlDBMgr = SQLDataManagerFactory.getShared(SchemaConstants.MGD);

    }

    /**
     * initialize the internal structures used by this class
     * 
     * @assumes nothing
     * @effects internal structures are initialized
     */
    protected void initialize() throws MGIException {

	logger.logdInfo("Initializing Targeted allele load\n", true);

	cfg = new TargetedAlleleLoadCfg();

	sqlDBMgr.setLogger(logger);
	logger.logdDebug("TargetedAlleleLoader sqlDBMgr.server "
			+ sqlDBMgr.getServer());
	logger.logdDebug("TargetedAlleleLoader sqlDBMgr.database "
			+ sqlDBMgr.getDatabase());

	logger.logdDebug("Initializing lookupMutantCelllineByName", true);
	lookupMutantCelllineByName = new LookupMutantCelllineByName();

	logger.logdDebug("Initializing lookupAllelesByProjectId", true);
	lookupAllelesByProjectId = LookupAllelesByProjectId.getInstance();
	//logger.logdInfo("lookupAllelesByProjectId cache:", true);
	//logger.logdInfo(lookupAllelesByProjectId.lookup("31080").toString(), true);
	//logger.logdInfo(lookupAllelesByProjectId.lookup("72104").toString(), true);

	logger.logdDebug("Initializing lookupAllelesByMarker", true);
	lookupAllelesByMarker = LookupAllelesByMarker.getInstance();
	
	logger.logdDebug("Initializing lookupCellLineCountByAlleleSymbol", true);
	lookupCellLineCountByAlleleSymbol = 
		LookupCellLineCountByAlleleSymbol.getInstance();

	logger.logdDebug("Initializing derivationLookup", true);
	derivationLookup = 
		LookupDerivationByVectorCreatorParentType.getInstance();

	logger.logdDebug("Initializing lookupAlleleByCellLine", true);
	lookupAlleleByCellLine = LookupAlleleByCellLine.getInstance();

	logger.logdDebug("Initializing lookupAlleleByKey", true);
	lookupAlleleByKey = LookupAlleleByKey.getInstance();

	
	logger.logdDebug("Initializing parentStrainLookupByParentKey", true);
	parentStrainLookupByParentKey = 
		new ParentStrainLookupByParentKey();

	logger.logdDebug("Initializing strainKeyLookup", true);
	strainKeyLookup = new StrainKeyLookup();

	logger.logdDebug("Initializing lookupVectorKeyByTerm", true);
	lookupVectorKeyByTerm = new LookupVectorKeyByTerm();

	logger.logdDebug("Initializing lookupMarkerByMGIID", true);
	lookupMarkerByMGIID = LookupMarkerByMGIID.getInstance();

	logger.logdDebug("Initializing vocTermLookup", true);
	vocTermLookup = new VocabTermLookup();

	logger.logdDebug("Initializing cellLineNameLookupByKey", true);
	cellLineNameLookupByKey = new CellLineNameLookupByKey();

	logger.logdDebug("Initializing lookupStrainKeyByCellLineKey", true);
	lookupStrainKeyByCellLineKey = new LookupStrainKeyByCellLineKey();

	logger.logdDebug("Initializing strainNameLookup", true);
	strainNameLookup = new StrainNameLookup();

	alleleFactory = KnockoutAlleleFactory.getFactory();

	logger.logdDebug("Filtering project IDs", true);
	filterProjectIds(databaseProjectIds);
	//logger.logdInfo("databaseProjectsIds:", true);
	//logger.logdInfo(databaseProjectIds.toString(), true);

	logger.logdDebug("Filtering cell lines", true);
	filterCellLines(databaseCellLines);

	logger.logInfo("Reading input files");
	logger.logpInfo("Processing " + cfg.getPipeline(), false);

	InputDataFile inputFile = new InputDataFile(cfg);

	// Get an appropriate Interpreter for the file
	interp = alleleFactory.getInterpreter();

	// Get an Iterator for going through the input file
	iter = inputFile.getIterator(interp);

	// Get an appropriate Processor for the records in the file
	processor = alleleFactory.getProcessor();

	logger.logdInfo("Finished initializing Targeted allele load\n", true);
}


    /**
     * Filter out inappropriate project IDs based on pipeline and provider.
     * Projects for other pipeline don't get QCed during this execution
     * 
     * @param databaseProjectIds
     *            set of all project IDs to be QCed
     * @throws DBException
     *             if the lookup fails
     * @throws CacheException
     *             if the lookup fails
     */
    private void filterProjectIds(Set databaseProjectIds)
    throws MGIException {

	logger.logdInfo("Building project ID filter\n", true);
	// sc - for eucomm hmgu: "(EUCOMM)Hmgu"
	String loadProvider = 
	    "(" + cfg.getPipeline() + ")" + cfg.getProviderLabcode();

	Iterator it = lookupAllelesByProjectId.getKeySet().iterator();
	// sc - 'label is PID'
	while (it.hasNext()) {
	    String label = (String) it.next();
	    // sc note:
	    // Map a -  {symbol1:alleleSet, symbol2:alleleSet, ...}
	    // alleleSet is a set of  HashMaps - 
	    //        {"projectID":String, "key":Integer, "symbol":String, 
	    // 		"parentCellLine":String, "parentCellLineKey":Integer,
	    // 		"mutantCellLines":ArrayList}
	    // where key is allele key, symbol is allele symbol, mutantCellLines is 
	    //         ArrayList of String   MCL IDs	
	    Map a = lookupAllelesByProjectId.lookup(label);

	    // All alleles in the project belong to the same
	    // pipeline/provider combination, so just look at the first one
	    // sc 6/12 - not true due to Orphans - need to find a non-orphan
	    //Map b = (Map) a.values().toArray()[0];
	

	    Collection b = a.values();

	    // new code, check all alleles for correct loadProvider - 
	    // pid can get added more than once, but databseProjectIds is a Set
	    Iterator bIt = b.iterator();
	    while (bIt.hasNext()) {
		Map all = (Map)bIt.next();
		// If the allele belongs to the same combination of pipeline
		// and provider, then add it to the QC check pool
		if (((String) all.get("symbol")).indexOf(loadProvider) >= 0) {
		    databaseProjectIds.add(label);
		}
	    }
	}
	logger.logdInfo("Finished building project ID filter\n", true);

    }

    /**
     * Filter out inappropriate cell lines based on pipeline and provider.
     * Projects for other pipeline don't get QCed during this execution
     * 
     * @param databaseCellLines
     *            set of all cell lines to be QCed
     * @throws DBException
     *             if the lookup fails
     * @throws CacheException
     *             if the lookup fails
     */
    private void filterCellLines(Set databaseCellLines)
    throws MGIException 
    {

	logger.logdInfo("Building celllines filter\n", true);

	// Add only cell lines appropriate for this pipeline and provider
	// to the QC pool (cell lines for other pipeline don't need QC
	// during this run)
	String [] jnumbers = cfg.getJNumbers();

	// 5/16/13 - sc NOTE: jnumbers[0] parameter NOT USED BY LOOKUP -
	// LOOKUP ALWAYS uses the primary JNUM (first one in the configured
	// string)
	LookupCelllinesByJnumber lookupCelllinesByJnumber = 
	    new LookupCelllinesByJnumber(sqlDBMgr, jnumbers[0]);
	
	while (lookupCelllinesByJnumber.hasNext()) {
	    String cellline = lookupCelllinesByJnumber.next();
	    databaseCellLines.add(cellline.toLowerCase());
	}

	logger.logdInfo("Finished building celllines filter\n", true);
    
    }

    /**
     * 
     *  Initialize QC stats 
     * 
     * 
     * @assumes nothing
     * @effects nothing
     * @throws nothing
     */
    protected void preprocess() throws MGIException {

	logger.logdInfo("Preprocessing Targeted allele load\n", true);

	// Initialize the statistics for alleles and cell lines created
	// by the load so far
	qcStats.record("SUMMARY", NUM_ALLELES_CREATED, 0);
	qcStats.record("SUMMARY", NUM_CELLLINES_CREATED, 0);

	// Build a vector that contains a Table object for each table to be
	// written to in the "load" database.
	//
	Vector loadTables = new Vector();
        loadTables.add(Table.getInstance("ALL_CellLine_Derivation", loadDBMgr));
        loadTables.add(Table.getInstance("ALL_CellLine", loadDBMgr));
        loadTables.add(Table.getInstance("ALL_Allele", loadDBMgr));
        loadTables.add(Table.getInstance("ALL_Allele_CellLine", loadDBMgr));
        loadTables.add(Table.getInstance("ALL_Allele_Mutation", loadDBMgr));
	loadTables.add(Table.getInstance("ACC_Accession", loadDBMgr));
	loadTables.add(Table.getInstance("VOC_Annot", loadDBMgr));
        loadTables.add(Table.getInstance("MGI_Note", loadDBMgr));
        loadTables.add(Table.getInstance("MGI_NoteChunk", loadDBMgr));
        loadTables.add(Table.getInstance("MGI_Reference_Assoc", loadDBMgr));

	// Initialize writers for each table if a BCP stream if being used.
	//                                                              
	if (loadStream.isBCP())
	    ((BCP_Stream)loadStream).initBCPWriters(loadTables);
	logger.logdInfo("Finished preprocessing Targeted allele load\n", true);

    }

    /**
     * read the knockout allele input file and run the process that creates new
     * alleles in MGD
     * 
     * @assumes nothing
     * @effects the data will be created for loading Alleles and associated ES
     *          Cell lines into the database
     * @throws MGIException
     *             thrown if there is an error accessing the input file or
     *             writing output data
     */
    protected void run() throws MGIException {

	logger.logdInfo("Running Targeted allele load\n", true);

	// Keep track of which alleles we've updated the notes for
	// so we only update it once
	Set alreadyProcessed = new HashSet();
	
	int numberOfCelllinesToCheck = databaseCellLines.size();
	int numberChecked = 0;

	// For each input record
	while (iter.hasNext()) {
	    // Instance the input records
	    KnockoutAlleleInput in = null;

	    try {
		in = (KnockoutAlleleInput) iter.next();
	    } catch (MGIException e) {
		logger.logdInfo(e.toString(), true);
		qcStats.record("WARNING", NUM_BAD_INPUT_REC);
		continue;
	    }

	    // Keep track of the projects and mutant cell lines we've already
	    // seen
	   
	    databaseProjectIds.remove(in.getProjectId().toLowerCase());
	    databaseCellLines.remove(in.getMutantCellLine().toLowerCase());

	    // If this record is not appropriate to be handled by this
	    // processor, skip it. The only reason we included it in the
	    // first place was to assist in the QC of all cell lines
	    // sc - N2MO I removed this because interpreter already filters this out
	    //if (!in.getInputPipeline().equals(cfg.getPipeline())) {
	    //	continue;
	    //}

	    if (alreadyProcessed.contains(in.getMutantCellLine())) {
		String m = "Multiple input records for: ";
		m += in.getMutantCellLine() + "\n";
		logger.logdInfo(m, false);
		qcStats.record("WARNING", NUM_DUPLICATE_INPUT_REC);
		continue;
	    } else {
		alreadyProcessed.add(in.getMutantCellLine());
	    }

	    if (in.getParentCellLine().equals("")
		    || in.getParentCellLine().equals("-")
		    || in.getParentCellLine().equals("[ENTERYOURDATAVALUE]")) {
		qcStats.record("ERROR", NUM_MISSING_PARENT);

		String m = "Missing parental cell line, skipping record: "
		    + in.getMutantCellLine() + " (parental: "
		    + in.getParentCellLine() + ")\n";
		logger.logdInfo(m, false);
		continue;
	    }

	    // sc - Log and skip if the marker is secondary - this lookup only includes preferred IDs
	    Marker mrk = null;
	    try {
		mrk = lookupMarkerByMGIID.lookup(in.getGeneId());
	    } catch (Exception e) {
		//pass
	    }

	    if (mrk == null) {
		qcStats.record("ERROR", BAD_MARKER_ID);

		String m = "Invalid/Secondary Marker ID; Cell Line is associated to Marker : skipping record\n" +
			in.toString() + "\n";
		logger.logcInfo(m, false);
		continue;
		    
	    }
	    //  sc - log and skip if marker has been withdrawn
	    if (mrk.getStatusKey().equals(Constants.MARKER_WITHDRAWN)) {
		qcStats.record("ERROR", NUM_WITHDRAWN_MARKER);

		String m = "Marker " +
		    in.getGeneId() +
		    " has been withdrawn, but cell line " +
		    in.getMutantCellLine() +
		    " is associated to it, skipping record\n";
		logger.logcInfo(m, false);
		continue;
	    }

	    
	    // Construct the allele from the input record
	    KnockoutAllele constructed = null;

	    try {
		constructed = processor.process(in);
	    } catch (KeyNotFoundException e) {
		qcStats.record("ERROR", NUM_BAD_ALLELE_PROCESSING);

		String m = "Could not create allele (bad key), check: "
				+ in.getMutantCellLine() + "\n" + in + "\n";
		logger.logdInfo(m, false);
		m = "Could not process the input record for: "
		    + in.getMutantCellLine() + "\n"
		    + "The provider might be using a secondary MGI ID ("
		    + in.getGeneId() + ")\n";
		logger.logcInfo(m, false);
		continue;
	    } catch (MGIException e) {
		qcStats.record("ERROR", NUM_BAD_ALLELE_PROCESSING);

		String m = "Could not process, skipping record: "
		    + in.getMutantCellLine() + "\n" + in + "\n";
		try {
		    // Just the first message of the exception needs
		    // to be reported
		    BufferedReader reader = new BufferedReader(
				    new StringReader(e.getMessage()));
		    m += reader.readLine();
		} catch (IOException e1) {
		    m = "Could not process "
			+ in.getMutantCellLine()
			+ " then something bad happened trying to report what happened.";
		}

		logger.logdInfo(m, false);
		continue;
	    }
	    // not sure if this can happen because exception caught above and continue
	    if (constructed == null) {
		qcStats.record("ERROR", NUM_BAD_ALLELE_PROCESSING);

		String m = "Allele creation error, check: ";
		m += in.getMutantCellLine();
		logger.logdInfo(m, false);
		continue;
	    }

	    // Does the Mutant Cell Line record exist in the
	    // cache (database or recently created)?
	    // sc - don't understand how it could be in the cache if dups are weeded out already ...
	    // sc - I think this simply lets us know if the MCL is in the database
	    MutantCellLine esCell = lookupMutantCelllineByName.lookup(in
		.getMutantCellLine());

	    if (numberChecked % 1000 == 0) {
		String m = "Processed " + numberChecked + " " +
		    "celllines (of " + numberOfCelllinesToCheck + ")\n";
		logger.logdInfo(m, true);
	    }
	    numberChecked++;

	    // Update mode or create mode
	    if (cfg.getUpdateOnlyMode()) {
		if (esCell != null) {
		    // Mutant ES Cell found in MGI, check the associated allele

		    // Find the existing associated allele
		    KnockoutAllele existing = lookupAlleleByCellLine.lookup(in
				    .getMutantCellLine());
		    
		    if (existing == null) {
			    String m = "Missing allele for cell line in lookup " + in.getMutantCellLine() + "\n";
			    logger.logdInfo(m, true);
			    continue;
		    }
		    
		    // Replace it with the object by Key lookup
		    // because the allele might include jnumber 
		    // association updates, but this cell line
		    // doesn't know about those
		    existing = lookupAlleleByKey.lookup(existing.getKey());

		    // If the associated allele can't be found, there's a major
		    // problem. The caches are out of synch, or the cell line to
		    // allele association is missing. Regardless, we can't
		    // process this record further, report the error and
		    // continue
		    if (existing == null) {
			    // Report this to the diagnostic log
			    String m = LOG_ALLELE_NOT_FOUND.replaceAll(
					    "~~INPUT_MCL~~", in.getMutantCellLine());
			    logger.logdInfo(m, true);
			    qcStats.record("ERROR", NUM_CELLINES_MISSING_ALLELE);
			    continue;
		    }

		    // ********************************************************
		    // Ensure that all cfg references are associated to
		    // this allele
		    // ********************************************************
		    existing.normalizeReferences(loadStream);

		    // ********************************************************
		    // BEGIN QC CHECKS
		    // ********************************************************

		    try {
			if (!isMatchingGene(existing, constructed)) {
			    // Check the allele to marker association, if it has
			    // changed,
			    // report to the log for manual curation.
			    logMarkerChanged(in, constructed, existing);
			} else if (alleleProjects.get(existing.getKey()) != null
				|| (!existing.getProjectId().equals(
				    constructed.getProjectId())
				    && !isTypeChange(existing, constructed)
				    && !isGroupChange(existing, constructed)
				    && !isCreatorChange(existing, constructed) && !isDerivationChange(
				    esCell, in))) {
			    // We can only update the project ID once all cell lines
			    // for the allele have been
			    // verified to require the same change

			    // The extra "alleleProjects.get(existing) != null ||"
			    // check is to see if there are project IDs that have
			    // NOT
			    // changed from the original, when others have. The
			    // existence of an entry in alleleProjects means we've
			    // updated this project ID before.

			    // The project ID changed, but the type, group,
			    // creator, derivation and marker didn't change, we
			    // can just try to update the allele project ID
			    // in place

			    if (alleleProjects.get(existing.getKey()) == null) {
				alleleProjects
				    .put(existing.getKey(), new HashSet());
		}

			    // Record the updated project ID for this allele symbol
			    Set projSet = (Set) alleleProjects.get(existing
				.getKey());
			    projSet.add(constructed.getProjectId());
			    alleleProjects.put(existing.getKey(), projSet);

			    // save the new project ID for this allele symbol
			    String m = existing.getSymbol() + "\t"
				+ existing.getProjectId() + "\t"
				+ constructed.getProjectId() + "\t"
				+ in.getMutantCellLine();
			    alleleProjectIdUpdated.add(m);

			} else if (!existing.getSymbol().equals(
				constructed.getSymbol())) {
			    // If the associated allele symbol has changed at all,
			    // then we need to change it and update the derivation

			    // Symbols don't match
			    // The marker didn't change (checked previously)
			    // so one of these attributes changed.
			    // 1- Parental cell line
			    // 2- Type
			    // 3- IKMC group
			    // 4- Creator
			    // 5- Vector

			    if (isTypeChange(existing, constructed)) {
				logTypeChange(in, constructed, existing);
			    }

			    if (isGroupChange(existing, constructed)) {
				logGroupChange(in, constructed, existing);
			    }

			    if (isCreatorChange(existing, constructed)) {
				logCreatorChange(in, constructed, existing);
			    }

			    if (isNumberChange(existing, constructed)) {
				logNumberChange(in, constructed, existing);
			    }

			    // Re-associate the cell line to a new allele
			    logAlleleChanged(in, constructed, esCell, existing);
			    changeMutantCellLineAssociation(in, esCell, existing,
				constructed);

			} else if (!esCell.getDerivationKey().equals(
				getDerivationKey(in))) {
			    // Check the derivation (this implicitly checks the
			    // parental cell line, the creator, the vector and the
			    // allele type)

			    // This QC check is a street sweeper. Derivation
			    // changes, by themselves, should not happen.
			    // If ONLY the derivation changed and nothing else
			    // (which was checked previously in the if-then)
			    // then we can go ahead and change the derivation
			    // association, but we will skip updating the note
			    // if it also changed.

			    logDerivationChange(in, esCell, existing);
			    changeDerivationKey(getDerivationKey(in), esCell);

			} else {

			    // Compress the note fields to discount any extra spaces
			    // that might have snuck in
			    String existingNote = existing.getNote()
				.replaceAll("\\n", "").replaceAll(" ", "");
			    String constructedNote = constructed.getNote()
				.replaceAll("\\n", "").replaceAll(" ", "");

			    // The extra "|| alleleNotes.get(existing) != null"
			    // check is to see if there are notes that have NOT
			    // changed from the original, when others have. The
			    // existence of an entry in alleleNotes means we've
			    // updated this note before.
			    if (!existingNote.equals(constructedNote)
				    || alleleNotes.get(existing.getKey()) != null) {
				// If we get this far in the QC checks, then
				// we can be sure that the creator, the type,
				// the vector, and the parental cell line are all
				// the same. The only thing left that could have
				// changed are the coordinates

				if (alleleNotes.get(existing.getKey()) == null) {
					alleleNotes.put(existing.getKey(),
							new HashSet());
				}

				// save the new molecular note for this allele
				// symbol
				Set notes = (Set) alleleNotes
						.get(existing.getKey());
				notes.add(constructed.getNote());
				alleleNotes.put(existing.getKey(), notes);
			    }
			}

		    } catch (MGIException e) {
			    logger.logdInfo(e.getMessage(), false);
		    }

		    // ********************************************************
		    // END QC CHECKS
		    // ********************************************************
		    // done with QC checks. skip on to the next record
		    qcStats.record("SUMMARY", NUM_CELLLINES_PASSED_QC);

		    continue;

		} // end of if (esCell != null)
	    // end of if (cfg.getUpdateOnlyMode())
	    } else { 

		// Only create anything if the cell line doesn't exist
		// the QC checking process (which occurs if this load
		// is run when the cfg.getUpdateOnlyMode() is true)

		// If the cell line was not found in the database,
		// create a new cell line and associated objects
		if (esCell == null) {
		    Integer mclKey = null;
		    try {
			mclKey = createMutantCellLine(in, false);
		    } catch (MGIException e) {
			qcStats.record("ERROR", NUM_BAD_CELLLINE_PROCESSING);
			String m = "Could not create mutant cell line, "
			    + "skipping record: " + in.getMutantCellLine()
			    + "\n" + in + "\n";
			try {
			    // Just the first message of the exception needs
			    // to be reported
			    BufferedReader reader = new BufferedReader(
					    new StringReader(e.getMessage()));
			    m += reader.readLine();
			} catch (IOException e1) {
			    m = "Could not process "
				+ in.getMutantCellLine()
				+ " then something bad happened trying to report what happened.";
			}
			logger.logdInfo(m, false);
			continue;
		    }

		    if (mclKey == null) {
			qcStats.record("ERROR", NUM_BAD_CELLLINE_PROCESSING);
			String m = "Mutant cell line not created, "
			    + "skipping record: " + in.getMutantCellLine()
			    + "\n" + in + "\n";
			logger.logdInfo(m, false);
			continue;
		    }

		    // lookup existing alleles for this project
		    String projectId = in.getProjectId();
		    Map alleles = lookupAllelesByProjectId.lookup(projectId);
		    Integer alleleKey = null;
		    if (alleles != null) {
			// try to get the allele identified by the constructed
			// symbol
			Map allele = (Map) alleles.get(constructed.getSymbol());
			if (allele != null) {
			    // found an allele with this same symbol
			    alleleKey = (Integer) allele.get("key");
			}
		    }

		    if (alleleKey == null) {
			// did not find appropriate allele. create a new allele
			createAllele(constructed, in, alleles);
			alleleKey = constructed.getKey();
		    }

		    // if an appropriate allele cannot be found or created,
		    // report the error and skip on to the next record
		    if (alleleKey == null) {
			String m = "Could not create allele: ";
			m += constructed + "\n";
			m += in + "\n";
			logger.logdInfo(m, false);
			continue;
		    }

		    KnockoutAllele lookedUpAllele = lookupAlleleByKey.lookup(alleleKey);
		    if (lookedUpAllele == null)
		    {   
			// This is fatal.  We should not continue because
			// the caches do not contain the correct set of
			// alleles
			String m = "Cannot find allele for allele key: " + 
			    alleleKey + "\n";
			logger.logcInfo(m, false);
			m += " Check that the logicalDB ("+ cfg.getProjectLogicalDb() +
			    ") is in the LookupAlleleByCellLine cache query";
			logger.logdInfo(m, false);
			throw new MGIException("Invalid configuration. Can't find allele when one certainly exists.");
		    }
		    associateCellLineToAllele(alleleKey, mclKey);
		} // end if (esCell == null) 

	    } // end of if (cfg.getUpdateOnlyMode()) else clause
	} // end while (iter.hasNext())
	
	logger.logdInfo("Finished running Targeted allele load\n", true);
    
    } // end protected void run()

    // Logging helper functions
    private void logMarkerChanged(KnockoutAlleleInput in,
		    KnockoutAllele constructed, KnockoutAllele existing) {
	    String m = LOG_MARKER_CHANGED
			    .replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
			    .replaceAll("~~EXISTING_MARKER~~", existing.getSymbol())
			    .replaceAll("~~INPUT_MARKER~~", constructed.getSymbol());

	    logger.logcInfo(m, false);
	    qcStats.record("SUMMARY", NUM_CELLINES_CHANGED_MARKER);
    }

    private void logDerivationChange(KnockoutAlleleInput in,
	    MutantCellLine esCell, KnockoutAllele existing) throws MGIException {
	String m = LOG_CELLLINE_DERIVATION_CHANGED
	    .replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
	    .replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
	    .replaceAll("~~EXISTING_DERIVATION~~",
		esCell.getDerivationKey().toString())
	    .replaceAll("~~INPUT_DERIVATION~~",
		getDerivationKey(in).toString());
	logger.logcInfo(m, false);
	qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_DERIVATION);
    }

    private void logAlleleChanged(KnockoutAlleleInput in,
	    KnockoutAllele constructed, MutantCellLine esCell,
	    KnockoutAllele existing) throws MGIException {
	String m = LOG_CELLLINE_ALLELE_CHANGED
	    .replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
	    .replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
	    .replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol())
	    .replaceAll("~~EXISTING_DERIVATION~~",
		esCell.getDerivationKey().toString())
	    .replaceAll("~~INPUT_DERIVATION~~",
		getDerivationKey(in).toString());
	logger.logcInfo(m, false);
	qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_DERIVATION);
	qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_ALLELE);
    }

    private void logNumberChange(KnockoutAlleleInput in,
	    KnockoutAllele constructed, KnockoutAllele existing) {
	String m = LOG_CELLLINE_NUMBER_CHANGED
	    .replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
	    .replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
	    .replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol());
	logger.logcInfo(m, false);
	qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_NUMBER);
    }

    private void logCreatorChange(KnockoutAlleleInput in,
	    KnockoutAllele constructed, KnockoutAllele existing) {
	String m = LOG_CELLLINE_CREATOR_CHANGED
	    .replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
	    .replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
	    .replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol());
	logger.logcInfo(m, false);
	qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_CREATOR);
    }

    private void logGroupChange(KnockoutAlleleInput in,
	    KnockoutAllele constructed, KnockoutAllele existing) {
	String m = LOG_CELLLINE_GROUP_CHANGED
	    .replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
	    .replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
	    .replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol());
	logger.logcInfo(m, false);
	qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_PIPELINE);
    }

    private void logTypeChange(KnockoutAlleleInput in,
	    KnockoutAllele constructed, KnockoutAllele existing) {
	String m = LOG_CELLLINE_TYPE_CHANGED
	    .replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
	    .replaceAll("~~EXISTING_SYMBOL~~", existing.getSymbol())
	    .replaceAll("~~INPUT_SYMBOL~~", constructed.getSymbol());
	logger.logcInfo(m, false);
	qcStats.record("SUMMARY", NUM_CELLLINES_CHANGE_TYPE);
    }

    /**
     * Checks if two KnockoutAllele objects have the same creator lab code based
     * on a substring of the symbols. The creator lab code has been included in
     * the allele symbol and is a strong pattern to find (alleleCreatorPattern)
     * 
     * This method short circuits with true (changed) if it cannot find creator
     * for either of the alleles
     * 
     * Example: Xyz&lt;tm1a(KOMP)Wtsi&lt; has a type difference from
     * Xyz&lt;tm1a(KOMP)Ucd&gt;
     * 
     * @param first
     * @param second
     * @return true if both alleles have the same creator, false otherwise
     */
    private boolean isCreatorChange(KnockoutAllele first, KnockoutAllele second) {
	Matcher regexMatcher;
	String firstCreator;
	String secondCreator;

	regexMatcher = alleleCreatorPattern.matcher(first.getSymbol());
	if (regexMatcher.find()) {
	    firstCreator = regexMatcher.group(1);
	} else {
	    return true;
	}

	regexMatcher = alleleCreatorPattern.matcher(second.getSymbol());
	if (regexMatcher.find()) {
	    secondCreator = regexMatcher.group(1);
	} else {
	    return true;
	}

	if (firstCreator.equals(secondCreator)) {
	    // is not different
	    return false;
	}

	// is different
	return true;
    }

    /**
     * Checks if two KnockoutAllele objects have the same sequence number based
     * on a substring of the symbols. The sequence number has been included in
     * the allele symbol as an integer and is a strong pattern to find
     * (alleleSequencePattern)
     * 
     * This method short circuits with true (changed) if it cannot find sequence
     * for either of the alleles
     * 
     * Example: Xyz&lt;tm1a(KOMP)Wtsi&lt; has a type difference from
     * Xyz&lt;tm2a(KOMP)Wtsi&gt;
     * 
     * @param first
     * @param second
     * @return true if both alleles have the same sequence, false otherwise
     */
    private boolean isNumberChange(KnockoutAllele first, KnockoutAllele second) {
	Matcher regexMatcher;
	String firstNumber;
	String secondNumber;

	regexMatcher = alleleSequencePattern.matcher(first.getSymbol());
	if (regexMatcher.find()) {
	    firstNumber = regexMatcher.group(1);
	} else {
	    return true;
	}

	regexMatcher = alleleSequencePattern.matcher(second.getSymbol());
	if (regexMatcher.find()) {
	    secondNumber = regexMatcher.group(1);
	} else {
	    return true;
	}

	if (firstNumber.equals(secondNumber)) {
	    // is not different
	    return false;
	}

	// is different
	return true;
    }

    /**
     * Returns the type of an IKMC allele based on a letter code defined by the
     * International nomenclature committee
     * 
     * @param allele
     * @return the type of allele passed in
     */
    private String getAlleleType(KnockoutAllele allele) {
	Matcher regexMatcher;
	String type;

	regexMatcher = alleleTypePattern.matcher(allele.getSymbol());
	if (regexMatcher.find()) {
	    if (regexMatcher.group(1).equals("a")) {
		type = "Conditional";
	    } else if (regexMatcher.group(1).equals("e")) {
		type = "Targeted non-conditional";
	    } else if (regexMatcher.group(1).equals("")) {
		type = "Deletion";
	    } else {
		type = "Unknown";
	    }
	} else {
	    type = "Deletion";
	}
	return type;
    }

    /**
     * Checks if two KnockoutAllele objects have the same type based on a
     * substring of the symbols. The type has been included in the allele symbol
     * as a letter code (or lacking a letter) and is a strong pattern to find
     * (alleleTypePattern)
     * 
     * Example: Xyz&lt;tm1a(KOMP)Wtsi&lt; has a type difference from
     * Xyz&lt;tm1e(KOMP)Wtsi&gt;
     * 
     * @param first
     * @param second
     * @return true if both alleles have the same type, false otherwise
     */
    private boolean isTypeChange(KnockoutAllele first, KnockoutAllele second) {
	String firstType = getAlleleType(first);
	String secondType = getAlleleType(second);

	if (firstType.equals(secondType)) {
	    // is not different
	    return false;
	}

	// is different
	return true;
    }

    /**
     * Checks if two KnockoutAllele objects have the same IKMC group based on a
     * substring of the symbols. The IKMC group has been included in the allele
     * symbol in parenthesis and there is a strong pattern to find it
     * (pipelinePattern)
     * 
     * This method short circuits with true (changed) if it cannot find an IKMC
     * group for either of the alleles
     * 
     * Example: Xyz&lt;tm1a(KOMP)Wtsi&gt; has a group difference from
     * Xyz&lt;tm1a(EUCOMM)Wtsi&gt;
     * 
     * @param first
     * @param second
     * @return true if different IKMC groups, false if same group
     */
    private boolean isGroupChange(KnockoutAllele first, KnockoutAllele second) {
	String firstIkmcGroup;
	String secondIkmcGroup;
	Matcher regexMatcher;

	regexMatcher = pipelinePattern.matcher(first.getSymbol());
	if (regexMatcher.find()) {
	    firstIkmcGroup = regexMatcher.group(1);
	} else {
	    return true;
	}

	regexMatcher = pipelinePattern.matcher(second.getSymbol());
	if (regexMatcher.find()) {
	    secondIkmcGroup = regexMatcher.group(1);
	} else {
	    return true;
	}

	if (firstIkmcGroup.equals(secondIkmcGroup)) {
	    // is not different
	    return false;
	}

	// is different
	return true;
    }

    /**
     * Checks if an input record has the same derivation as an existing
     * MutantCellLine object
     * 
     * @param esCell
     * @param in
     * @return true if different derivation key, false if same
     */
    private boolean isDerivationChange(MutantCellLine esCell,
	    KnockoutAlleleInput in) throws MGIException {
	Integer key = esCell.getDerivationKey();
	Integer newKey = getDerivationKey(in);
	if (key.equals(newKey)) {
	    // is not different
	    return false;
	}
	// is different
	return true;
    }

    /**
     * Checks if two KnockoutAllele objects belong to the same gene. The gene is
     * identified by gene_key
     * 
     * @param first
     * @param second
     * @return
     */
    private boolean isMatchingGene(KnockoutAllele first, KnockoutAllele second) {
	Integer existingGeneKey = first.getMarkerKey();
	Integer constructedGeneKey = second.getMarkerKey();
	if (existingGeneKey.equals(constructedGeneKey)) {
	    return true;
	}
	return false;
    }

    private Integer getDerivationKey(KnockoutAlleleInput in)
	    throws MGIException {

	// Find the derivation key for this ES Cell
	// The correct derivation is found by combining:
	// * cassette
	// * parental cell line
	// * mutation type
	// * creator
	String cassette = in.getCassette();
	String parent = in.getParentCellLine();
	String aType = in.getMutationType();

	Integer vectorKey = lookupVectorKeyByTerm.lookup(cassette);

	if (vectorKey == null) {
	    throw new MGIException("Cannot find vector for cassette: "
		+ cassette);
	}

	Integer creatorKey = new Integer(cfg.getCreatorKey());
	Integer parentKey;

	try {
	    parentKey = cfg.getParentalKey(parent);
	} catch (ConfigException e) {
	    String s = in.getParentCellLine();
	    s += " Does not exist in CFG file! Skipping record";
	    logger.logdInfo(s, true);
	    qcStats.record("ERROR", NUM_DERIVATIONS_NOT_FOUND);
	    throw new MGIException("Cannot find parental cell line key for "
		+ in.getParentCellLine());
	}
	Integer typeKey = (Integer) Constants.ALLELE_TYPE_KEY;

	String dCompoundKey = vectorKey.toString();
	dCompoundKey += "|" + creatorKey.toString();
	dCompoundKey += "|" + parentKey.toString();
	dCompoundKey += "|" + typeKey.toString();

	Integer derivationKey = derivationLookup.lookup(dCompoundKey);

	if (derivationKey == null) {
	    // CREATE THE NEW DERIVATION AND INSERT IT
	    Derivation d = new Derivation();

	    String creatorName = vocTermLookup.lookup(creatorKey);
	    String typeName = vocTermLookup.lookup(typeKey);
	    String parentName = cellLineNameLookupByKey.lookup(parentKey);
	    String strainName = strainNameLookup
		.lookup(lookupStrainKeyByCellLineKey.lookup(parentKey));

	    // Derivation name is Creator+Type+Parental+Strain+Vector
	    String name = creatorName + " " + typeName + " " + parentName + " "
		+ strainName + " " + cassette;

	    d.setName(name);
	    d.setDescription(null);
	    d.setVectorKey(vectorKey);
	    d.setVectorTypeKey(new Integer(Constants.VECTOR_TYPE_KEY));
	    d.setParentCellLineKey(parentKey);
	    d.setDerivationTypeKey(typeKey);
	    d.setCreatorKey(creatorKey);
	    d.setRefsKey(null);

	    // Inserting a new derivation automatically adds it to the
	    // singleton derivation lookup cache
	    d.insert(loadStream);

	    derivationKey = d.getDerivationKey();

	    String s = "Creating derivation for " + name;
	    qcStats.record("WARNING", s);
	    logger.logdInfo(s, true);
	}

	return derivationKey;
    }

    private void changeDerivationKey(Integer newDerivationKey,
	    MutantCellLine esCell) throws MGIException {
	// Update the derivation key for this cell line
	String query = "UPDATE ALL_Cellline SET ";
	query += "_derivation_key = " + newDerivationKey;
	query += " WHERE _cellline_key = " + esCell.getMCLKey();
	executeQuery(query);
	qcStats.record("WARNING", NUM_CELLLINES_CHANGED_DERIVATION);
    }

    private void changeMutantCellLineAssociation(KnockoutAlleleInput in,
		MutantCellLine esCell, KnockoutAllele oldAllele,
		KnockoutAllele newAllele) throws MGIException {

	// Prevent the cell line from being moved to a different
	// allele if the transmission has changed
	if ((oldAllele.getTransmissionKey().intValue() != Constants.ALLELE_TRANSMISSION_CELLLINE)
		&& !oldAllele.getSymbol().equals(newAllele.getSymbol())) {
	    // Check the allele to marker association, if it has changed,
	    // report to the log for manual curation.
	    String m = LOG_ALLELE_TRANSMISSION_CHANGED
		.replaceAll("~~INPUT_MCL~~", in.getMutantCellLine())
		.replaceAll("~~EXISTING_ALLELE~~", oldAllele.getSymbol())
		.replaceAll("~~CONSTRUCTED_ALLELE~~", newAllele.getSymbol());

	    logger.logcInfo(m, false);
	    qcStats.record("SUMMARY", NUM_ALLELES_CHANGED_TRANS);
	    return;
	}

	// lookupCellLineCountByAlleleSymbol is a lazy cache, so make 
	// sure the Alleles are in the cache before 
	// incrementing/decrementing
	lookupCellLineCountByAlleleSymbol.lookup(oldAllele.getSymbol());
	lookupCellLineCountByAlleleSymbol.lookup(newAllele.getSymbol());

	// Update the count of MCL associated to this allele and create an
	// orphan MCL record if the count drops to 0
	lookupCellLineCountByAlleleSymbol.decrement(oldAllele.getSymbol());
	lookupCellLineCountByAlleleSymbol.increment(newAllele.getSymbol());

	Integer count = lookupCellLineCountByAlleleSymbol.lookup(oldAllele.getSymbol());
	if (count.intValue() < 1) {
	    // the *last* MCL associated to the old allele has been removed
	    // create an orphaned MCL and associate it to the allele
	    createOrphanMCL(esCell, oldAllele);

	    // we just created a "placeholder" MCL to keep the allele
	    // associated to the correct derivation even though the last
	    // "real" MCL migrated elsewhere. increment the MCL counter
	    // There will only ever be either zero or one orphaned MCL
	    // associated to an allele
	    lookupCellLineCountByAlleleSymbol.increment(oldAllele.getSymbol());
	}

	// Change the derivation _after_ the orphan is created...
	// Changing the allele requires that the derivation key changes.
	changeDerivationKey(getDerivationKey(in), esCell);

	// Remove the association existing allele <-> cellline association
	// from the database
	String query = "DELETE FROM ALL_Allele_Cellline";
	query += " WHERE _Allele_key = ";
	query += oldAllele.getKey();
	query += " AND _MutantCellLine_key = ";
	query += esCell.getMCLKey();

	executeQuery(query);

	// Lookup existing alleles for this project
	String projectId = in.getProjectId();
	Map alleles = lookupAllelesByProjectId.lookup(projectId);

	// If there are any alleles for this project, see if one of the
	// existing alleles is the correct one
	if (alleles != null) {
	    // Try to get the allele identified by the new allele symbol
	    HashMap allele = (HashMap) alleles.get(newAllele.getSymbol());

	    // If we found the new allele, attach the MCL to it and return
	    if (allele != null) {
		// Found an allele with this same symbol
		Integer alleleKey = (Integer) allele.get("key");
		associateCellLineToAllele(alleleKey, esCell.getMCLKey());

		// Guarantee that the ES cell logical DB is correct
		updateAccessionLogicalDb(esCell);

		// This may occur multiple times when an orphaned
		// allele is brought back from deleted status. It's
		// okay. This action is idempotent (it can be applied
		// multiple times without changing the result).
		setAlleleApproved(lookupAlleleByKey.lookup(alleleKey));

		return;
	    }
	}

	// Turns out that the cellline didn't match any existing alleles,
	// create a new allele and association the cellline
	createAllele(newAllele, in, alleles);
	associateCellLineToAllele(newAllele.getKey(), esCell.getMCLKey());

	// Guarantee that the ES cell logical DB is correct
	updateAccessionLogicalDb(esCell);

	qcStats.record("SUMMARY", NUM_CELLLINES_CHANGED_ALLELE);

    }

    /**
     * Update the allele status to approved (TR 10492)
     * 
     * @param allele
     *            the allele to update. Must be a KnockoutAllele object
     * @throws ConfigException
     * @throws DBException
     */
    private void setAlleleApproved(KnockoutAllele allele)
	    throws ConfigException, DBException {

	// Only update the status if it is not approved already
	if (allele.getStatus().intValue() != Constants.ALLELE_STATUS_APPROVED) {
	    String q = "UPDATE ALL_Allele SET _Allele_Status_key = "
		+ Constants.ALLELE_STATUS_APPROVED
		+ " WHERE _Allele_key = " + allele.getKey();
	    executeQuery(q);
	}
    }

    /**
     * Update the logical DB of the accession id to match the current pipeline
     * 
     * @param esCell
     *            the esCell to update
     * @throws ConfigException
     * @throws DBException
     */
    private void updateAccessionLogicalDb(MutantCellLine esCell)
	    throws ConfigException, DBException {
	String query;
	query = "UPDATE ACC_Accession SET _LogicalDB_key = ";
	query += cfg.getEsCellLogicalDb();
	query += " WHERE _object_key = " + esCell.getMCLKey();
	query += " AND _MGIType_key = " + Constants.ESCELL_MGITYPE_KEY;
	query += " AND accID = '" + esCell.getCellLine() + "'";

	executeQuery(query);
    }

    private void createOrphanMCL(MutantCellLine esCell, KnockoutAllele oldAllele)
	    throws MGIException {
	// Create the mutant cell line
	MutantCellLine mcl = new MutantCellLine();
	mcl.setCellLine("Orphaned");
	mcl.setCellLineTypeKey(new Integer(Constants.ESCELL_TYPE_KEY));
	mcl.setDerivationKey(esCell.getDerivationKey());
	mcl.setIsMutant(new Boolean(true));
	mcl.setStrainKey(esCell.getStrainKey());
	mcl.setCreationDate(currentTime);
	mcl.setModificationDate(currentTime);
	mcl.setCreatedByKey(cfg.getJobStreamKey());
	mcl.setModifiedByKey(cfg.getJobStreamKey());
	ALL_CellLineDAO mclDAO = new ALL_CellLineDAO(mcl.getState());
	loadStream.insert(mclDAO);

	associateCellLineToAllele(oldAllele.getKey(), mclDAO.getKey().getKey());

	// Set the old allele to deleted status
	String query = "UPDATE ALL_Allele SET _Allele_Status_key = "
	    + Constants.ALLELE_STATUS_DELETED + " WHERE _Allele_key = "
	    + oldAllele.getKey();

	executeQuery(query);

	qcStats.record("WARNING", NUM_ORPHANED_ALLELES);
	logger.logcInfo("Orphaned allele " + oldAllele.getSymbol() + "\n",
	    false);
    }

    private Integer createMutantCellLine(KnockoutAlleleInput in, boolean orphan)
	    throws MGIException {
	Integer derivationKey = getDerivationKey(in);

	// Create the mutant cell line
	MutantCellLine mcl = new MutantCellLine();
	mcl.setCellLine(in.getMutantCellLine());
	mcl.setCellLineTypeKey(new Integer(Constants.ESCELL_TYPE_KEY));
	mcl.setDerivationKey(derivationKey);
	mcl.setIsMutant(new Boolean(true));

	// Get the stain key of the parental cell line
	Integer parentalCellLineKey = cfg
	    .getParentalKey(in.getParentCellLine());
	String strainName = parentStrainLookupByParentKey
	    .lookup(parentalCellLineKey);
	Integer strainKey = strainKeyLookup.lookup(strainName);
	mcl.setStrainKey(strainKey);

	mcl.setCreationDate(currentTime);
	mcl.setModificationDate(currentTime);
	mcl.setCreatedByKey(cfg.getJobStreamKey());
	mcl.setModifiedByKey(cfg.getJobStreamKey());

	// Insert the MCL into the database to get the _CellLine_key
	ALL_CellLineDAO mclDAO = new ALL_CellLineDAO(mcl.getState());
	loadStream.insert(mclDAO);

	// Only record the new accession entry and cached version of this
	// cell line if it is not an orphan MCL
	if (!orphan) {
	    // Add the recently created cell line to the cache
	    lookupMutantCelllineByName.addToCache(in.getMutantCellLine(), mcl);

	    // Create the MutantCellLine Accession object
	    // note the missing AccID parameter which indicates this is
	    // an MGI ID
	    AccessionId mclAccId = new AccessionId(in.getMutantCellLine(), // MCL
		cfg.getEsCellLogicalDb(), // Logical DB
		mclDAO.getKey().getKey(), // MCL object key
		new Integer(Constants.ESCELL_MGITYPE_KEY), // MGI type
		Boolean.FALSE, // Private?
		Boolean.TRUE // Preferred?
	    );
	    mclAccId.insert(loadStream);

	    qcStats.record("SUMMARY", NUM_CELLLINES_CREATED);
	}

	return mclDAO.getKey().getKey();

    }

    // Create the allele to cell line association
    private void associateCellLineToAllele(Integer alleleKey,
		    Integer celllineKey) throws MGIException {
	    ALL_Allele_CellLineState aclState = new ALL_Allele_CellLineState();
	    aclState.setMutantCellLineKey(celllineKey);
	    aclState.setAlleleKey(alleleKey);
	    ALL_Allele_CellLineDAO aclDAO = new ALL_Allele_CellLineDAO(aclState);
	    loadStream.insert(aclDAO);

	    // Update the allele status
    KnockoutAllele lookedUpAllele = lookupAlleleByKey.lookup(alleleKey);
    if (lookedUpAllele == null)
    {   
	    String m = "Cannot find allele for allele key: " + 
			    alleleKey + "\n";
	    logger.logcInfo(m, false);
	    m += " Check that the logicalDB ("+ cfg.getProjectLogicalDb() +
			    ") is in the LookupAlleleByCellLine cache query";
	    logger.logdInfo(m, false);
	return;
    }
	    setAlleleApproved(lookedUpAllele);
    }

    private KnockoutAllele createAllele(KnockoutAllele constructed,
	    KnockoutAlleleInput in, Map alleles) throws MGIException {
	// Persist the constructed allele
	constructed.insert(loadStream);

	// Create the allele lookup with all the data from this input record
	Map allele = new HashMap();
	allele.put("projectid", in.getProjectId());
	allele.put("key", constructed.getKey());
	allele.put("symbol", constructed.getSymbol());
	List mcls = new ArrayList();
	mcls.add(in.getMutantCellLine());
	allele.put("mutantCellLines", mcls);
	allele.put("parentCellLine", in.getParentCellLine());
	allele.put("parentCellLineKey",
			cfg.getParentalKey(in.getParentCellLine()));

	if (alleles == null) {
	    alleles = new HashMap();
	}

	// Include the new allele in the cached allelesByProjectID map
	alleles.put(constructed.getSymbol(), allele);
	lookupAllelesByProjectId.addToCache(in.getProjectId(), alleles);

	// add the newly created allele to the allele cache
	lookupAlleleByKey.addToCache(constructed.getKey(), constructed);

	// add the newly created allele to the alleleByMarker cache
	Marker mrk = lookupMarkerByMGIID.lookup(in.getGeneId());
	String markerSymbol = mrk.getSymbol();

	Set alleleSet = null;
	alleleSet = lookupAllelesByMarker.lookup(markerSymbol);
	if (alleleSet == null) {
	    alleleSet = new HashSet();
	}
	alleleSet.add(constructed.getKey());
	lookupAllelesByMarker.addToCache(markerSymbol, alleleSet);

	qcStats.record("SUMMARY", NUM_ALLELES_CREATED);

	return constructed;
    }

    /**
     * Must close the BCP file stream and commit the new maximum MGI number back
     * to the database. Print out the QC statistics
     * 
     * @assumes nothing
     * @effects Updates the database row in ACC_AccessionMax for MGI IDs
     * @throws MGIException
     *             if something goes wrong
     */
    protected void postprocess() throws MGIException {

	logger.logdInfo("Postprocessing Targeted allele load\n", true);


	// After processing ALL the input records, there is now enough
	// data to determine if the allele level attributes can be changed.
	if (alleleProjects.size() > 0) {
	    // These alleles need to have their project ID updated
	    Iterator projectIt = alleleProjects.entrySet().iterator();
	    while (projectIt.hasNext()) {
		Map.Entry entry = (Map.Entry) projectIt.next();
		Integer key = (Integer) entry.getKey();
		KnockoutAllele existing = lookupAlleleByKey.lookup(key);

		List projects = new ArrayList((Set) entry.getValue());

		if (projects.size() != 1) {
		    logger.logdInfo("Project for " + existing.getSymbol()
					+ " could NOT be updated to " + projects, false);
		} else if (!existing.getProjectId().equals(projects.get(0))) {
		    logger.logdInfo("Project for " + existing.getSymbol()
			+ " updated to " + projects, false);

		    String newProjectId = (String) projects.get(0);

		    String query = "UPDATE ACC_Accession" + " SET accID = '"
			+ newProjectId + "'" + " WHERE _Object_key = "
			+ existing.getKey() + " AND _LogicalDB_key = "
			+ cfg.getProjectLogicalDb()
			+ " AND _MGIType_key = "
			+ Constants.ALLELE_MGI_TYPE + " AND accID = '"
			+ existing.getProjectId() + "'";
		    executeQuery(query);
		}
	    }
	}

	// These alleles need to have their molecular note updated
	if (alleleNotes.size() > 0) {
	    Iterator noteIt = alleleNotes.entrySet().iterator();
	    while (noteIt.hasNext()) {
		Map.Entry entry = (Map.Entry) noteIt.next();
		Integer key = (Integer) entry.getKey();
		KnockoutAllele a = lookupAlleleByKey.lookup(key);
		List notes = new ArrayList((Set) entry.getValue());

		if (notes.size() != 1) {

		    // Multiple notes for this allele!
		    logger.logdInfo("Molecular note for " + a.getSymbol()
					+ " could NOT be updated to:\n" + notes, false);
		} else if (!a.getNote().equals(notes.get(0))) {

		    // The MCLs all agree that there should be a new note
		    logger.logdInfo("Molecular note for " + a.getSymbol()
				    + " updated to:\n" + notes, false);

		    // If a note exists
		    // Delete the existing note
		    if (a.getNoteKey() != null) {
			String query = "DELETE FROM MGI_Note WHERE ";
			query += "_Note_key = ";
			query += a.getNoteKey();

			executeQuery(query);

			// Attach the new note to the existing allele
			a.updateNote(loadStream, (String) notes.get(0));
			qcStats.record("SUMMARY", NUM_ALLELES_NOTE_CHANGE);
		    } else {
			logger.logdInfo("Note key for " + a.getSymbol()
			    + " could NOT be found", false);
		    }
		}
	    }
	}

	// LOG THE RESULTS OF THE LOAD

	TreeMap qc = null;
	Iterator iterator = null;

	// Print out the error statistics
	qc = (TreeMap) qcStats.getStatistics().get("ERROR");

	if (qc != null) {
	    logger.logdInfo("\nERRORS", false);
	    logger.logpInfo("\nERRORS", false);
	    logger.logcInfo("\nERRORS", false);

	    iterator = qc.keySet().iterator();
	    while (iterator.hasNext()) {
		String label = (String) iterator.next();
		logger.logdInfo(label + ": " + qc.get(label), false);
		logger.logpInfo(label + ": " + qc.get(label), false);
		logger.logcInfo(label + ": " + qc.get(label), false);
	    }
	}

	// Print out the warning statistics
	qc = (TreeMap) qcStats.getStatistics().get("WARNING");

	if (qc != null) {
	    logger.logdInfo("\nWARNINGS", false);
	    logger.logpInfo("\nWARNINGS", false);

	    iterator = qc.keySet().iterator();
	    while (iterator.hasNext()) {
		String label = (String) iterator.next();
		logger.logdInfo(label + ": " + qc.get(label), false);
		logger.logpInfo(label + ": " + qc.get(label), false);
	    }
	}

	// Print out the summary statistics
	qc = (TreeMap) qcStats.getStatistics().get("SUMMARY");

	if (qc != null) {
	    logger.logdInfo("\nSUMMARY", false);
	    logger.logpInfo("\nSUMMARY", false);

	    iterator = qc.keySet().iterator();
	    while (iterator.hasNext()) {
		String label = (String) iterator.next();
		logger.logdInfo(label + ": " + qc.get(label), false);
		logger.logpInfo(label + ": " + qc.get(label), false);
	    }
	}

	if (databaseCellLines.size() > 0 || databaseProjectIds.size() > 0
		|| alleleProjectIdUpdated.size() > 0) {
	    logger.logdInfo(
		"Number of project IDs that exist in the MGI database, but not in file: "
		    + databaseProjectIds.size(), false);
	    logger.logpInfo(
		"Number of project IDs that exist in the MGI database, but not in file: "
		    + databaseProjectIds.size(), false);

	    logger.logdInfo(
		"Number of celllines that exist in the MGI database, but not in file: "
		    + databaseCellLines.size(), false);
	    logger.logpInfo(
		"Number of celllines that exist in the MGI database, but not in file: "
		    + databaseCellLines.size(), false);

	    logger.logdInfo("Number of alleles that changed project IDs: "
		+ alleleProjectIdUpdated.size(), false);
	    logger.logpInfo("Number of alleles that changed project IDs: "
		+ alleleProjectIdUpdated.size(), false);

	    logger.logdInfo("\nANOMALIES", false);
	    logger.logcInfo("\nANOMALIES", false);
	}

	if (databaseCellLines.size() > 0) {
	    logger.logdInfo(
		"\nCelllines that exist in the MGI database, but not in the input file: "
		    + databaseCellLines.size(), false);
	    logger.logcInfo(
		"\nCelllines that exist in the MGI database, but not in the input file: "
		    + databaseCellLines.size(), false);

	    logger.logdInfo("\nAllele\tExisting Project\tES Cell Line", false);
	    logger.logcInfo("\nAllele\tExisting Project\tES Cell Line", false);

	    iterator = databaseCellLines.iterator();
	    Set s = new TreeSet();
	    while (iterator.hasNext()) {
		String label = (String) iterator.next();
		KnockoutAllele a = lookupAlleleByCellLine.lookup(label);
		if (a != null) {
		    s.add(a.getSymbol() + "\t" + a.getProjectId() + "\t"
					+ label.toUpperCase());					
		} else {
		    logger.logdInfo("\nCannot find allele for cellline "+label+" in MGD", false);
		}
	    }

	    iterator = s.iterator();
	    while (iterator.hasNext()) {
		String lbl = (String) iterator.next();
		logger.logdInfo(lbl, false);
		logger.logcInfo(lbl, false);
	    }
	}

	if (databaseProjectIds.size() > 0) {
	    logger.logdInfo(
		"\nProject IDs that exist in the MGI database, but not in the input file: "
					    + databaseProjectIds.size(), false);
	    logger.logcInfo(
		"\nProject IDs that exist in the MGI database, but not in the input file: "
		    + databaseProjectIds.size(), false);

	    logger.logdInfo("\nAllele\tExisting Project", false);
	    logger.logcInfo("\nAllele\tExisting Project", false);

	    iterator = databaseProjectIds.iterator();
	    Set s = new TreeSet();
	    while (iterator.hasNext()) {
		String label = (String) iterator.next();
		Map hmA = lookupAllelesByProjectId.lookup(label);
		if (hmA != null) {
		    Set entries = hmA.entrySet();
		    Iterator aIt = entries.iterator();
		    while (aIt.hasNext()) {
			Map.Entry entry = (Map.Entry) aIt.next();
			Map tmpAllele = (Map) entry.getValue();
			s.add((String) tmpAllele.get("symbol") + "\t" + label);
		    }
		}
	    }

	    iterator = s.iterator();
	    while (iterator.hasNext()) {
		String lbl = (String) iterator.next();
		logger.logdInfo(lbl, false);
		logger.logcInfo(lbl, false);
	    }
	}

	if (alleleProjectIdUpdated.size() > 0) {
	    logger.logdInfo("\nAlleles that have had project ID changes: "
		+ alleleProjectIdUpdated.size(), false);
	    logger.logcInfo("\nAlleles that have had project ID changes: "
		+ alleleProjectIdUpdated.size(), false);

	    logger.logdInfo("\nAllele\tExisting Project\tNew Project\tMCL",
		false);
	    logger.logcInfo("\nAllele\tExisting Project\tNew Project\tMCL",
		false);

	    iterator = alleleProjectIdUpdated.iterator();
	    while (iterator.hasNext()) {
		String label = (String) iterator.next();
		logger.logdInfo(label, false);
		logger.logcInfo(label, false);
	    }
	}

	// Empty line to the log files
	logger.logdInfo("\n", false);
	logger.logcInfo("\n", false);

	// Close the database writer
	loadStream.close();

	// If any new MGI IDs have been generated during processing, the
	// ACC_AccessionMax table needs to be updated with the new maximum
	// value.
	AccessionLib.commitAccessionMax();

	logger.logdInfo("Finished postprocessing Targeted allele load\n", true);

	logger.logInfo("Process Finishing");
	return;
    }

    // Helper function to log queries when run in debug mode
    // otherwise execute the query.
    private void executeQuery(String query) throws ConfigException, DBException {
	if (cfg.getPreventBcpExecute()) {
	    logger.logdInfo("SQL prevented by CFG. Would have run: " + query,
				false);
	} else {
	    logger.logdInfo("Ran: " + query, false);
	    sqlDBMgr.executeUpdate(query);
	}
    }

}
