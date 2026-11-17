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

package net.ivoa.calycopis.broker.engine.entities.compute.allocation;

import java.time.Instant;

import net.ivoa.calycopis.broker.engine.entities.component.Component;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;

/**
 * Capacity added to one compute pool over a window of time.
 *
 * An allocation is supply, not demand: it belongs to a pool, says how many
 * cores and how much memory that pool has from a start instant, and may end at
 * a later instant. An allocation with no end is open ended.
 *
 * The sizes are single values per dimension, unlike the min and max range of a
 * reservation, because an administrator is describing what the pool has rather
 * than negotiating what a session may ask for.
 *
 */
public interface AbstractComputeAllocation
extends Component
    {

    /**
     * The pool this allocation adds capacity to.
     *
     */
    public AbstractComputeResourcePoolEntity getPool();

    /**
     * The instant the allocation starts supplying capacity.
     *
     */
    public Instant getStart();

    /**
     * The instant the allocation stops supplying capacity, or null when it is
     * open ended.
     *
     */
    public Instant getEnd();

    /**
     * The number of CPU cores the allocation supplies.
     *
     */
    public long getCores();

    /**
     * The amount of CPU memory in GiB the allocation supplies.
     *
     */
    public long getMemory();

    }
