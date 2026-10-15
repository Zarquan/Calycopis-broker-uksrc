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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.functional.testing.TestingSettings;

/**
 * Spring-specific implementation of TestingSettings, bound from the
 * calycopis.broker.testing section of the application configuration, which is
 * carried in the optional testing.yaml file.
 *
 * Everything is disabled by default. When the mechanism is enabled the bean
 * logs a clear warning at startup, naming the armed values, so an enabled
 * testing configuration is never silent.
 *
 * The testing.yaml file is shared with the Python test-data configuration,
 * which binds calycopis.broker.testing.testdata. That key is not represented
 * here, and Spring Boot ignores unknown properties by default, so the shared
 * file binds cleanly.
 *
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "calycopis.broker.testing")
public class TestingSettingsImpl
implements TestingSettings
    {

    private boolean enabled = false;

    @Override
    public boolean isEnabled()
        {
        return enabled;
        }

    public void setEnabled(boolean enabled)
        {
        this.enabled = enabled;
        }

    private Delays delays = new Delays();

    public Delays getDelays()
        {
        return delays;
        }

    public void setDelays(Delays delays)
        {
        this.delays = delays;
        }

    @Override
    public long getBookingQueryToSaveDelaySeconds()
        {
        return delays.getBookingQueryToSave();
        }

    /**
     * Log a clear warning when the testing mechanism is enabled.
     *
     */
    @PostConstruct
    public void logEnabled()
        {
        if (!this.enabled)
            {
            log.debug("Testing configuration is disabled.");
            return;
            }
        log.warn(
            "Testing configuration is ENABLED, booking-query-to-save delay [{}] seconds. This must not be used in production.",
            delays.getBookingQueryToSave()
            );
        }

    /**
     * Delays, in seconds.
     *
     */
    public static class Delays
        {

        private long bookingQueryToSave = 0L;

        public long getBookingQueryToSave()
            {
            return bookingQueryToSave;
            }

        public void setBookingQueryToSave(long bookingQueryToSave)
            {
            this.bookingQueryToSave = bookingQueryToSave;
            }

        }

    }
