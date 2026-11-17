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

package net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;

/**
 * Creates, selects and changes compute allocations.
 *
 * Unlike the factories for the schema types, this one takes plain values rather
 * than a validator result: an allocation is an admin resource with no Ivoa bean
 * to validate.
 *
 */
public interface SimpleComputeAllocationEntityFactory
    {
    /**
     * Select an allocation by its identifier.
     *
     */
    public Optional<SimpleComputeAllocationEntity> select(
        final UUID uuid
        );

    /**
     * List the allocations of one pool.
     *
     */
    public List<SimpleComputeAllocationEntity> list(
        final AbstractComputeResourcePoolEntity pool
        );

    /**
     * Create and persist an allocation.
     *
     */
    public SimpleComputeAllocationEntity create(
        final AbstractComputeResourcePoolEntity pool,
        final long cores,
        final long memory,
        final Instant start,
        final Instant end
        );

    /**
     * Change the sizes and the window of an allocation, and persist it.
     *
     */
    public SimpleComputeAllocationEntity update(
        final SimpleComputeAllocationEntity entity,
        final long cores,
        final long memory,
        final Instant start,
        final Instant end
        );

    /**
     * Remove an allocation.
     *
     */
    public void delete(
        final SimpleComputeAllocationEntity entity
        );

    /**
     * Whether reserving supply at these values would leave the pool with less
     * capacity than its live reservations in any block.
     *
     * The excluded allocation is the one being changed or removed; the candidate
     * values stand in for it, and zero sizes stand for its removal.
     *
     */
    public boolean wouldOverCommit(
        final UUID poolUuid,
        final UUID excludedUuid,
        final long cores,
        final long memory,
        final Instant start,
        final Instant end
        );

    }
