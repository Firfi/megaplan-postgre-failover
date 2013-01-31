pg_ctl -D data -l data/postmaster.log promote
function getStatus() {
  Q="select pg_is_in_recovery()::int;"
  status=`psql -c "$Q" -t | sed '/^$/d'`
  cur="select pg_current_xlog_location();"
  receive="select pg_last_xlog_receive_location();"
  replay="select pg_last_xlog_replay_location();"
  c=`psql -c "$cur" -t | sed '/^$/d'`
  rec=`psql -c "$receive" -t | sed '/^$/d'`
  rep=`psql -c "$replay" -t | sed '/^$/d'`
  echo ------
  echo $c
  echo $rec
  echo $rep
}
getStatus
while [ $status == 1 ]; do
  getStatus
done
#function fork() {
#  echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
#  while [ $status == 0 ]; do
#    getStatus
#  done
#}
#fork &
