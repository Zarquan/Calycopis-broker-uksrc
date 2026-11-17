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

package net.ivoa.calycopis.broker.spring.pools;

import java.time.Instant;

/**
 * One allocation as it is written in pools.yaml.
 *
 * The bootstrap turns these into entities. Sizes are positive; an absent start
 * is the epoch, so the allocation has always been available, and an absent end
 * is open ended.
 *
 */
public class ComputeAllocationSpec
    {
    private Long cores;
    private Long memory;
    private Instant start;
    private Instant end;

    public Long getCores()
        {
        return this.cores;
        }

    public void setCores(final Long cores)
        {
        this.cores = cores;
        }

    public Long getMemory()
        {
        return this.memory;
        }

    public void setMemory(final Long memory)
        {
        this.memory = memory;
        }

    public Instant getStart()
        {
        return this.start;
        }

    public void setStart(final Instant start)
        {
        this.start = start;
        }

    public Instant getEnd()
        {
        return this.end;
        }

    public void setEnd(final Instant end)
        {
        this.end = end;
        }

    }
