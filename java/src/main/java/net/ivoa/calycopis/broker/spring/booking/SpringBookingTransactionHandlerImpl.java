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
 *     "interval": "2026-10-15T09:30:00/2026-10-15T09:46:00",
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

import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.functional.booking.BookingTransactionHandler;

/**
 * Spring-specific implementation of BookingTransactionHandler.
 *
 * The booking step runs in one transaction, and the advisory lock taken at the
 * start of it is released when that transaction ends, so no lock is left
 * behind if the step fails.
 *
 * A transaction on its own would not be enough: under READ COMMITTED two
 * concurrent transactions both read the free blocks before either commits its
 * reservations. The lock is what makes a concurrent request wait for the
 * reservations to be visible. PostgreSQL is already required by the booking
 * query, which uses GENERATE_SERIES, so a PostgreSQL advisory lock is a
 * smaller step than a lock table, and it composes per resource type rather
 * than serialising every stage of the booking pipeline (see issue #169).
 *
 */
@Slf4j
@Service
public class SpringBookingTransactionHandlerImpl
implements BookingTransactionHandler
    {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Public constructor.
     *
     */
    public SpringBookingTransactionHandlerImpl(final JdbcTemplate jdbcTemplate)
        {
        this.jdbcTemplate = jdbcTemplate;
        }

    @Override
    @Transactional
    public <T> T book(final String resourceType, final Supplier<T> booking)
        {
        //
        // Take the lock before the booking query runs, and hold it until this
        // transaction commits. hashtext() maps the resource type onto the
        // bigint key space, so a resource type always maps to the same lock,
        // and different resource types do not contend.
        //
        log.debug(
            "Booking lock for resource type [{}]",
            resourceType
            );
        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(hashtext(?)::bigint)",
            ps -> ps.setString(1, resourceType),
            rs -> { }
            );
        return booking.get();
        }

    }
