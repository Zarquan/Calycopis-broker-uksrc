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
 *     "timestamp": "2026-10-20T11:57:26",
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

package net.ivoa.calycopis.broker.spring.webapp.error;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Tests for the RFC 9457 evaluation settings binding.
 *
 */
public class ErrorResponseSettingsImplTest
    {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            SettingsConfiguration.class
            );

    @Test
    public void defaultsToExistingDirectResponse()
        {
        this.contextRunner.run(
            context -> {
                ErrorResponseSettings settings = context.getBean(
                    ErrorResponseSettings.class
                    );
                Assertions.assertFalse(
                    settings.isDirectExecutionProblemDetails(),
                    "The direct execution problem details flag must default to false"
                    );
                }
            );
        }

    @Test
    public void bindsConfiguredValue()
        {
        this.contextRunner.withPropertyValues(
            "calycopis.broker.errors.direct-execution-problem-details=true"
            ).run(
                context -> {
                    ErrorResponseSettings settings = context.getBean(
                        ErrorResponseSettings.class
                        );
                    Assertions.assertTrue(
                        settings.isDirectExecutionProblemDetails(),
                        "The configured flag must reach the settings bean"
                        );
                    }
                );
        }

    /**
     * Register the settings bean for the binding tests.
     *
     */
    @Configuration
    @EnableConfigurationProperties(ErrorResponseSettingsImpl.class)
    static class SettingsConfiguration
        {
        }
    }
