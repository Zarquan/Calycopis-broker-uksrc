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
 *     },
 *     {
 *     "interval": "2026-10-11T13:33:00/2026-10-11T13:35:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 30,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-13T10:19:00/2026-10-13T10:35:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 28,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-15T05:23:00/2026-10-15T05:27:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 8,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-16T14:40:00/2026-10-16T16:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 30,
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
     * With no explicit capacity, the query must use the platform defaults,
     * so an application with no capacity configuration behaves as it did
     * before the setting existed.
     *
     */
    @Test
    public void testBuildUsesDefaultCapacity()
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
            query.contains("(256  - COALESCE(sum(ExecutionBlocks.UsedCores),  0)) AS FreeCores"),
            "FreeCores must default to the platform total of 256 cores"
            );
        Assertions.assertTrue(
            query.contains("(256 - COALESCE(sum(ExecutionBlocks.UsedMemory), 0)) AS FreeMemory"),
            "FreeMemory must default to the platform total of 256 GiB"
            );
        }

    /**
     * An explicit capacity must be used in place of the platform defaults,
     * so a deployment or a test can size the platform.
     *
     */
    @Test
    public void testBuildUsesConfiguredCapacity()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofHours(2),
            1L,
            1L,
            4,
            16L,
            32L
            );
        Assertions.assertTrue(
            query.contains("(16  - COALESCE(sum(ExecutionBlocks.UsedCores),  0)) AS FreeCores"),
            "FreeCores must use the configured total of 16 cores"
            );
        Assertions.assertTrue(
            query.contains("(32 - COALESCE(sum(ExecutionBlocks.UsedMemory), 0)) AS FreeMemory"),
            "FreeMemory must use the configured total of 32 GiB"
            );
        Assertions.assertFalse(
            query.contains("(256  - COALESCE(sum(ExecutionBlocks.UsedCores),  0)) AS FreeCores"),
            "The default total must not be used when an explicit capacity is given"
            );
        }

    /**
     * A duration of less than one minute must round up to one block, plus
     * the block that compensates for the current block, and must not
     * truncate to zero blocks.
     *
     */
    @Test
    public void testBuildSubMinuteDurationIncludesCompensationBlock()
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
            query.contains("COUNT(*) >= 2"),
            "A 30 second duration must require one block plus one compensation block"
            );
        Assertions.assertFalse(
            query.contains("COUNT(*) >= 0"),
            "A 30 second duration must not truncate to zero blocks"
            );
        Assertions.assertTrue(
            query.contains("generate_series(1, 2)"),
            "The maximum block length must not be smaller than the minimum block length"
            );
        }

    /**
     * The reservation must be the hull of the preparing, available and
     * releasing windows, not the available window alone.
     *
     * Counting only the available window let two sessions with adjacent
     * available windows pass the query while overlapping in reality, because
     * one session's preparing, or the other's releasing, fell outside the
     * window that was counted. See issue #158.
     *
     */
    @Test
    public void testBuildReservesTheWholeReservationHull()
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
        //
        // Compare with the whitespace collapsed, so that reformatting the SQL
        // does not break the test, but changing the derivation does.
        //
        String compact = query.replaceAll("\\s+", " ");
        Assertions.assertTrue(
            compact.contains(
                "least( LifecycleComponents.prepare_start_instant_seconds, "
                + "LifecycleComponents.available_start_instant_seconds, "
                + "LifecycleComponents.release_start_instant_seconds ) / 60 ) AS BlockStart"
                ),
            "The reservation must start at the least of the three window starts"
            );
        Assertions.assertTrue(
            compact.contains(
                "greatest( LifecycleComponents.prepare_start_instant_seconds "
                + "+ LifecycleComponents.prepare_duration_seconds, "
                + "LifecycleComponents.available_start_instant_seconds "
                + "+ LifecycleComponents.available_start_duration_seconds "
                + "+ LifecycleComponents.available_duration_seconds, "
                + "LifecycleComponents.release_start_instant_seconds "
                + "+ LifecycleComponents.release_duration_seconds )"
                ),
            "The reservation must end at the greatest of the three window ends, "
            + "including the available start duration"
            );
        Assertions.assertFalse(
            compact.contains("available_start_instant_seconds / 60 ) AS BlockStart"),
            "The reservation must not start at the available window alone"
            );
        }

    /**
     * A duration that is not a whole number of minutes must round up to
     * the next whole block, plus the compensation block.
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
            query.contains("COUNT(*) >= 3"),
            "A 61 second duration must round up to two blocks plus one compensation block"
            );
        }

    /**
     * The phase filter must include every live session phase, including
     * ACCEPTED and AVAILABLE, and must exclude the dead offer phases.
     *
     */
    @Test
    public void testBuildIncludesActiveSessionPhases()
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
            query.contains("SimpleExecutionSessions.phase IN ('OFFERED', 'ACCEPTED', 'WAITING', 'PREPARING', 'AVAILABLE', 'RUNNING', 'RELEASING')"),
            "The phase filter must include every live session phase"
            );
        Assertions.assertFalse(
            query.contains("'REJECTED'"),
            "The phase filter must exclude REJECTED sessions"
            );
        Assertions.assertFalse(
            query.contains("'EXPIRED'"),
            "The phase filter must exclude EXPIRED sessions"
            );
        }

    /**
     * The booking blocks must come from the compute resource's own
     * availability window, not from the parent session timing.
     *
     */
    @Test
    public void testBuildUsesComputeResourceAvailabilityWindow()
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
            query.contains("LifecycleComponents.available_start_instant_seconds"),
            "BlockStart must come from the compute resource availability window"
            );
        Assertions.assertTrue(
            query.contains("LifecycleComponents.available_duration_seconds"),
            "BlockLength must come from the compute resource availability window"
            );
        Assertions.assertFalse(
            query.contains("SimpleExecutionSessions.available_"),
            "The parent session timing must not be used for the booking blocks"
            );
        }

    /**
     * The query must join the compute resources to their lifecycle
     * component row, which holds the availability window.
     *
     */
    @Test
    public void testBuildJoinsComputeResourcesToLifecycleComponents()
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
            query.contains("LifecycleComponents.uuid = AbstractComputeResources.uuid"),
            "The compute resources must be joined to their lifecycle components"
            );
        }

    /**
     * The block phase must come from the compute resource phase.
     *
     */
    @Test
    public void testBuildUsesComputeResourcePhase()
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
            query.contains("LifecycleComponents.phase AS BlockPhase"),
            "The block phase must come from the compute resource phase"
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
     * applied: a 2 hour duration maps to 120 blocks plus one compensation
     * block, and the default query limit is 4 rows.
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
            query.contains("COUNT(*) >= 121"),
            "The default 2 hour duration must map to 120 blocks plus one compensation block"
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
            query.contains("generate_series(0, 120)"),
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

    /**
     * The search must start at the block that contains the requested start
     * time, so an immediate request can be offered the current block.
     *
     */
    @Test
    public void testBuildIncludesCurrentBlock()
        {
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofMinutes(5),
            1L,
            1L,
            4
            );
        Assertions.assertTrue(
            query.contains("generate_series(0, 120)"),
            "The search must start at the block that contains the requested start time"
            );
        Assertions.assertFalse(
            query.contains("generate_series(1, 120)"),
            "The search must not skip the block that contains the requested start time"
            );
        }

    /**
     * A reservation window must be rounded out to whole blocks, so a
     * window that does not sit on block boundaries still reserves every
     * block it touches.
     *
     */
    @Test
    public void testBuildRoundsReservationWindowOut()
        {
        String template = SimpleComputeResourceQuery.DATABASE_QUERY.replaceAll("\\s+", " ");
        Assertions.assertTrue(
            template.contains("+ (:blockstep - 1) ) / :blockstep - ( least("),
            "The block length must round the reservation end up and subtract its rounded down start"
            );
        String query = SimpleComputeResourceQuery.build(
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                ),
            Duration.ofMinutes(5),
            1L,
            1L,
            4
            );
        Assertions.assertTrue(
            query.contains("+ (60 - 1)"),
            "The rounded up end must be built into the query"
            );
        }

    /**
     * The range start instant must apply the same default as the query,
     * and must be the start of an explicit start interval.
     *
     */
    @Test
    public void testGetRangeStartInstant()
        {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        //
        // A null start interval resolves to the current time.
        //
        Instant before = Instant.now();
        Instant resolved = SimpleComputeResourceQuery.getRangeStartInstant(
            null
            );
        Assertions.assertFalse(
            resolved.isBefore(before),
            "A null start interval must resolve to the current time"
            );
        //
        // An explicit start interval resolves to its start.
        //
        Assertions.assertEquals(
            start,
            SimpleComputeResourceQuery.getRangeStartInstant(
                Interval.of(start, Duration.ofMinutes(5))
                ),
            "An explicit start interval must resolve to its start"
            );
        //
        // An open ended start interval resolves to its start.
        //
        Assertions.assertEquals(
            start,
            SimpleComputeResourceQuery.getRangeStartInstant(
                Interval.of(start, Instant.MAX)
                ),
            "An open ended start interval must resolve to its start"
            );
        }
    }