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
      "interval": "2026-10-15T10:30:00/2026-10-15T17:00:00",
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

# The booking contract

What the broker promises about offer start times and availability windows, and
what a client should not assume. This is the broker's behaviour; the OpenAPI
schema defines the shape of the request and response.

## An offer is a reservation

An offer is not a quotation. When the broker returns an offer it has already
reserved the resources for that offer's window, and the reservation is held
until the offer is accepted, rejected or expires. An offer that is never
accepted still holds its capacity while it is `OFFERED`, so a client that
abandons offers delays other clients. Offers expire after a configurable
timeout, 300 seconds by default (`calycopis.broker.timing.session.EXPIRED`).

## The booking grid

The broker books in blocks of 60 seconds. A booking query searches a two hour
range (120 blocks) forwards from the requested start, and compares the free
blocks it finds against the capacity configured for the platform
(`calycopis.broker.booking.capacity`).

That grid is the broker's own. Clients see exact instants and durations, not
block indices.

## The requested start interval

`requestedScheduleBlock.requested.start` is an ISO 8601 interval, and it means
**the window in which the client will accept a start**. `duration` is the
minimum duration the client needs.

* If no start interval is given, the broker uses `[now + prepare, +2h]`, where
  `prepare` is the time the platform needs before the resource is available.
* If the window begins before the broker can start, it is **clamped forward** to
  the earliest start the platform can offer. The rest of the window is honoured,
  and the response carries an informational message.
* If the window **ends** before the broker can start, then no offer can start
  inside it. The response is `result: NO` with no offers, and an error message
  saying so. This is a valid request that this platform cannot meet, not a
  malformed one.

Only starts inside the window are offered. A narrow window combined with a long
duration may therefore produce no offers at all, because a free run long enough
may only exist outside the window.

## Offer start times are best effort

The start of an offer is the earliest time the resource can be prepared and is
free. For an immediate request that is the block containing the current time,
when the capacity is there — the broker no longer rounds up to the next block
boundary ([#154](https://github.com/uksrc/Calycopis-broker/issues/154)).

It is **best effort, not a guarantee**. Capacity is finite, so under contention
only the requests that fit in the current block's remaining capacity start
immediately; later ones are offered the next free run, which is the boundary
they would have been given before. There is no way to promise a zero delay to
everyone at once.

## Availability windows are not block aligned

* The **start** is an exact instant: the requested start, or the request's
  arrival plus the preparation time. It is not necessarily on a minute boundary.
* The **end** is always a block boundary, so the offered duration is usually not
  a whole number of minutes.
* The offered **duration is at least the requested duration**. When the start is
  clamped forward the offer is shortened, so the booking query adds one
  compensation block to the free run it requires.

Do not assume whole minutes, whole blocks, or that the start equals the
requested start.

## The reservation is wider than the availability window

The server holds the resources for longer than `available` shows: preparation
begins before the availability window, and releasing runs after it. A client
that needs to reason about actual occupancy should allow for both. The
availability window is what the offer advertises, not the full extent of the
reservation. See [#158](https://github.com/uksrc/Calycopis-broker/issues/158)
for the work on making that explicit.

## Results and messages

* `result: YES` — at least one offer is included.
* `result: NO` — no offer meets the criteria. The offers list is empty, and the
  response messages explain why.

Messages to look for on the offer set:

| Type | Meaning |
|---|---|
| `urn:start-interval-clamped` | The requested window began before the platform could start, so it was clamped forward. |
| `urn:start-interval-past` | The requested window ended before the platform could start, so no offer can start inside it. |

## Related

* [#156](https://github.com/uksrc/Calycopis-broker/issues/156) — the behaviour this document records
* [#154](https://github.com/uksrc/Calycopis-broker/issues/154) — zero delay start times
* [#158](https://github.com/uksrc/Calycopis-broker/issues/158) — the reservation window across preparing, available and releasing
* [#169](https://github.com/uksrc/Calycopis-broker/issues/169) — booking across resource types
