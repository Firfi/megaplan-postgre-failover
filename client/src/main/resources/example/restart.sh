psql -U postgres -h 127.0.0.1 -d pgbouncer -p 6543 -c 'SHUTDOWN;'
pgbouncer -R -d "$1"
echo "restarted"
