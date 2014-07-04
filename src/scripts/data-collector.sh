#!/bin/bash
logfile="/tmp/data-collector-err.log"$(date +%Y-%m-%d)
datacenter="mdc"
targetrawfolder="/mnt/backup/bic/raw/${datacenter}/"
targetcompressfloder="/mnt/backup/bic/compressed/"

nodetypes=(cccdr ccstats cgcdr cgstats \
mucdr mustats submscdr submsstats huaweicdr)

cccdrnodes="clcm@srv0,clcm@srv1,clcm@srv3"
cccdrsources="/path/to/cdr1,/path/to/cdr2"

ccstatsnodes="clcm@srv0,clcm@srv1,clcm@srv3"
ccstatssources="/path/to/stats1,/path/to/stats2"

cgcdrnodes="clcm@srv0,clcm@srv1,clcm@srv3"
cgcdrsources="/path/to/cdr1,/path/to/cdr2"

cgstatsnodes="clcm@srv0,clcm@srv1,clcm@srv3"
cgstatssources="/path/to/stats1,/path/to/stats2"

mucdrnodes="clcm@srv0,clcm@srv1,clcm@srv3"
mucdrsources="/path/to/cdr1,/path/to/cdr2"

mustatsnodes="clcm@srv0,clcm@srv1,clcm@srv3"
mustatssources="/path/to/stats1,/path/to/stats2"

submscdrnodes="clcm@srv0,clcm@srv1,clcm@srv3"
submscdrsources="/path/to/cdr1,/path/to/cdr2"

submsstatsnodes="clcm@srv0,clcm@srv1,clcm@srv3"
submsstatssources="/path/to/stats1,/path/to/stats2"

submssnapshotnodes="clcm@srv0,clcm@srv1,clcm@srv3"
submssnapshotsources="/path/to/stats1,/path/to/stats2"

huaweicdrnodes="clcm@srv0,clcm@srv1,clcm@srv3"
huaweicdrsources="/path/to/cdr1,/path/to/cdr2"

for nodetype in "${nodetypes[@]}"
do
    IFS=','
    t11=$(eval echo \$${nodetype}nodes)
    t12=$(eval echo \$${nodetype}sources)
    IFS=' '
    t21=(${t11})
    t22=(${t12})
    for node in "${t21[@]}"
    do
        for src in "${t22[@]}"
        do
            rsync -az -e \"ssh -i /path/to/ssh/pem\" ${node}:${src} ${targetrawfolder}${nodetype} 2>> ${logfile}
            rc=$?
            if [[ rc != 0 ]]; then
                echo "snmptrap polling data..."
            fi
        done
    done
done

compessfilename=${datacenter}-`date +%s%N`
tar -P -J -cf ${targetcompressfloder}${compessfilename}.tar.xz ${targetrawfolder}  2>> ${logfile}
rc=$?
if [[ rc != 0 ]]; then
    echo "snmptrap compressing data..."
fi

rsync -az ${targetcompressfloder} -e \"ssh -i /path/to/ssh/pem\" user@ec2instance:${targetcompressfloder} 2>> ${logfile}
rc=$?
if [[ rc != 0 ]]; then
    echo "snmptrap pushing compressed data..."
fi

#Convert OpenSSH RSA or DSA key to PEM format
#ssh-keygen -t rsa/dsa
#openssl rsa -in ~/.ssh/idrsa/dsa -outform pem > idrsa.pem
