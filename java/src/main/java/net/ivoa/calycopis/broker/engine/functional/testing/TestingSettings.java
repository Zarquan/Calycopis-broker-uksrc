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

package net.ivoa.calycopis.broker.engine.functional.testing;

/**
 * Framework-neutral interface for the testability and fault-injection
 * configuration.
 *
 * The settings are bound from the calycopis.broker.testing section of the
 * application configuration, which is carried in the optional testing.yaml
 * file. The whole mechanism is inert unless {@link #isEnabled()} is true, and
 * it is disabled by default, so a production deployment that has no
 * testing.yaml behaves exactly as it did before the mechanism existed.
 *
 * The values are grouped by effect:
 *
 *  * delays - hold part of a request open for a configured time, so a timing
 *    window that a test depends on can be made reproducible. Values are in
 *    seconds.
 *  * faults - deliberately fail part of a request, for chaos engineering
 *    style experiments. Fault switches are added as consumers need them.
 *
 * Anything added here must remain inert while {@link #isEnabled()} is false,
 * and consumers must log when they act, so an injected delay or failure can
 * never be mistaken for a real one.
 *
 * testing.yaml is shared with the Python test-data configuration, which binds
 * calycopis.broker.testing.testdata. This interface binds only the keys below,
 * and the testdata key is ignored by the broker.
 *
 */
public interface TestingSettings
    {

    /**
     * Is the testing mechanism enabled?
     * Bound from calycopis.broker.testing.enabled, and false by default.
     *
     */
    public boolean isEnabled();

    /**
     * How long to hold the window between the booking query and the offer
     * save, in seconds.
     * Bound from calycopis.broker.testing.delays.booking-query-to-save, and
     * zero by default.
     *
     * The atomicity race in #156 needs this window to be wide enough that a
     * second request can reach its own booking query while the first is
     * between the query and the save.
     *
     */
    public long getBookingQueryToSaveDelaySeconds();

    }
