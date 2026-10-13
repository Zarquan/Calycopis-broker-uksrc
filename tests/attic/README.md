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
      along with this program.  If not, see <http://www.gnu.org/licenses/>.
    </meta:licence>
  </meta:header>

  AIMetrics: [
      {
      "timestamp": "2026-10-13T04:35:13",
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

# Retired test material

This directory holds test material that is no longer used. It is kept for
reference only, and is kept out of the way so that it does not confuse visitors
to the project.

## `curl/`

A set of worked examples (numbered `001`–`007` and `example-001`–`example-013`)
that use `curl` to send and receive messages to the broker service. They predate
the current API and schema, many of them are out of date and will not run, and
they must not be used as working examples.

The current tests live in [`tests/python/`](../python/). See the "Testing"
section of [`AGENTS.md`](../../AGENTS.md) for details.
