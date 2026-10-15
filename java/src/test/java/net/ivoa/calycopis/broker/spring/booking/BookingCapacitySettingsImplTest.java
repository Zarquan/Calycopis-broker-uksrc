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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;

/**
 * Tests for the BookingCapacitySettings binding.
 *
 * These cover the configuration path from the property names to the settings
 * bean, which the booking query then reads. The binding uses the
 * calycopis.broker.booking.capacity prefix, so a typo in the prefix or the
 * property names fails here rather than silently leaving the defaults in place.
 *
 */
public class BookingCapacitySettingsImplTest
    {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            CapacitySettingsConfiguration.class
            );

    /**
     * With no configuration the bean must report the platform defaults.
     *
     */
    @Test
    public void testDefaultsToPlatformCapacity()
        {
        contextRunner.run(
            context -> {
                BookingCapacitySettings settings = context.getBean(
                    BookingCapacitySettings.class
                    );
                Assertions.assertEquals(
                    BookingCapacitySettings.DEFAULT_TOTAL_CORES,
                    settings.getTotalAvailableCpuCores(),
                    "The default total cores must be the platform default"
                    );
                Assertions.assertEquals(
                    BookingCapacitySettings.DEFAULT_TOTAL_MEMORY,
                    settings.getTotalAvailableCpuMemory(),
                    "The default total memory must be the platform default"
                    );
                }
            );
        }

    /**
     * Configured values must reach the bean, so a deployment or a test can
     * size the platform.
     *
     */
    @Test
    public void testBindsConfiguredCapacity()
        {
        contextRunner
            .withPropertyValues(
                "calycopis.broker.booking.capacity.cores=1",
                "calycopis.broker.booking.capacity.memory=2"
                )
            .run(
                context -> {
                    BookingCapacitySettings settings = context.getBean(
                        BookingCapacitySettings.class
                        );
                    Assertions.assertEquals(
                        1L,
                        settings.getTotalAvailableCpuCores(),
                        "The configured total cores must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        2L,
                        settings.getTotalAvailableCpuMemory(),
                        "The configured total memory must reach the settings bean"
                        );
                    }
                );
        }

    /**
     * Register the settings bean for the binding tests.
     *
     */
    @Configuration
    @EnableConfigurationProperties(BookingCapacitySettingsImpl.class)
    static class CapacitySettingsConfiguration
        {
        }

    }
