#!/bin/bash

set -e

if [ -z ${MONGODB_UPDATER_PASSWORD+x} ];
then
  echo "MONGODB_UPDATER_PASSWORD is not set"
  exit 1
fi

if [ -z ${MONGODB_FRONTEND_PASSWORD+x} ];
then
  echo "MONGODB_FRONTEND_PASSWORD is not set"
  exit 1
fi

if [ -z ${MONGODB_ADMIN_PASSWORD+x} ];
then
  echo "MONGODB_ADMIN_PASSWORD is not set"
  exit 1
fi



if [ -z ${MONGODB_UPDATER_USER+x} ];
then
  MONGODB_UPDATER_USER="updater"
fi

if [ -z ${MONGODB_FRONTEND_USER+x} ];
then
  MONGODB_FRONTEND_USER="frontend"
fi

if [ -z ${MONGODB_ADMIN_USER+x} ];
then
  MONGODB_ADMIN_USER="api-admin"
fi

if [ -z ${MONGODB_NAME+x} ];
then
  MONGODB_NAME="api"
fi

mongosh <<EOF
use $MONGODB_NAME

try {
  db.createUser({
    user: '$MONGODB_ADMIN_USER',
    pwd: '$MONGODB_ADMIN_PASSWORD',
    roles: [
      {
        role: 'dbOwner',
        db: '$MONGODB_NAME'
      }
    ]
  })
}
catch (err) {
  db.updateUser('$MONGODB_ADMIN_USER', { pwd: '$MONGODB_ADMIN_PASSWORD' });
}


try {
  db.createUser({
    user: '$MONGODB_UPDATER_USER',
    pwd: '$MONGODB_UPDATER_PASSWORD',
    roles: [
      {
        role: 'readWrite',
        db: '$MONGODB_NAME'
      },
      {
        role: 'clusterMonitor',
        db: 'admin'
      }
    ]
  })
}
catch (err) {
  db.updateUser('$MONGODB_UPDATER_USER', { pwd: '$MONGODB_UPDATER_PASSWORD' });
}


try {
  db.createUser({
    user: '$MONGODB_FRONTEND_USER',
    pwd: '$MONGODB_FRONTEND_PASSWORD',
    roles: [
      {
        role: 'read',
        db: '$MONGODB_NAME'
      }
    ]
  })
}
catch (err) {
  db.updateUser('$MONGODB_FRONTEND_USER', { pwd: '$MONGODB_FRONTEND_PASSWORD' });
}

EOF


