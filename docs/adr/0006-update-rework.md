# 4. Rework Update Mechanism

Date: 2021-04-08

## Status

Proposed

## Context

Currently, we update our data (contained within AdoptRepos) atomically. We build up a view of the state of
all our repos into a AdoptRepos object, then persist the whole thing at once. This has issues, the main one being
that we need to have completely updated ALL Repositories before we can persist the data. This can take a 
long time as we have by now release many assets. To reduce the load we do divide this into two types of
updates incremental and full, full being a full re-pull of all data, and incremental which only looks
for changes. Unfortunately full updates can take a number of hours and due to the data being treated as
atomic we cannot persist new releases found until we have finished updating.

This has led at times to long lag before releases are detected, and fairly complex update logic.

## Decision

Rework how releases are persisted to the datastore. Releases will be created/updated/deleted in the database 
as their state is discovered without waiting for all releases being read. 

In addition to this a communications channel can be set up between the updater and frontend. This channel can be
used to communicate new/updated/deleted releases to the frontend, again sent as and when changes are detected. A 
potential technology for implementing the communications channel is JGroups. 
