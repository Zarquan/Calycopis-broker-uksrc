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
 *     "timestamp": "2026-09-11T11:29:44",
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

package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;

/**
 * Unit tests for the SimpleComputeResourceQuery query builder.
 *
 */
public class SimpleComputeResourceQueryTest
    {

    /**
     * The named parameters that must be substituted into the query.
     *
     */
    private static final String[] PLACEHOLDERS = new String[] {
        ":blockstep",
        ":totalcores",
        ":totalmemory",
        ":rangeoffset",
        ":rangestart",
        ":rangeend",
        ":mincores",
        ":minmemory",
        ":minblocklength",
        ":maxblocklength",
        ":maxcores",
        ":maxmemory",
        ":querylimit"
        };

    /**
     * The query constant must exist and be non-empty.
     *
     */
    @Test
    public void testDatabaseQueryConstantExists()
        {
        Assertions.assertNotNull(
            SimpleComputeResourceQuery.DATABASE_QUERY
            );
        Assertions.assertFalse(
            SimpleComputeResourceQuery.DATABASE_QUERY.isEmpty()
            );
        }

    /**
     * Build a query with explicit values and check that every named
     * parameter has been substituted.
     *
     */
    @Test
    public void testBuildSubstitutesAllParameters()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofHours(2),
            2L,
            4L,
            3
            );
        for (String placeholder : PLACEHOLDERS)
            {
            Assertions.assertFalse(
                query.contains(placeholder),
                "Query still contains unreplaced placeholder [" + placeholder + "]"
                );
            }
        Assertions.assertTrue(
            query.contains("SELECT * FROM EarlyBlocks"),
            "Query must end with the EarlyBlocks select"
            );
        }

    /**
     * The memory dimension must use GiB values, not byte values.
     *
     */
    @Test
    public void testBuildMemoryTotalsInGib()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofHours(2),
            2L,
            4L,
            3
            );
        Assertions.assertTrue(
            query.contains("(256 - COALESCE(sum(ExecutionBlocks.UsedMemory), 0)) AS FreeMemory"),
            "FreeMemory must be computed from the GiB total of 256"
            );
        Assertions.assertFalse(
            query.contains("274877906944"),
            "The byte value of 256 GiB must not appear in the query"
            );
        }

    /**
     * A duration of less than one minute must round up to a single block,
     * not truncate to zero blocks.
     *
     */
    @Test
    public void testBuildSubMinuteDurationRoundsUpToSingleBlock()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofSeconds(30),
            1L,
            1L,
            4
            );
        Assertions.assertTrue(
            query.contains("COUNT(*) >= 1"),
            "A 30 second duration must require at least one block"
            );
        Assertions.assertFalse(
            query.contains("COUNT(*) >= 0"),
            "A 30 second duration must not truncate to zero blocks"
            );
        Assertions.assertTrue(
            query.contains("generate_series(1, 1)"),
            "The maximum block length must be at least one block"
            );
        }

    /**
     * A duration that is not a whole number of minutes must round up to
     * the next whole block.
     *
     */
    @Test
    public void testBuildDurationRoundsUpToWholeBlocks()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofSeconds(61),
            1L,
            1L,
            4
            );
        Assertions.assertTrue(
            query.contains("COUNT(*) >= 2"),
            "A 61 second duration must round up to two blocks"
            );
        }

    /**
     * The phase filter must include ACCEPTED sessions.
     *
     */
    @Test
    public void testBuildIncludesAcceptedPhase()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofHours(2),
            1L,
            1L,
            4
            );
        Assertions.assertTrue(
            query.contains("SimpleExecutionSessions.phase IN ('ACCEPTED', 'OFFERED', 'PREPARING', 'WAITING', 'RUNNING', 'RELEASING')"),
            "The phase filter must include ACCEPTED"
            );
        }

    /**
     * The unused ranking CTEs must have been removed.
     *
     */
    @Test
    public void testBuildExcludesDeadRankingCtes()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofHours(2),
            1L,
            1L,
            4
            );
        Assertions.assertFalse(
            query.contains("HiMemBlocks"),
            "The HiMemBlocks CTE must be removed"
            );
        Assertions.assertFalse(
            query.contains("HiCpuBlocks"),
            "The HiCpuBlocks CTE must be removed"
            );
        Assertions.assertFalse(
            query.contains("CombinedQuery"),
            "The CombinedQuery CTE must be removed"
            );
        }

    /**
     * Build a query with no request values and check the defaults are
     * applied: a 2 hour duration maps to 120 blocks and the default
     * query limit is 4 rows.
     *
     */
    @Test
    public void testBuildAppliesDefaults()
        {
        String query = SimpleComputeResourceQuery.build(
            null,
            null,
            null,
            null,
            0
            );
        Assertions.assertTrue(
            query.contains("COUNT(*) >= 120"),
            "The default 2 hour duration must map to 120 blocks"
            );
        Assertions.assertTrue(
            query.contains("LIMIT 4"),
            "The default query limit must be 4 rows"
            );
        for (String placeholder : PLACEHOLDERS)
            {
            Assertions.assertFalse(
                query.contains(placeholder),
                "Query still contains unreplaced placeholder [" + placeholder + "]"
                );
            }
        }

    /**
     * An open ended start interval must be replaced with the default
     * start range.
     *
     */
    @Test
    public void testBuildOpenEndedStartIntervalUsesDefaultRange()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.MAX
                ),
            Duration.ofHours(2),
            1L,
            1L,
            4
            );
        Assertions.assertTrue(
            query.contains("generate_series(1, 120)"),
            "The open ended start interval must use the default 120 block range"
            );
        for (String placeholder : PLACEHOLDERS)
            {
            Assertions.assertFalse(
                query.contains(placeholder),
                "Query still contains unreplaced placeholder [" + placeholder + "]"
                );
            }
        }
    }