/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (C) 2026 University of Manchester.
 *
 *     This information is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This information is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "timestamp": "2026-10-10T06:27:56",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.platform.docker;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The broker labels applied to every container we launch.
 *
 * The keys use the reserved 'calycopis-broker-' prefix, so a container on the
 * Podman host can be traced back to the broker resource that created it. User
 * defined labels are merged underneath these, and cannot override them.
 *
 */
public final class DockerContainerLabels
    {

    /**
     * The UUID of the execution session that owns the container.
     *
     */
    public static final String SESSION_UID = "calycopis-broker-session-uid";

    /**
     * The UUID of the broker resource that launched the container.
     * This is the compute resource for an execution container, and the
     * data resource for a data download helper container.
     *
     */
    public static final String RESOURCE_UID = "calycopis-broker-resource-uid";

    /**
     * The schema kind URI of the broker resource that launched the container.
     *
     */
    public static final String RESOURCE_KIND = "calycopis-broker-resource-kind";

    /**
     * The role of the container within the broker.
     *
     */
    public static final String CONTAINER_ROLE = "calycopis-broker-container-role";

    /**
     * The role of a container running an execution session.
     *
     */
    public static final String ROLE_EXECUTION = "execution";

    /**
     * The role of a short lived container used to download data.
     *
     */
    public static final String ROLE_DATA_DOWNLOAD = "data-download";

    /**
     * Private constructor, this class only provides static methods.
     *
     */
    private DockerContainerLabels()
        {
        }

    /**
     * Build the broker labels for a container.
     * Values that are null are omitted.
     *
     */
    public static Map<String, String> makeLabels(
        final UUID sessionUuid,
        final UUID resourceUuid,
        final URI resourceKind,
        final String containerRole
        ){
        Map<String, String> labels = new HashMap<String, String>();
        if (sessionUuid != null)
            {
            labels.put(
                SESSION_UID,
                sessionUuid.toString()
                );
            }
        if (resourceUuid != null)
            {
            labels.put(
                RESOURCE_UID,
                resourceUuid.toString()
                );
            }
        if (resourceKind != null)
            {
            labels.put(
                RESOURCE_KIND,
                resourceKind.toString()
                );
            }
        if (containerRole != null)
            {
            labels.put(
                CONTAINER_ROLE,
                containerRole
                );
            }
        return labels;
        }

    }
