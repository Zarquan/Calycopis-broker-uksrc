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
 *     "timestamp": "2026-09-05T12:05:01",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.functional.platform.SessionTimingSettings;

/**
 * Spring-specific implementation of SessionTimingSettings, bound from
 * the calycopis.broker.timing.session section of the application configuration.
 *
 * Timing values are in seconds.
 *
 */
@Component
@ConfigurationProperties(prefix = "calycopis.broker.timing.session")
public class SessionTimingSettingsImpl
implements SessionTimingSettings
    {

    private Expired expired = new Expired();

    public Expired getExpired()
        {
        return expired;
        }

    public void setExpired(Expired expired)
        {
        this.expired = expired;
        }

    @Override
    public long getExpiredTimeoutSeconds()
        {
        return expired.getTimeout();
        }

    @Override
    public long getExpiredPollingSeconds()
        {
        return expired.getPolling();
        }

    /**
     * Timing settings for session expiry.
     *
     */
    public static class Expired
        {
        private long timeout = 5 * 60L;
        private long polling = 5L;

        public long getTimeout()
            {
            return timeout;
            }

        public void setTimeout(long timeout)
            {
            this.timeout = timeout;
            }

        public long getPolling()
            {
            return polling;
            }

        public void setPolling(long polling)
            {
            this.polling = polling;
            }
        }
    }
