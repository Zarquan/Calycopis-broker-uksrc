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

package net.ivoa.calycopis.broker.spring.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;

/**
 * Spring-specific implementation of BookingCapacitySettings, bound from
 * the calycopis.broker.booking.capacity section of the application configuration.
 *
 * Both values default to the platform defaults in BookingCapacitySettings, so
 * an application with no capacity configuration behaves as it did before the
 * setting existed. Memory is in GiB.
 *
 */
@Component
@ConfigurationProperties(prefix = "calycopis.broker.booking.capacity")
public class BookingCapacitySettingsImpl
implements BookingCapacitySettings
    {

    private long cores = DEFAULT_TOTAL_CORES;

    public long getCores()
        {
        return cores;
        }

    public void setCores(long cores)
        {
        this.cores = cores;
        }

    private long memory = DEFAULT_TOTAL_MEMORY;

    public long getMemory()
        {
        return memory;
        }

    public void setMemory(long memory)
        {
        this.memory = memory;
        }

    @Override
    public long getTotalAvailableCpuCores()
        {
        return this.cores;
        }

    @Override
    public long getTotalAvailableCpuMemory()
        {
        return this.memory;
        }

    }
