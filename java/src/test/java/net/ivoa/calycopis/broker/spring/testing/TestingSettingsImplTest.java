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
 *     "interval": "2026-10-15T05:50:00/2026-10-15T05:57:00",
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

package net.ivoa.calycopis.broker.spring.testing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import net.ivoa.calycopis.broker.engine.functional.testing.TestingSettings;

/**
 * Tests for the TestingSettings binding.
 *
 * These cover the configuration path from the property names to the settings
 * bean, which the booking offer factory reads. The binding uses the
 * calycopis.broker.testing prefix, so a typo in the prefix or the property
 * names fails here rather than silently leaving the mechanism disabled.
 *
 */
public class TestingSettingsImplTest
    {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            TestingSettingsConfiguration.class
            );

    /**
     * With no configuration the mechanism must be inert.
     *
     */
    @Test
    public void testDefaultsToDisabled()
        {
        contextRunner.run(
            context -> {
                TestingSettings settings = context.getBean(
                    TestingSettings.class
                    );
                Assertions.assertFalse(
                    settings.isEnabled(),
                    "The testing mechanism must default to disabled"
                    );
                Assertions.assertEquals(
                    0L,
                    settings.getBookingQueryToSaveDelaySeconds(),
                    "The booking query delay must default to zero"
                    );
                }
            );
        }

    /**
     * Configured values must reach the bean.
     *
     */
    @Test
    public void testBindsEnabledAndDelay()
        {
        contextRunner
            .withPropertyValues(
                "calycopis.broker.testing.enabled=true",
                "calycopis.broker.testing.delays.booking-query-to-save=3"
                )
            .run(
                context -> {
                    TestingSettings settings = context.getBean(
                        TestingSettings.class
                        );
                    Assertions.assertTrue(
                        settings.isEnabled(),
                        "The configured enabled flag must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        3L,
                        settings.getBookingQueryToSaveDelaySeconds(),
                        "The configured delay must reach the settings bean"
                        );
                    }
                );
        }

    /**
     * The testing.yaml file is shared with the Python test-data
     * configuration, under calycopis.broker.testing.testdata. The broker does
     * not bind that key, so it must be ignored rather than rejected, and the
     * settings alongside it must still bind.
     *
     */
    @Test
    public void testIgnoresSharedTestDataKey()
        {
        contextRunner
            .withPropertyValues(
                "calycopis.broker.testing.enabled=true",
                "calycopis.broker.testing.delays.booking-query-to-save=2",
                "calycopis.broker.testing.testdata[0].name=random.dat",
                "calycopis.broker.testing.testdata[0].location=file:///var/calycopis/data/random.dat",
                "calycopis.broker.testing.testdata[0].md5sum=00000000000000000000000000000000"
                )
            .run(
                context -> {
                    Assertions.assertFalse(
                        context.getStartupFailure() != null,
                        "The shared testdata key must not fail the binding"
                        );
                    TestingSettings settings = context.getBean(
                        TestingSettings.class
                        );
                    Assertions.assertTrue(
                        settings.isEnabled(),
                        "The settings must bind alongside the shared testdata key"
                        );
                    Assertions.assertEquals(
                        2L,
                        settings.getBookingQueryToSaveDelaySeconds(),
                        "The delay must bind alongside the shared testdata key"
                        );
                    }
                );
        }

    /**
     * Register the settings bean for the binding tests.
     *
     */
    @Configuration
    @EnableConfigurationProperties(TestingSettingsImpl.class)
    static class TestingSettingsConfiguration
        {
        }

    }
