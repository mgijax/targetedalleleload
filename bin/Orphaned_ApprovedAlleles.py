#!/usr/local/bin/python

##########################################################################
#
# Purpose:
#   This script prints a report of all approved alleles
#   associated with orphaned MCLs
#
# Usage: Orphaned_ApprovedAlleles.py
# Env Vars:
#   1. RPTDIR
#   
# Inputs:
#   
# Outputs:
#   1. rpt file 
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

import urllib
import os
import db
import mgi_utils
import loadlib

db.useOneConnection(1)

outFilePath = os.environ['BASEDIR'] + "/Orphaned_ApprovedAlleles.rpt"

# column delimiter
colDelim = "\t"
# record delimiter
lineDelim = "\n"

#
# Process
#

# get all duplicated tal load created alleles
results = db.sql('''select a.accid, aa.symbol
from ALL_Allele aa,  ALL_Allele_CellLine aac,  ALL_CellLine c, ACC_Accession a
where c.cellLine = 'Orphaned' 
and c._CellLine_key = aac._MutantCellLine_key
and aac._Allele_key = aa._Allele_key
and aa._Allele_Status_key = 847114 /* Approved */
and aa._Allele_key = a._Object_key
and a._MGIType_key = 11
and a._LogicalDB_key = 1
and a.prefixPart = 'MGI:'
and a.preferred = 1
order by aa.symbol''', 'auto')

try:
    outFile = open(outFilePath, 'w')
except:
    exit('Could not open file for writing %s \n' %  outFilePath)

outFile.write(colDelim.join(['Allele MGI ID','Allele Symbol']))
outFile.write(lineDelim)

for r in results:
    outFile.write(colDelim.join([r['accid'],r['symbol']]))
    outFile.write(lineDelim)

#
# Post Process
#

outFile.close()

db.useOneConnection(0)
