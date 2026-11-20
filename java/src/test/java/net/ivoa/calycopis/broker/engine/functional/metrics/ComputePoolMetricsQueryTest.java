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
 *     "timestamp": "2026-11-20T17:13:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.metrics;

import java.time.Instant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the ComputePoolMetricsQuery query builder.
 *
 */
public class ComputePoolMetricsQueryTest
    {

    /**
     * The query constant must exist and be non-empty.
     *
     */
    @Test
    public void testDatabaseQueryConstantExists()
        {
        Assertions.assertNotNull(
            ComputePoolMetricsQuery.DATABASE_QUERY
            );
        Assertions.assertFalse(
            ComputePoolMetricsQuery.DATABASE_QUERY.isEmpty()
            );
        }

    /**
     * Build a query and check that the block placeholders are substituted.
     *
     */
    @Test
    public void testBuildSubstitutesBlockValues()
        {
        final String query = ComputePoolMetricsQuery.build(
            Instant.parse("2026-11-20T17:13:15Z")
            );

        Assertions.assertFalse(
            query.contains(":blockstep"),
            "Query still contains the block step placeholder"
            );
        Assertions.assertFalse(
            query.contains(":blockstart"),
            "Query still contains the block start placeholder"
            );

        final long expectedBlockStart = Instant.parse("2026-11-20T17:13:15Z").getEpochSecond() / 60L;

        Assertions.assertTrue(
            query.contains(
                String.valueOf(
                    expectedBlockStart
                    )
                ),
            "Query must contain the block containing the supplied instant"
            );
        Assertions.assertTrue(
            query.contains("(60 - 1)"),
            "Query must round allocation and reservation ends up to whole blocks"
            );
        }

    /**
     * The query must read the pool capacity from the pools and their
     * allocations, and the reservation capacity from the live sessions.
     *
     */
    @Test
    public void testQueryReadsPoolsAllocationsAndReservations()
        {
        final String query = ComputePoolMetricsQuery.build(
            Instant.parse("2026-11-20T17:13:15Z")
            );

        Assertions.assertTrue(
            query.contains("AbstractComputeResourcePools"),
            "Query must read the compute pools"
            );
        Assertions.assertTrue(
            query.contains("AbstractComputeAllocations"),
            "Query must read the pool allocations"
            );
        Assertions.assertTrue(
            query.contains("AbstractComputeReservations"),
            "Query must read the compute reservations"
            );
        Assertions.assertTrue(
            query.contains("SimpleExecutionSessions"),
            "Query must read the owning execution sessions"
            );
        Assertions.assertTrue(
            query.contains("COALESCE("),
            "Query must default missing capacity to zero"
            );
        }

    /**
     * The query must count the same live phases as the booking query.
     *
     */
    @Test
    public void testQueryUsesLiveSessionPhases()
        {
        final String query = ComputePoolMetricsQuery.build(
            Instant.parse("2026-11-20T17:13:15Z")
            );

        for (final String phase : new String[] {
            "OFFERED",
            "ACCEPTED",
            "WAITING",
            "PREPARING",
            "AVAILABLE",
            "RUNNING",
            "RELEASING"
            })
            {
            Assertions.assertTrue(
                query.contains(
                    "'" + phase + "'"
                    ),
                "Query must count the live phase [" + phase + "]"
                );
            }
        }

    /**
     * The query must use the offered size when one was stored, and fall back
     * to the requested size otherwise.
     *
     */
    @Test
    public void testQueryUsesOfferedThenRequestedSizes()
        {
        final String query = ComputePoolMetricsQuery.build(
            Instant.parse("2026-11-20T17:13:15Z")
            );

        Assertions.assertTrue(
            query.contains("maxofferedcores"),
            "Query must read the offered cores"
            );
        Assertions.assertTrue(
            query.contains("maxrequestedcores"),
            "Query must fall back to the requested cores"
            );
        Assertions.assertTrue(
            query.contains("maxofferedmemory"),
            "Query must read the offered memory"
            );
        Assertions.assertTrue(
            query.contains("maxrequestedmemory"),
            "Query must fall back to the requested memory"
            );
        }
    }
