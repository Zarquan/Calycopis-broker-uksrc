#!/bin/bash
#
# <meta:header>
#   <meta:licence>
#     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
#
#     This information is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This information is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#   </meta:licence>
# </meta:header>
#
#

randomfilename=random.dat
randomfilepath=${CALYCOPIS_TEST_DATA_PATH:?}/${randomfilename:?}

dd if=/dev/urandom of=${randomfilepath:?} bs=1MB count=10
chmod a+r "${randomfilepath:?}"

randomfilemd5sum=$(
    md5sum "${randomfilepath:?}" | awk '{print $1}'
    )

randomfilesha256sum=$(
    sha256sum "${randomfilepath:?}" | awk '{print $1}'
    )

testdatahostpath=$(
    podman volume inspect \
        ${CALYCOPIS_TEST_DATA_VOLUME:?} \
    | jq -r '
        .[0].Mountpoint
        '
    )

cat > "${CALYCOPIS_BROKER_CONFIG_PATH:?}/testing.yaml" << EOF
calycopis:
  broker:
    testing:
      testdata:
        - name: "${randomfilename:?}"
          path: "${randomfilepath:?}"
          location: "file://${testdatahostpath:?}/${randomfilename:?}"
          md5sum: "${randomfilemd5sum:?}"
          sha256sum: "${randomfilesha256sum:?}"

        - name: "apples.jpg"
          location: "http://www.beespace.me/sites/www.beespace.me/files/styles/large/public/field/image/20250824_111206.jpg"
          md5sum: "4e3a7c3bde242d127fbfb925e94c9f32"
          sha256sum: "a58505e353f1533a3e08ec530df820331a1bd9fd1150f779372ba1cecbc24f20"
EOF


