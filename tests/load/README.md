# Load Test

Run Locust from the Mac or another external machine, not on `47.94.227.58`.

Recommended access is an SSH tunnel to the isolated loadtest Nginx bound on the server loopback:

```bash
ssh -N -L 18080:127.0.0.1:18080 aliyun-procurement
```

Then run:

```bash
cd tests/load
export LOADTEST_USER_PASSWORD='...'
export LOADTEST_ADMIN_PASSWORD="$LOADTEST_USER_PASSWORD"
export LOADTEST_UNIT_PASSWORD="$LOADTEST_USER_PASSWORD"
export LOADTEST_EXPECTED_ENVIRONMENT=loadtest
export LOADTEST_EXPECTED_NAMESPACE=LOADTEST
export LOADTEST_DATABASE_FINGERPRINT='fingerprint-from-/api/v1/system/environment'
locust -f locustfile.py --host http://127.0.0.1:18080
```

The first request made by each user is `/api/v1/system/environment`. If the target is not the isolated loadtest instance, users stop before login or business writes.

Low-risk coexistence stages:

```bash
locust -f locustfile.py --host http://127.0.0.1:18080 --headless -u 1 -r 1 -t 3m --html ../../reports/load/01-user/report.html --csv ../../reports/load/01-user/locust
locust -f locustfile.py --host http://127.0.0.1:18080 --headless -u 5 -r 1 -t 5m --html ../../reports/load/05-users/report.html --csv ../../reports/load/05-users/locust
locust -f locustfile.py --host http://127.0.0.1:18080 --headless -u 10 -r 2 -t 5m --html ../../reports/load/10-users/report.html --csv ../../reports/load/10-users/locust
```

High-load stages must only run after ECS snapshot and maintenance-window confirmation:

```bash
tests/load/run_high_load_phase.sh 20-users 20 2 10m 60
tests/load/run_high_load_phase.sh 30-users 30 3 15m 60
tests/load/run_high_load_phase.sh 30-users-stability 30 3 30m 60
tests/load/run_high_load_phase.sh 40-users-peak 40 4 5m 60
```

Use these defaults for 40-user peak tests:

```bash
export LOADTEST_ADMIN_USER_COUNT=15
export LOADTEST_UNIT_USER_COUNT=40
export LOADTEST_MAX_ORDERS_PER_USER=2
```
