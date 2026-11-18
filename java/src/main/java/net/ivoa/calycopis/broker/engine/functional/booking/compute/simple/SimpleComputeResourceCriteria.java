/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
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
 *     "timestamp": "2026-11-18T11:50:00",
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

package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.time.Duration;

import org.threeten.extra.Interval;

import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationValidator;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;

/**
 * The selection criteria for a compute pool booking.
 *
 * The criteria describe the request the pool is being asked to bid for: the
 * parsed request context, the compute resource result, the offer limit and the
 * preparation time the new reservation has to hold. They carry no query
 * handler, so a Stage 6 pool policy can inspect the selection without seeing
 * the functional engine that runs the query.
 *
 */
public interface SimpleComputeResourceCriteria
    {

    /**
     * Get the parsed request context.
     *
     */
    public OfferSetRequestParserContext getRequest();

    /**
     * Get the compute resource being bid for.
     *
     */
    public AbstractComputeReservationValidator.Result getResource();

    /**
     * Get the maximum number of offers the pool may return.
     *
     */
    public int getOfferLimit();

    /**
     * Get how long the new reservation holds the resource before its available
     * window starts.
     *
     */
    public long getPrepareSeconds();

    /**
     * Get the requested start interval.
     *
     */
    default Interval getStartInterval()
        {
        return getRequest().getStartInterval();
        }

    /**
     * Get the requested execution duration.
     *
     */
    default Duration getExecutionDuration()
        {
        return getRequest().getExecutionDuration();
        }

    /**
     * Get the requested total minimum cores.
     *
     */
    default long getMinCores()
        {
        return getRequest().getTotalMinCores();
        }

    /**
     * Get the requested total minimum memory.
     *
     */
    default long getMemory()
        {
        return getRequest().getTotalMinMemory();
        }
    }
