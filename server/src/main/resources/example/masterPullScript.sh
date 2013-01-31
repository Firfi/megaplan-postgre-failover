pg_ctl -D data -l data/postmaster.log -m fast stop

ip=$1

function getStatus() {
  Q="select pg_is_in_recovery()::int;"
  status=`psql -h "$ip" -c "$Q" -t | sed '/^$/d'`
}
getStatus
while [ $status == 1 ]; do
  getStatus
done

repmgr -w 16 -D data -F --verbose standby clone "$ip" > backup.log 2>&1
pg_ctl -D data -l data/postmaster.log start
