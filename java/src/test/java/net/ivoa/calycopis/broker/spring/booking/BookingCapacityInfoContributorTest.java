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

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;

/**
 * Tests for the BookingCapacityInfoContributor.
 *
 * The race test in tests/python/states reads the capacity from the info
 * endpoint, so the shape of the contributed details is part of the contract
 * between the broker and that test.
 *
 */
public class BookingCapacityInfoContributorTest
    {

    /**
     * The details must carry the configured capacity, so a test can read the
     * value the broker was started with rather than assuming it.
     *
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testContributesConfiguredCapacity()
        {
        BookingCapacitySettings settings = new BookingCapacitySettings()
            {
            @Override
            public long getTotalAvailableCpuCores()
                {
                return 1L;
                }

            @Override
            public long getTotalAvailableCpuMemory()
                {
                return 2L;
                }
            };

        Info.Builder builder = new Info.Builder();
        new BookingCapacityInfoContributor(settings).contribute(builder);
        Info info = builder.build();

        Object bookingDetail = info.getDetails().get("booking");
        Assertions.assertInstanceOf(
            Map.class,
            bookingDetail,
            "The booking details must be a map"
            );

        Object capacityDetail = ((Map<String, Object>) bookingDetail).get("capacity");
        Assertions.assertInstanceOf(
            Map.class,
            capacityDetail,
            "The capacity details must be a map"
            );

        Map<String, Object> capacity = (Map<String, Object>) capacityDetail;
        Assertions.assertEquals(
            1L,
            capacity.get("cores"),
            "The contributed cores must be the configured value"
            );
        Assertions.assertEquals(
            2L,
            capacity.get("memory"),
            "The contributed memory must be the configured value"
            );
        }

    }
