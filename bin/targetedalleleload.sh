#!/bin/sh
#
#  targetedalleleload.sh
###########################################################################
#
#  Purpose:  This script controls the execution of the targetedalleleload.
#
  Usage="targetedalleleload.sh config_file"
#      e.g. targetedalleleload.sh tal_regeneron.config
#
#  Env Vars:
#
#      See the configuration file
#
#  Inputs:
#
#      - Common mgiconfig configuration file
#      - Common targeted allele load configuration file
#      - Load specific configuration file - 
#           tal_regeneron.config, tal_csd.config
#      - Load specific input file (defined in the load specific conf file)
#
#  Outputs:
#
#      - Log files defined by the environment variables ${LOG_PROC},
#        ${LOG_DIAG}, ${LOG_CUR} and ${LOG_VAL}
#      - Records written to the database tables
#      - Exceptions written to standard error
#      - Configuration and initialization errors are written to a log file
#        for the shell script
#
#  Exit Codes:
#
#      0:  Successful completion
#      1:  Fatal error occurred
#      2:  Non-fatal error occurred
#
#  Assumes:  Nothing
#
#  Notes:  None
#
###########################################################################

#
#  Set up a log file for the shell script in case there is an error
#  during configuration and initialization.
#
cd `dirname $0`/..
LOG=`pwd`/targetedalleleload.log
rm -f ${LOG}

#
#  Verify the argument(s) to the shell script.
#
if [ $# -ne 1 ]
then
    echo "Usage: ${Usage}" | tee -a ${LOG}
    exit 1
fi

#
#  Establish the configuration file names.
#
CONFIG_LOAD=`pwd`/$1
CONFIG_LOAD_COMMON=`pwd`/tal_common.config

#
#  Make sure the configuration files are readable.
#

if [ ! -r ${CONFIG_LOAD} ]
then
    echo "Cannot read configuration file: ${CONFIG_LOAD}" | tee -a ${LOG}
    exit 1
fi

if [ ! -r ${CONFIG_LOAD_COMMON} ]
then
    echo "Cannot read configuration file: ${CONFIG_LOAD_COMMON}" | tee -a ${LOG}
    exit 1
fi

#
# Source the Targeted Allele Load configuration files - order is important
#
. ${CONFIG_LOAD_COMMON}
. ${CONFIG_LOAD}


#
#  Source the common DLA functions script.
#
if [ "${DLAJOBSTREAMFUNC}" != "" ]
then
    if [ -r ${DLAJOBSTREAMFUNC} ]
    then
        . ${DLAJOBSTREAMFUNC}
    else
        echo "Cannot source DLA functions script: ${DLAJOBSTREAMFUNC}" | tee -a ${LOG}
        exit 1
    fi
else
    echo "Environment variable DLAJOBSTREAMFUNC has not been defined." | tee -a ${LOG}
    exit 1
fi

#
# Set and verify the master configuration file name
#
CONFIG_MASTER=${MGICONFIG}/master.config.sh
if [ ! -r ${CONFIG_MASTER} ]
then
    echo "Cannot read configuration file: ${CONFIG_MASTER}" | tee -a ${LOG}
    exit 1
fi

#
# Copy the input file into place
#
DOWNLOAD=${DOWNLOADFILE_PATH}/${DOWNLOADFILE_NAME}
INPUT=${INPUTDIR}/${DOWNLOADFILE_NAME}
cp ${DOWNLOAD} ${INPUTDIR}

if [ `echo ${DOWNLOAD} | awk -F"." '{ print $NF }'` = "gz" ]
then
    gunzip -f ${INPUT}
fi

#
#  Perform pre-load tasks.
#
preload ${OUTPUTDIR}
#preload

#
#  Run the load application.
#
echo "\n`date`" >> ${LOG_PROC}
echo "Run the targetedalleleLoad application" >> ${LOG_PROC}
${JAVA} ${JAVARUNTIMEOPTS} -classpath ${CLASSPATH} \
        -DCONFIG=${CONFIG_MASTER},${CONFIG_LOAD_COMMON},${CONFIG_LOAD} \
        -DJOBKEY=${JOBKEY} ${DLA_START}
STAT=$?
if [ ${STAT} -ne 0 ]
then
    echo "targetedalleleLoad application failed.  Return status: ${STAT}" >> ${LOG_PROC}
    postload
    exit 1
fi

echo "targetedalleleLoad application completed successfully" >> ${LOG_PROC}

postload

echo "Run the targetedalleleLoad QC report" >> ${LOG_PROC}

${PYTHON} ./bin/QCreport.py

echo "QC report for targetedalleleLoad completed successfully" >> ${LOG_PROC}

exit 0

