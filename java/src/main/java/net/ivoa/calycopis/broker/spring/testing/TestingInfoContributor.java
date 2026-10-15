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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.functional.testing.TestingSettings;

/**
 * Expose the testing configuration state on the actuator info endpoint.
 *
 * The value is always contributed, not only when the mechanism is enabled, so
 * a test can confirm that the reporting itself is live. That matters because
 * testing.yaml is an optional import: a missing or misplaced file disables the
 * whole mechanism silently, and without this a test would fail for the wrong
 * reason.
 *
 * This is the surface an operator or a test uses to see, and to confirm it can
 * turn off, a deliberately armed delay or failure.
 *
 */
@Component
public class TestingInfoContributor
implements InfoContributor
    {

    private final TestingSettings testingSettings;

    /**
     * Public constructor.
     *
     */
    public TestingInfoContributor(final TestingSettings testingSettings)
        {
        this.testingSettings = testingSettings;
        }

    @Override
    public void contribute(final Info.Builder builder)
        {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put(
            "enabled",
            testingSettings.isEnabled()
            );
        details.put(
            "booking-query-to-save-delay-seconds",
            testingSettings.getBookingQueryToSaveDelaySeconds()
            );
        builder.withDetail(
            "testing",
            details
            );
        }

    }
