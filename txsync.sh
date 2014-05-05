#!/bin/bash
# Sync translation with Transifex
#
# NOTE: This is designed to be invoked by a cron job. Therefore,
# it does things which would be inappropriate to do to a usual working
# copy!

git reset --hard
git pull
tx push -s --no-interactive
tx pull -a --minimum-perc=30
git add res/values-*/*.xml
git commit -m "Automated translation synchronization from Transifex" --author="Transifex Robot <robot@e43.eu>"
git push
