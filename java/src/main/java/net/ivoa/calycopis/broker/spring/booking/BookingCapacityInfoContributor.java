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
 *     "interval": "2026-10-15T06:20:00/2026-10-15T07:50:00",
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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;

/**
 * Expose the configured booking capacity on the actuator info endpoint.
 *
 * The capacity is otherwise invisible from outside the broker: it is what the
 * booking query compares requests against, and it decides how many concurrent
 * sessions can share a block of time. A test that needs to know the capacity
 * the broker was started with reads it here, rather than assuming it.
 *
 */
@Component
public class BookingCapacityInfoContributor
implements InfoContributor
    {

    private final BookingCapacitySettings bookingCapacitySettings;

    /**
     * Public constructor.
     *
     */
    public BookingCapacityInfoContributor(final BookingCapacitySettings bookingCapacitySettings)
        {
        this.bookingCapacitySettings = bookingCapacitySettings;
        }

    @Override
    public void contribute(final Info.Builder builder)
        {
        Map<String, Object> capacity = new LinkedHashMap<String, Object>();
        capacity.put(
            "cores",
            bookingCapacitySettings.getTotalAvailableCpuCores()
            );
        capacity.put(
            "memory",
            bookingCapacitySettings.getTotalAvailableCpuMemory()
            );
        Map<String, Object> booking = new LinkedHashMap<String, Object>();
        booking.put(
            "capacity",
            capacity
            );
        builder.withDetail(
            "booking",
            booking
            );
        }

    }
