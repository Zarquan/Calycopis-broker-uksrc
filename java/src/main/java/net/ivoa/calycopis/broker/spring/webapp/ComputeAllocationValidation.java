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

import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Validation shared by the allocation create and update endpoints.
 *
 * The entity enforces the same rules, but a value that is rejected here gets a
 * 400 with a problem body rather than an exception from deeper in the broker.
 *
 */
final class ComputeAllocationValidation
    {

    /**
     * Private constructor, this is a utility class.
     *
     */
    private ComputeAllocationValidation()
        {
        }

    /**
     * Require a positive number of cores.
     *
     */
    static long cores(final Long cores)
        {
        if (cores == null || cores.longValue() <= 0L)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_ALLOCATION,
                "A compute allocation needs a positive number of cores."
                );
            }
        return cores.longValue();
        }

    /**
     * Require a positive amount of memory.
     *
     */
    static long memory(final Long memory)
        {
        if (memory == null || memory.longValue() <= 0L)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_ALLOCATION,
                "A compute allocation needs a positive amount of memory."
                );
            }
        return memory.longValue();
        }

    /**
     * Use now when the request omits the start.
     *
     */
    static Instant start(final Instant start)
        {
        if (start == null)
            {
            return Instant.now();
            }
        return start;
        }

    /**
     * Check an optional end against the start.
     *
     */
    static Instant end(final Instant end, final Instant start)
        {
        if (end != null && !end.isAfter(start))
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_ALLOCATION,
                "A compute allocation must end after it starts."
                );
            }
        return end;
        }

    }
