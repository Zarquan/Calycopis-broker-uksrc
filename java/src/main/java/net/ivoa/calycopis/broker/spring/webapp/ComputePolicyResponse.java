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
 *     along with this software. If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-11-18T13:45:00/2026-11-18T17:00:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.webapp;

import java.time.Instant;
import java.util.UUID;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntity;

/**
 * The response body for a compute pool policy.
 *
 */
public record ComputePolicyResponse(
    UUID uuid,
    UUID pool,
    String kind,
    String name,
    Long minimumMemory,
    Long maximumMemory,
    String username,
    Integer startHour,
    Integer endHour,
    Instant created
    )
    {
    /**
     * Map an entity onto the response body.
     *
     */
    public static ComputePolicyResponse of(final SimpleComputePoolPolicyEntity entity)
        {
        return new ComputePolicyResponse(
            entity.getUuid(),
            entity.getPool().getUuid(),
            entity.getKind().getConfigName(),
            entity.getName(),
            entity.getMinimumMemory(),
            entity.getMaximumMemory(),
            entity.getUsername(),
            entity.getStartHour(),
            entity.getEndHour(),
            entity.getCreated()
            );
        }
    }
