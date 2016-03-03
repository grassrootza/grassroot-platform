#!/bin/bash
cp -r /var/grassroot /var/grassroot-`date +'%Y-%m-%d-%H-%M-%s'`
rm -rf /var/grassroot/*
rm -f /var/grassroot/.pid

echo "CLEAN"
