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

import os
import db

db.useOneConnection(1)

outFilePath = os.environ['BASEDIR'] + "/duplicatedAllele.rpt"

TAB= '\t'
CRT = '\n'

#
# Process
#

# get all duplicated tal load created alleles
db.sql('''
    select to_char(a.creation_date, 'MM/dd/yyyy') as cdate, a._allele_key, a.symbol, t.term as status
    into temporary table dups
    from all_allele a, VOC_Term t
    where a._CreatedBy_key = 1466
        and a._Allele_Status_key = t._Term_key
    and exists (select 1 from all_allele a2
    where a2._allele_key != a._allele_key
    and a.symbol = a2.symbol)
    order by a.symbol ''', None)

db.sql('create index idx1 on dups(_Allele_key)', None)
results = db.sql('''select d.*, c.cellLine
        from dups d
        left outer join ALL_Allele_CellLine aac on (d._Allele_key = aac._Allele_key)
        left outer join ALL_CellLine c on (aac._MutantCellLine_key = c._CellLine_key)''', 'auto')
try:
    outFile = open(outFilePath, 'w')
except:
    exit('Could not open file for writing %s %s' %  (outFilePath, CRT))

outFile.write(TAB.join(['cellline','allele_status', 'allele_key','symbol','creation_date']))
outFile.write(CRT)

# map the dup allele attributes to their cell lines
resultsDict = {}
for r in results:
    symbol = r['symbol']
    key = r['_Allele_key']
    status = r['status']
    date = r['cdate']
    cellLine = r['cellLine']
    if cellLine == None:
        cellLine = ''
    #resultsDict[key].append(cellLine)
    outFile.write('%s%s%s%s%s%s%s%s%s%s' % (cellLine, TAB, status, TAB, key, TAB, symbol, TAB, date, CRT ))

#
# Post Process
#

outFile.close()

db.useOneConnection(0)
