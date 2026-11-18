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

package net.ivoa.calycopis.broker.engine.entities.compute.pool.policy;

import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceCriteria;

/**
 * Accepts requests whose minimum memory is at most a ceiling.
 *
 */
public class MemoryCeilingPolicy
implements PoolPolicy
    {
    public static final String REASON_TYPE = "urn:compute-policy-memory-ceiling";

    private final String name;
    private final long maximumMemory;

    public MemoryCeilingPolicy(
        final String name,
        final long maximumMemory
        ){
        this.name = name;
        this.maximumMemory = maximumMemory;
        }

    @Override
    public String getName()
        {
        return this.name;
        }

    @Override
    public PoolPolicyResult accepts(final SimpleComputeResourceCriteria criteria)
        {
        if (criteria == null)
            {
            return PoolPolicyMessages.decline(
                REASON_TYPE,
                "No request criteria were supplied."
                );
            }
        long memory = criteria.getMemory();
        if (memory > this.maximumMemory)
            {
            return PoolPolicyMessages.decline(
                REASON_TYPE,
                "Request memory [" + memory + "] GiB is above the ["
                    + this.maximumMemory + "] GiB ceiling."
                );
            }
        return PoolPolicyResultBean.accepted();
        }
    }
