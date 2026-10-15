<!--
  <meta:header>
    <meta:licence>
      Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)

      This information is free software: you can redistribute it and/or modify
      it under the terms of the GNU General Public License as published by
      the Free Software Foundation, either version 3 of the License, or
      (at your option) any later version.

      This information is distributed in the hope that it will be useful,
      but WITHOUT ANY WARRANTY; without even the implied warranty of
      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
      GNU General Public License for more details.

      You should have received a copy of the GNU General Public License
      along with this software. If not, see <http://www.gnu.org/licenses/>.
    </meta:licence>
  </meta:header>

  AIMetrics: [
      {
      "interval": "2026-10-15T06:20:00/2026-10-15T08:35:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 100,
        "units": "%"
        }
      }
    ]
-->

# The atomicity race in offer generation — test procedure and result

This note records how the atomicity race test was run against a live broker,
what it produced, and the control run that shows the check is sound. It is the
evidence for the test added in
[PR #168](https://github.com/uksrc/Calycopis-broker/pull/168) for
[issue #159](https://github.com/uksrc/Calycopis-broker/issues/159). The test
itself is `tests/python/states/test_atomicity_race.py`.

## What the race is

Offer generation is check-then-act. `SimpleComputeResourceOfferFactoryImpl.generate`
runs the booking query from `OfferSetRequestParserImpl.stageTwo`, but the offer
set and the compute resource's reservation window are only committed by the save
at the end of `OfferSetEntityFactoryImpl.create`. Two requests that overlap
between the query and that save both see the same block as free, so both are
offered it and the capacity is over-subscribed. This is
[issue #156](https://github.com/uksrc/Calycopis-broker/issues/156), question 1.

## The configuration it needs

The race is only observable when the capacity is small enough that a second
overlapping offer is a violation, and the query-to-save window is wide enough
that a second request can reach its own query while the first is inside it:

* `calycopis.broker.booking.capacity.cores: 1` and `.memory: 1`
  ([#157](https://github.com/uksrc/Calycopis-broker/issues/157));
* `calycopis.broker.testing.enabled: true` and
  `calycopis.broker.testing.delays.booking-query-to-save: 5`
  ([#162](https://github.com/uksrc/Calycopis-broker/issues/162)).

This cannot be the configuration the docker suite uses: the suite's stress test
submits `STRESS_COUNT=100` concurrent sessions, so a capacity of 1 breaks it
long before the race test runs. Both files also have to be in place **before the
broker starts**, and the CI harness writes `testing.yaml` after the broker is up.
Wiring a dedicated broker for the race test into CI is
[issue #167](https://github.com/uksrc/Calycopis-broker/issues/167).

## Procedure

The steps follow
[`docs/deployment.md`](../docs/deployment.md#running-the-test-infrastructure-from-an-agent-container),
"Running the test infrastructure from an agent container" and "Running the
broker from source instead". A pod, network, PostgreSQL and the broker from
source, all inside the pod. No images had to be pulled: the `developer-tools`,
`postgres` and `heliophorus-cantliei` images were already in the Podman storage,
and the `calycopis-broker-config`, `calycopis-database-config` and
`calycopis-testdata` volumes already existed.

1. **Pod and network.**

   ```bash
   podman network create calycopis-network
   podman pod create --name calycopis-pod \
       --network calycopis-network --publish 8082:8082
   ```

2. **Service-side paths.** The Podman **service** resolves bind mount sources, so
   the container paths in this session are not the paths to mount. The
   service-side paths were read from this container's own entry in the service's
   view:

   | Mount in this container | Service-side path |
   |---|---|
   | `/Calycopis/Calycopis-broker` | `/home/Zarquan/Projects/IVOA/ivoa/Calycopis/Calycopis-broker/Calycopis-broker-uksrc-zrq` |
   | `/Calycopis/agents/scratch` | `/home/Zarquan/Projects/IVOA/ivoa/Calycopis/agents/scratch` |
   | `/Calycopis/Calycopis-openapi` | `/home/Zarquan/Projects/IVOA/ivoa/Calycopis/Calycopis-openapi/Calycopis-openapi-uksrc-zrq` |

   The socket is `HOST_CONTAINER_PATH`, `/run/user/1000/podman/podman.sock`.

3. **Broker configuration.** `bin/config-broker.sh` was run in a
   `developer-tools` container with the broker configuration volume mounted at
   `/etc/calycopis`, and the race settings were appended to `spring.yaml` and
   written to `testing.yaml` in the same container, so both existed before the
   broker started:

   ```yaml
   # appended to spring.yaml
   calycopis:
       broker:
           booking:
               capacity:
                   cores: 1
                   memory: 1
   ```

   ```yaml
   # testing.yaml
   calycopis:
       broker:
           testing:
               enabled: true
               delays:
                   booking-query-to-save: 5
   ```

   `bin/config-database.sh` then extracted the credentials into the database
   configuration volume, and PostgreSQL was started in the pod and waited for
   with `pg_isready`.

4. **Broker from source.** The documented command, with the source mounted from
   the service-side path, the shared Maven repository at `/m2`, and the socket
   mounted at `/run/podman/podman.sock`:

   ```bash
   podman run \
       --detach \
       --user 0:0 \
       --security-opt label=disable \
       --pod calycopis-pod \
       --name calycopis-broker \
       --volume "${HOST_CODE}:/opt/calycopis:rw" \
       --volume "${HOST_M2}:/m2:rw" \
       --volume "calycopis-broker-config:/etc/calycopis" \
       --volume "${HOST_SOCKET}:/run/podman/podman.sock:rw" \
       --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
       --env "MAVEN_USER_HOME=/m2" \
       --env-file calycopis.vars \
       ghcr.io/ivoa/calycopis/developer-tools:2026.09.17 \
           bash -c '
               set -eo pipefail
               source /opt/calycopis/bin/versions.sh /opt/calycopis/config.yaml
               set -u
               cd /opt/calycopis/java
               exec ./mvnw -o -B -Dmaven.repo.local=/m2/repository spring-boot:run
               '
   ```

   `--security-opt label=disable` is needed on every container in this sequence:
   without it, running `bin/config-broker.sh` from the mounted source failed with
   "Permission denied".

5. **Test container.** Another `developer-tools` container joined to the pod,
   with the source, the built client wheel (`calycopis_openapi_client`) from the
   Openapi clone, the test-data volume and the broker configuration volume
   mounted, and the socket mounted for `bin/config-tests.sh`. Test dependencies
   were installed from pypi, and the client from the local wheel, because the
   `calycopis_openapi_client` requirement resolves through the uksrc package
   index, which returned 404 from this network namespace:

   ```bash
   pip install --quiet pytest "psycopg[binary]" PyYAML
   pip install --quiet /wheel/*.whl
   pytest -q states/test_atomicity_race.py
   ```

   The broker was restarted before each run, because it recreates its schema at
   startup (`spring.jpa.hibernate.ddl-auto: create`) and a live session from an
   earlier run would otherwise occupy the whole search range.

## Results

### The broker confirmed the configuration

`GET /actuator/info` on the live broker:

```json
{"booking":{"capacity":{"cores":1,"memory":1}},
 "testing":{"enabled":true,"booking-query-to-save-delay-seconds":5}}
```

### The race run failed the invariant, as the xfail expects

`pytest -q states/test_atomicity_race.py`, two workers, on a fresh schema:

```text
1 passed, 1 xfailed in 7.44s
```

Running the same test with `--runxfail`, which ignores the marker so the real
failure is visible, produced exactly the invariant assertion and nothing else:

```text
E  AssertionError: The broker reserved 2 cores in one block against a capacity
   of 1 core(s). 2 of 2 concurrent immediate requests were offered a session,
   and their availability windows overlap the same block. This is the in-flight
   atomicity race: the offer set is committed after the booking query, so a
   request that arrives during that window sees the block as free.
E  assert Decimal('2') <= 1
```

The database agrees. Two `OFFERED` sessions, one core each, with **identical**
availability windows:

```text
 phase  |               session                | avail_start | avail_duration | cores
--------+--------------------------------------+-------------+----------------+-------
 OFFERED | 42a5758a-d6d1-4b0d-96e6-dcfdf65ce34b |  1792052414 |           7246 |     1
 OFFERED | e7a76c75-7d91-46d2-8f6d-f92a1df3e809 |  1792052414 |           7246 |     1
```

The delay fired for both offer sets: `SimpleComputeResourceOfferFactoryImpl.generate`
took about 5026 ms in each.

### The control run held the invariant

Submitting the requests one after another (`RACE_SUBMIT_WORKERS=1`) on a fresh
schema, with `--runxfail` because the strict marker would otherwise turn the pass
into a failure:

```text
2 passed in 12.85s
```

The database then held one session with one reserved core, so the second request
was displaced by the first request's committed reservation. The invariant check
therefore passes when the requests are serialised, which is what makes the
failure above attributable to concurrency rather than to the query over-counting.

## What this settles

* The race is **real and reproducible** with capacity 1 and a five second
  query-to-save delay. The decision point in
  [issue #159](https://github.com/uksrc/Calycopis-broker/issues/159) — close the
  question as an assumption if it cannot be reproduced — does not apply, so
  [issue #156](https://github.com/uksrc/Calycopis-broker/issues/156), question 1,
  should be fixed.
* The test's `xfail(strict=True)` is for the expected reason, not an incidental
  error, and the control run shows the invariant check itself is sound.
* The test is still not run in CI, for the reasons in
  [issue #167](https://github.com/uksrc/Calycopis-broker/issues/167).

## Cleanup

The containers were stopped and the pod and network removed. The `testing.yaml`
written for the race was deleted from the broker configuration volume, so a later
run does not inherit `enabled: true`; `spring.yaml` is overwritten by
`bin/config-broker.sh` on the next run. The named configuration, log and test-data
volumes were kept, as the deployment guide describes.

## References

* `tests/python/states/test_atomicity_race.py` — the test
* [`docs/deployment.md`](../docs/deployment.md#running-the-atomicity-race-test) — how to run it
* [#156](https://github.com/uksrc/Calycopis-broker/issues/156) — the race, question 1
* [#159](https://github.com/uksrc/Calycopis-broker/issues/159) — the test
* [#167](https://github.com/uksrc/Calycopis-broker/issues/167) — running it in CI
