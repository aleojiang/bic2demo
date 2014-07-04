#!/bin/bash

datacenter="idc"
targetrawfolder="/mnt/backup/bic/raw/${datacenter}/"

datatypes=(cccdr ccstats cgcdr cgstats \
mucdr mustats submscdr submsstats huaweicdr)

mkdir -p ${targetrawfolder}
for datatype in "${datatypes[@]}"
do
    mkdir -p ${targetrawfolder}${datatype}
done

