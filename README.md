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
      "timestamp": "2026-08-26T13:00:15",
      "name": "Cursor CLI",
      "version": "2026.02.13-41ac335",
      "model": "Claude 4.6 Opus (Thinking)",
      "contribution": {
        "value": 100,
        "units": "%"
        }
      }
    ]
-->

# Calycopis - Execution Broker
A Spring Boot implementation of the IVOA ExecutionBroker service.

This project is named after the <a href="https://en.wikipedia.org/wiki/Calycopis">_Calycopis_</a> genus of butterflies.

<a title="Charles J Sharp [CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0)], via Wikimedia Commons" href="https://commons.wikimedia.org/wiki/File:Trebula_groundstreak_(Calycopis_trebula).jpg"><img width="512" alt="Calycopis Trebula" src="https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/Trebula_groundstreak_%28Calycopis_trebula%29.jpg/440px-Trebula_groundstreak_%28Calycopis_trebula%29.jpg"></a>

Current work on this project is being developed as part of the SKA SRCNet and UKSRC programs.

The broker implements the IVOA ExecutionBroker OpenAPI schema (defined in the
[Calycopis-openapi](https://github.com/ivoa/Calycopis-openapi) project) as a
Spring Boot web application, with a `mock` platform and a `docker` platform that
runs containers via the local Docker/Podman service.

See [AGENTS.md](AGENTS.md) for the project structure, build process, and testing details.

[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.0-4baaaa.svg)](CODE_OF_CONDUCT.md)
