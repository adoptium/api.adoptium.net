#!/bin/bash

set -ev

openssl req -out key.csr -new -newkey rsa:4096 -nodes -keyout private.pem
openssl rsa -in private.pem -pubout > public.pem
