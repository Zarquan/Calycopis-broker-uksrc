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

package net.ivoa.calycopis.broker.engine.functional.platform;

/**
 * Framework-neutral interface for the session timing configuration.
 * Provides the externally configurable timing values used when
 * sessions expire, i.e. the amount of time an unaccepted (OFFERED)
 * session is allowed to live, and the polling interval used while
 * waiting for that expiry time to be reached.
 *
 */
public interface SessionTimingSettings
    {

    /**
     * Get the number of seconds an unaccepted session is allowed
     * to live before it is considered to have expired.
     * Bound from calycopis.broker.timing.session.EXPIRED.timeout.
     *
     */
    public long getExpiredTimeoutSeconds();

    /**
     * Get the number of seconds between polls while an OFFERED
     * session is waiting for its expiry time to be reached.
     * Bound from calycopis.broker.timing.session.EXPIRED.polling.
     *
     */
    public long getExpiredPollingSeconds();

    }
