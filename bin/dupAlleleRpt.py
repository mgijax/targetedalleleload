#!/usr/local/bin/python

##########################################################################
#
# Purpose:
#   This script prints a report of all duplicate alleles that
#   the targeted allele loads may have created
#
# Usage: dupAllele.py
# Env Vars:
#   1. RPTDIR
#   
# Inputs:
#   
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

import urllib
import os
import db
import mgi_utils
import loadlib

db.useOneConnection(1)
#print '%s' % mgi_utils.date()

outFilePath = os.environ['BASEDIR'] + "/duplicatedAllele.rpt"

# column delimiter
colDelim = "\t"
# record delimiter
lineDelim = "\n"

TAB= "\t"
userKey = 0
date = loadlib.loaddate


#
# Process
#

# get all duplicated tal load created alleles
results = db.sql("""
    select a._allele_key, a.symbol
    from all_allele a
    where _CreatedBy_key = 1466
    and exists (select 1 from all_allele a2
    where a2._allele_key != a._allele_key
    and a.symbol = a2.symbol) """, 'auto')

try:
    outFile = open(outFilePath, 'w')
except:
    exit('Could not open file for writing %s \n' %  outFilePath)

outFile.write(colDelim.join(['Allele symbol','Allele key']))
outFile.write(lineDelim)

for r in results:
    outFile.write(colDelim.join([r['symbol'],str(r['_Allele_key'])]))
    outFile.write(lineDelim)

#
# Post Process
#

outFile.close()

#print '%s' % mgi_utils.date()
db.useOneConnection(0)
