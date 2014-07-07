#!/bin/bash

targetrawfolder="/mnt/backup/bic"
rm -rf ${targetrawfolder}

datacenters=(idc mdc)
datapaths=(raw run conf out dim)
dims=(page user event)
dts=(cccdr ccstats cgcdr cgstats mucdr mustats submscdr submsstats huaweicdr)

for path in "${datapaths[@]}"
do
    for dc in "${datacenters[@]}"
    do
        if [ "${path}" == "dim" ]; then
            for dim in "${dims[@]}"
            do
                mkdir -p ${targetrawfolder}/$path/${dc}/${dim}
                currenttime=$(date +%s)
                touch ${targetrawfolder}/${path}/${dc}/${dim}/${currenttime}.dim
            done
        else
            for dt in "${dts[@]}"
            do
                mkdir -p ${targetrawfolder}/${path}/${dc}/${dt}
            done
        fi
    done
done

testsrc=/home/patrick.jiang/opensources/bic2demo/src/test/resources/data
cp ${testsrc}/raw/* /mnt/backup/bic/raw/idc/cccdr/
cp ${testsrc}/raw/* /mnt/backup/bic/raw/mdc/cccdr/
cp ${testsrc}/cccdr.properties /mnt/backup/bic/conf/idc/cccdr/
cp ${testsrc}/cccdr.properties /mnt/backup/bic/conf/mdc/cccdr/

#targetrawfolder="/mnt/backup/bic/raw/${datacenter}/"
#t1="/mnt/backup/bic/raw/idc"
#t2="/mnt/backup/bic/raw/mdc"
#
#rm -rf /mnt/backup/bic
#mkdir -p /mnt/backup/bic/in
#mkdir -p /mnt/backup/bic/out
#mkdir -p /mnt/backup/bic/conf
#mkdir -p /mnt/backup/bic/dim/idc/user
#mkdir -p /mnt/backup/bic/dim/idc/page
#mkdir -p /mnt/backup/bic/dim/mdc/user
#mkdir -p /mnt/backup/bic/dim/mdc/page
#
#datatypes=(cccdr ccstats cgcdr cgstats \
#mucdr mustats submscdr submsstats huaweicdr)
#
#mkdir -p ${targetrawfolder}
#for datatype in "${datatypes[@]}"
#do
#    mkdir -p ${targetrawfolder}${datatype}
#done
#cp -r ${t1} ${t2}
#
##prepare global conf
#psc=/media/d/workspace/bic2demo/src/test/resources/data/
#mkdir -p /mnt/backup/bic/app
#cp ${psc}cccdr.properties /mnt/backup/bic/conf/
#
##prepare raw data
#cp ${psc}raw/* /mnt/backup/bic/raw/idc/cccdr/
#cp ${psc}raw/*.properties /mnt/backup/bic/raw/idc/cccdr/
#touch  /mnt/backup/bic/dim/idc/user/1.dim
#touch  /mnt/backup/bic/dim/idc/page/1.dim
#touch  /mnt/backup/bic/dim/mdc/user/1.dim
#touch  /mnt/backup/bic/dim/mdc/page/1.dim



