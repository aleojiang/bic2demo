#!/bin/bash

datacenter="idc"
targetrawfolder="/mnt/backup/bic/raw/${datacenter}/"
t1="/mnt/backup/bic/raw/idc"
t2="/mnt/backup/bic/raw/mdc"

rm -rf /mnt/backup/bic
mkdir -p /mnt/backup/bic/in
mkdir -p /mnt/backup/bic/out
mkdir -p /mnt/backup/bic/conf
mkdir -p /mnt/backup/bic/dim/idc/user
mkdir -p /mnt/backup/bic/dim/idc/page
mkdir -p /mnt/backup/bic/dim/mdc/user
mkdir -p /mnt/backup/bic/dim/mdc/page

datatypes=(cccdr ccstats cgcdr cgstats \
mucdr mustats submscdr submsstats huaweicdr)

mkdir -p ${targetrawfolder}
for datatype in "${datatypes[@]}"
do
    mkdir -p ${targetrawfolder}${datatype}
done
cp -r ${t1} ${t2}

#prepare global conf
psc=/media/D/workspace/bic2demo/src/test/resources/data/
mkdir -p /mnt/backup/bic/app
cp ${psc}cccdr.properties /mnt/backup/bic/conf/

#prepare raw data
cp ${psc}raw/* /mnt/backup/bic/raw/idc/cccdr/
cp ${psc}raw/*.properties /mnt/backup/bic/raw/idc/cccdr/
touch  /mnt/backup/bic/dim/idc/user/1.dim
touch  /mnt/backup/bic/dim/idc/page/1.dim
touch  /mnt/backup/bic/dim/mdc/user/1.dim
touch  /mnt/backup/bic/dim/mdc/page/1.dim



