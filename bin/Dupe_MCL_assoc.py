#!/usr/local/bin/python

##########################################################################
#
# Purpose:
#   This script prints a report of all alleles that have duplicate associations to 
#	the same cell line
#
# Usage "Dupe_MCL_assoc.py
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

import os
import db
import string

db.setAutoTranslate(False)
db.setAutoTranslateBE(False)

db.useOneConnection(1)

outFilePath = os.environ['BASEDIR'] + "/Dupe_MCL_assoc.rpt"

# column delimiter
TAB = "\t"
# record delimiter
CRT = "\n"

#
# Process
#

# get all duplicated tal load created alleles
db.sql('''select _Allele_key, _MutantCellLine_key
    into temporary table dups
    from ALL_Allele_CellLine
    group by _Allele_key, _MutantCellLine_key
    having count(*) >1''', None)

db.sql('''create index idx1 on dups(_Allele_key)''', None)
db.sql('''create index idx2 on dups(_MutantCellLine_key)''', None)

results = db.sql('''select aa.symbol, aa._Allele_key, c.cellLine, c._CellLine_key, aac.creation_date, u.login
    from ALL_Allele_CellLine aac, dups d, ALL_Allele aa,
	ALL_CellLine c, MGI_User u
    where aac._MutantCellLine_key = d._MutantCellLine_key
    and aac._Allele_key = d._Allele_key
    and aac._Allele_key = aa._Allele_key
    and aac._MutantCellLine_key = c._CellLine_key 
    and aac._createdBy_key = u._User_key
    order by aa.symbol''', 'auto')

# {key: count, ...}
dupDict = {}
# {key: r, ...}
lineDict = {}
for r in results:
    key = '%s|%s' % (r['symbol'], r['cellLine'])
    if not dupDict.has_key(key):
	dupDict[key] = 1
	lineDict[key] = [r]
    else:
	dupDict[key] += 1
	lineDict[key].append(r)
try:
    outFile = open(outFilePath, 'w')
except:
    exit('Could not open file for writing %s \n' %  outFilePath)

outFile.write(TAB.join(['celllline','mutantcellline_key', 'dup_count', 'allele_key', 'symbol', 'assoc_creationdate', 'assoc_createdby' ]))
outFile.write(CRT)

for key in dupDict:
    count = dupDict[key]
    resultList = lineDict[key]
    for r in resultList:
	outFile.write('%s%s%s%s%s%s%s%s%s%s%s%s%s%s' % (r['cellLine'], TAB, r['_CellLine_key'], TAB, count, TAB, r['_Allele_key'], TAB, r['symbol'], TAB, r['creation_date'], TAB, r['login'], CRT))

#
# Post Process
#

outFile.close()

db.useOneConnection(0)
