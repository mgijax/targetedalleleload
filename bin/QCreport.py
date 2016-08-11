#!/usr/local/bin/python

##########################################################################
#
# Purpose:
#   This script prints a report of all "abandonded" alleles that
#   the recently run targeted allele load may have created 
#
# Usage: QCreport.py
# Env Vars:
#   1. PROJECT_LOGICAL_DB
#   2. RPTDIR
#   
# Inputs:
#   The MGI database 
#       1. allele key
#       2. allele symbol
#       3. count of mutant cell lines (should be 0)
# Outputs:
#   1. rpt file 
#   2. html files
# 
# Exit Codes:
#
#   0:  Successful completion
#
#  Assumes:  Nothing
#
#  Notes:  None
#
###########################################################################

import os
import db

db.setAutoTranslate(False)
db.setAutoTranslateBE(False)

outFilePath = os.environ['RPTDIR'] + "/AbandonedAllele.rpt"
logicaldb = os.environ['PROJECT_LOGICAL_DB']

# column delimiter
colDelim = "\t"
# record delimiter
lineDelim = "\n"

#
# Process
#

# get all alleles grouped by the logical db that have less
# than one attached mutant cell line
results = db.sql("""
SELECT a._Allele_key, a.symbol, a.creation_date, a.modification_date, acc.accid
FROM ACC_Accession acc, ALL_Allele a
WHERE acc._logicaldb_key = %s
AND acc._MGIType_key = 11
AND acc._object_key = a._Allele_key
and not exists (select 1 
from ALL_Allele_CellLine ac
where a._Allele_key = ac._Allele_key)
order by symbol
""" %logicaldb, 'auto')


try:
    outFile = open(outFilePath, 'w')
except:
    exit('Could not open file for writing %s \n' % outFilePath)

outFile.write(colDelim.join(['Allele symbol','Allele key', 'Creation date', 'Modification date', 'Project ID']))
outFile.write(lineDelim)

for r in results:
    outFile.write(  colDelim.join(  [r['symbol'],str(r['_Allele_key']),str(r['creation_date']), str(r['modification_date']), str(r['accid'])]  )  )
    outFile.write(lineDelim)


#
# Post Process
#

outFile.close()
