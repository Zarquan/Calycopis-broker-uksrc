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

import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQuery;

/**
 * The query that reads the current block's compute pool capacity.
 *
 * It answers one question for every pool: in the block containing the supplied
 * instant, how many cores and how much memory are allocated, how many are
 * reserved by live compute reservations, and therefore how many are available.
 *
 * The block rules match the booking query. An allocation covers every block
 * from its start up to, but not including, the block containing its end. A
 * reservation covers the hull of its preparing, available and releasing
 * windows, rounded outward to whole blocks. The live session phases are the
 * same set the booking query counts.
 *
 * Memory is returned in the same unit as the allocation and reservation
 * columns: GiB. The Prometheus publisher converts it to bytes.
 *
 * This SQL substitutes literals rather than binding parameters because the
 * Spring query handler ignores its parameter map. Every value substituted here
 * is a number derived from an Instant, so it cannot carry user text. Follow the
 * same rule as the booking query and the allocation guard.
 *
 */
public interface ComputePoolMetricsQuery
    {

    /**
     * The database query.
     *
     */
    public static final String DATABASE_QUERY =
        """
        WITH CurrentBlock AS
            (
            --
            -- The 60-second block containing the instant being measured.
            --
            SELECT
                :blockstart AS BlockStart
            ),
        PoolAllocations AS
            (
            --
            -- The supply of each pool in the current block: the sum of the
            -- allocations covering it. An open-ended allocation covers every
            -- block from its start onwards.
            --
            SELECT
                Allocations.pool       AS Pool,
                SUM(Allocations.cores)  AS AllocatedCores,
                SUM(Allocations.memory) AS AllocatedMemory
            FROM
                AbstractComputeAllocations AS Allocations,
                CurrentBlock
            WHERE
                (Allocations.startseconds / :blockstep) <= CurrentBlock.BlockStart
            AND
                (
                    Allocations.endseconds IS NULL
                OR
                    ((Allocations.endseconds + (:blockstep - 1)) / :blockstep) > CurrentBlock.BlockStart
                )
            GROUP BY
                Allocations.pool
            ),
        PoolReservations AS
            (
            --
            -- The capacity held by live reservations in the current block.
            -- A reservation is the hull of its three planned windows, rounded
            -- outward, so it holds every block it touches. The effective size
            -- is the offered value when one was stored, otherwise the requested
            -- value, matching the booking query.
            --
            SELECT
                AbstractComputeReservations.pool AS Pool,
                SUM(COALESCE(
                    SimpleComputeReservations.maxofferedcores,
                    SimpleComputeReservations.maxrequestedcores
                    )) AS ReservedCores,
                SUM(COALESCE(
                    SimpleComputeReservations.maxofferedmemory,
                    SimpleComputeReservations.maxrequestedmemory
                    )) AS ReservedMemory
            FROM
                AbstractComputeReservations
            JOIN
                LifecycleComponents
            ON
                LifecycleComponents.uuid = AbstractComputeReservations.uuid
            JOIN
                SimpleComputeReservations
            ON
                SimpleComputeReservations.uuid = AbstractComputeReservations.uuid
            JOIN
                SimpleExecutionSessions
            ON
                SimpleExecutionSessions.uuid = AbstractComputeReservations.session
            CROSS JOIN
                CurrentBlock
            WHERE
                SimpleExecutionSessions.phase IN ('OFFERED', 'ACCEPTED', 'WAITING', 'PREPARING', 'AVAILABLE', 'RUNNING', 'RELEASING')
            AND
                (
                least(
                    LifecycleComponents.prepare_start_instant_seconds,
                    LifecycleComponents.available_start_instant_seconds,
                    LifecycleComponents.release_start_instant_seconds
                    ) / :blockstep
                ) <= CurrentBlock.BlockStart
            AND
                (
                (
                greatest(
                    LifecycleComponents.prepare_start_instant_seconds
                        + LifecycleComponents.prepare_duration_seconds,
                    LifecycleComponents.available_start_instant_seconds
                        + LifecycleComponents.available_start_duration_seconds
                        + LifecycleComponents.available_duration_seconds,
                    LifecycleComponents.release_start_instant_seconds
                        + LifecycleComponents.release_duration_seconds
                    )
                    + (:blockstep - 1)
                ) / :blockstep
                ) > CurrentBlock.BlockStart
            GROUP BY
                AbstractComputeReservations.pool
            )
        SELECT
            Components.uuid AS pooluuid,
            Components.name AS poolname,
            COALESCE(PoolAllocations.AllocatedCores, 0)   AS allocatedcores,
            COALESCE(PoolReservations.ReservedCores, 0)   AS reservedcores,
            COALESCE(PoolAllocations.AllocatedMemory, 0)  AS allocatedmemory,
            COALESCE(PoolReservations.ReservedMemory, 0)  AS reservedmemory
        FROM
            Components
        JOIN
            AbstractComputeResourcePools
        ON
            AbstractComputeResourcePools.uuid = Components.uuid
        LEFT JOIN
            PoolAllocations
        ON
            PoolAllocations.Pool = Components.uuid
        LEFT JOIN
            PoolReservations
        ON
            PoolReservations.Pool = Components.uuid
        ORDER BY
            Components.name,
            Components.uuid
        """;

    /**
     * Build the query for one instant.
     *
     * @param instant The instant whose block is being measured.
     * @return The database query with its placeholders substituted.
     *
     */
    public static String build(final Instant instant)
        {
        String query = DATABASE_QUERY;
        query = query.replace(
            ":blockstep",
            String.valueOf(
                SimpleComputeReservationQuery.BLOCK_STEP_SECONDS
                )
            );
        query = query.replace(
            ":blockstart",
            String.valueOf(
                instant.getEpochSecond() / SimpleComputeReservationQuery.BLOCK_STEP_SECONDS
                )
            );
        return query;
        }

    }
