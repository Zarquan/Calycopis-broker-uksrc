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

# https://docs.fedoraproject.org/en-US/fedora/latest/release-notes/
FROM registry.fedoraproject.org/fedora:44

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
# Install Python and Pip
RUN dnf install -y \
    python3 \
    python3-pip

#
# Install JSON, YAML and XML parsers.
RUN dnf install -y \
    jq \
    yq \
    xmlstarlet \
    yamllint

#
# Install our Python test.
COPY . /opt/python-tests

#
# Install our Python requirements.
RUN pip install -r /opt/python-tests/test-requirements.txt

#
# Set our entrypoint script.
RUN chmod a+x /opt/python-tests/bin/run-tests.sh
ENTRYPOINT /opt/python-tests/bin/run-tests.sh

