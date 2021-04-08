# 7. Add Webhook For Update Notification 

Date: 2021-04-08

## Status

Proposed

## Context

Due to polling delays there have been quite long lags between a release being created and the updater registering it. The polling periodically runs and identifies updates
making this polling efficient has led to quite complex updater logic.

## Decision

We will add a webhook that can be called when a release is performed to notify the api of new releases. The endpoint can be placed on the frontend API and then a communications
channel between the frontend and the updater so that it can notify the updater of the new release. A possible technology of this communications channel would be JGroups.
