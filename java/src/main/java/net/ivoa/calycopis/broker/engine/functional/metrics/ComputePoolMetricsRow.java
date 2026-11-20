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
 *     "timestamp": "2026-11-20T17:13:00",
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

package net.ivoa.calycopis.broker.engine.functional.metrics;

import java.util.UUID;

/**
 * One pool's allocated and reserved capacity for the measured block.
 *
 * The memory values use the allocation and reservation column units, which are
 * GiB. The Prometheus publisher converts them to bytes at the metric boundary.
 *
 * Available capacity is derived rather than stored, so there is one definition
 * of the relationship: allocated minus reserved.
 *
 */
public record ComputePoolMetricsRow(
    UUID poolUuid,
    String poolName,
    long allocatedCores,
    long reservedCores,
    long allocatedMemory,
    long reservedMemory
    )
    {
    }
