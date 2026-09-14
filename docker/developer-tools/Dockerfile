#
# Copyright (c) 2024, Manchester (http://www.manchester.ac.uk/)
# All rights reserved.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

ARG buildtag

FROM calycopis/fedora-base:${buildtag}

# https://docs.docker.com/v17.09/engine/reference/builder/#understand-how-arg-and-from-interact
ARG buildtag
ARG buildtime

LABEL maintainer "Dave Morris <docker-admin@metagrid.co.uk>"
LABEL buildtag   "${buildtag}"
LABEL buildtime  "${buildtime}"
LABEL vendor     "UK SKA Regional Centre (UKSRC)"
LABEL version    "${buildtag}"
LABEL license    "GPL-3.0-or-later"
LABEL org.opencontainers.image.created  "${buildtime}"
LABEL org.opencontainers.image.vendor   "UK SKA Regional Centre (UKSRC)"
LABEL org.opencontainers.image.version  "${buildtag}"
LABEL org.opencontainers.image.licenses "GPL-3.0-or-later"
LABEL org.opencontainers.image.source   "https://github.com/ivoa/Calycopis-broker"
LABEL org.opencontainers.image.authors  "https://github.com/Zarquan"

#
# Install Podman
RUN dnf install -y \
    podman

#
# Add our install scripts.
COPY bin /usr/local/bin/
RUN chmod a+x /usr/local/bin/*.sh

#
# Install a specific version of the Java JDK
# Installing the 'latest' caused problems with the Maven compiler plugin.
# https://chat.deepseek.com/share/kbiefowswdwf6tmmv0
RUN dnf install -y \
    java-25-openjdk-devel

#
# Run our Java install script.
RUN /usr/local/bin/install-java-home.sh

#
# Install Python and Pip
RUN dnf install -y \
    python3 \
    python3-pip

#
# Install PyYAML for our OpenAPI processor.
RUN dnf install -y \
    python3-pyyaml

#
# Install PostgreSQL client
RUN dnf install -y \
    postgresql

