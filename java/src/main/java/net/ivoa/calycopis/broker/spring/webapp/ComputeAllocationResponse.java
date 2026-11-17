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
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
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

import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntity;

/**
 * The response body for a compute allocation.
 *
 * Off-schema for the same reason as the pool bodies: allocations are admin
 * resources until the Calycopis extension is designed.
 *
 * The pool is repeated in every response because the item path identifies the
 * allocation, not the pool. There is no modified timestamp, for the same reason
 * as the pool response: nothing maintains the component's modified field yet.
 *
 */
public record ComputeAllocationResponse(
    UUID uuid,
    UUID pool,
    long cores,
    long memory,
    Instant start,
    Instant end,
    Instant created
    )
    {

    /**
     * Map an entity onto the response body.
     *
     */
    public static ComputeAllocationResponse of(final SimpleComputeAllocationEntity entity)
        {
        return new ComputeAllocationResponse(
            entity.getUuid(),
            entity.getPool().getUuid(),
            entity.getCores(),
            entity.getMemory(),
            entity.getStart(),
            entity.getEnd(),
            entity.getCreated()
            );
        }

    }
