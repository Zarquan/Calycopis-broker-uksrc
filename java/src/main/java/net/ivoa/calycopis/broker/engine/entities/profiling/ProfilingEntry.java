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
 *     "timestamp": "2026-10-11T11:15:00",
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
package net.ivoa.calycopis.broker.engine.entities.profiling;

import java.time.Instant;

/**
 * A single timing measurement for one method call.
 *
 */
public interface ProfilingEntry
    {

    /**
     * The fully qualified name of the class being profiled.
     *
     */
    public String getClassName();

    /**
     * The name of the method being profiled.
     *
     */
    public String getMethodName();

    /**
     * The time the profiled method started.
     *
     */
    public Instant getStartTime();

    /**
     * The time the profiled method ended, may be null if it has not ended.
     *
     */
    public Instant getEndTime();

    /**
     * Set the start time to now.
     *
     */
    public void start();

    /**
     * Set the end time to now.
     *
     */
    public void end();

    }
