#!/usr/bin/env sh

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

psql -d postgres -U tanin -a -f ${DIR}/db.sql
