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
#   2. WEBSHARE_URL
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
webshare = "%s/getConfig.cgi"%os.environ['WEBSHARE_URL']

# column delimiter
colDelim = "\t"
# record delimiter
lineDelim = "\n"

TAB= "\t"
userKey = 0
date = loadlib.loaddate


wishared = urllib.urlopen(webshare).readlines()
wienv = {}

for line in wishared:
    data = line.strip().split('\t')
    if len(data) > 1:
        wienv[data[0]] = data[1]

#
# Process
#

# get all duplicated tal load created alleles
results = db.sql("""
    SELECT a._Allele_key, a.symbol
    FROM ALL_Allele a
    WHERE _CreatedBy_key = 1466
    group by symbol having count(*) > 1""", 'auto')

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
