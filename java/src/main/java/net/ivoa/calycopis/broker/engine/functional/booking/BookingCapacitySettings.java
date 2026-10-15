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
 *     "interval": "2026-10-15T05:23:00/2026-10-15T05:27:00",
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

package net.ivoa.calycopis.broker.engine.functional.booking;

/**
 * Framework-neutral interface for the booking capacity configuration.
 *
 * The booking query compares the requests it is given against the total amount
 * of compute resource the platform provides, and subtracts the resources
 * reserved by live sessions to work out what is free in each block of time.
 * This interface supplies that total, so a deployment can describe its real
 * capacity without a code change.
 *
 * The capacity is a number of CPU cores and a number of GiB of memory, bound
 * from the calycopis.broker.booking.capacity section of the application
 * configuration.
 *
 */
public interface BookingCapacitySettings
    {

    /**
     * The default total number of CPU cores, used when the booking query is
     * built without an explicit capacity.
     * Set to 256 cores.
     *
     */
    public static final long DEFAULT_TOTAL_CORES = 256L ;

    /**
     * The default total amount of CPU memory in GiB, used when the booking
     * query is built without an explicit capacity.
     * Set to 256 GiB.
     *
     */
    public static final long DEFAULT_TOTAL_MEMORY = 256L ;

    /**
     * Get the total number of CPU cores available on the platform.
     * Bound from calycopis.broker.booking.capacity.cores.
     *
     */
    public long getTotalAvailableCpuCores();

    /**
     * Get the total amount of CPU memory in GiB available on the platform.
     * Bound from calycopis.broker.booking.capacity.memory.
     *
     */
    public long getTotalAvailableCpuMemory();

    }
