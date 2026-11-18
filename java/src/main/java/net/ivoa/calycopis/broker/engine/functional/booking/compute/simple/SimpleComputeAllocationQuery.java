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
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-18T11:15:00/2026-11-18T11:55:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.time.Instant;
import java.util.UUID;

/**
 * The query that decides whether an allocation change would over-commit a pool.
 *
 * The guard answers one question: is there any block, among the blocks the live
 * reservations occupy, where the reservations ask for more cores or memory than
 * the pool's allocations supply once the candidate change is applied? It reads
 * reservations and allocations directly rather than running the booking query,
 * because the booking query looks ahead from a requested start while the guard
 * has to consider every block a reservation holds.
 *
 * The candidate allocation is identified by its UUID so the stored row can be
 * excluded, and its new values are supplied as literals. For a removal the
 * caller passes zero sizes, which is the same query with no candidate supply.
 *
 * The block grid matches the booking query: a reservation is the hull of its
 * preparing, available and releasing windows, rounded out to whole blocks, and
 * an allocation covers every block from its start up to the block containing
 * its end.
 *
 * This SQL is vulnerable to injection if a caller passes anything but a UUID
 * and numbers. Every caller does, and the placeholders are substituted here
 * rather than bound, for the same reason as the booking query: the Spring query
 * handler ignores its parameter map.
 *
 */
public interface SimpleComputeAllocationQuery
    {

    /**
     * The database query.
     *
     */
    public static final String DATABASE_QUERY =
        """
        WITH ReservedBlocks AS
            (
            --
            -- One row per live compute reservation, covering the blocks spanned
            -- by the hull of its preparing, available and releasing windows.
            -- The live phases match the booking query, so the guard sees the
            -- same reservations a new offer would see.
            --
            SELECT
                (
                    least(
                        LifecycleComponents.prepare_start_instant_seconds,
                        LifecycleComponents.available_start_instant_seconds,
                        LifecycleComponents.release_start_instant_seconds
                        ) / :blockstep
                ) AS BlockStart,
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
                ) AS BlockEnd,
                COALESCE(SimpleComputeReservations.maxofferedcores,  SimpleComputeReservations.maxrequestedcores)  AS UsedCores,
                COALESCE(SimpleComputeReservations.maxofferedmemory, SimpleComputeReservations.maxrequestedmemory) AS UsedMemory
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
            WHERE
                SimpleExecutionSessions.phase IN ('OFFERED', 'ACCEPTED', 'WAITING', 'PREPARING', 'AVAILABLE', 'RUNNING', 'RELEASING')
            ),
        ReservedBlocksByBlock AS
            (
            --
            -- Expand each reservation over the blocks it holds and sum the
            -- reservations that share a block.
            --
            SELECT
                Blocks.BlockStart              AS BlockStart,
                SUM(ReservedBlocks.UsedCores)  AS UsedCores,
                SUM(ReservedBlocks.UsedMemory) AS UsedMemory
            FROM
                ReservedBlocks
            CROSS JOIN
                generate_series(ReservedBlocks.BlockStart, ReservedBlocks.BlockEnd - 1) AS Blocks(BlockStart)
            GROUP BY
                Blocks.BlockStart
            ),
        OverCommitted AS
            (
            --
            -- Keep the blocks where the reservations ask for more than the pool
            -- supplies. The subquery sums the other allocations of the pool, and
            -- the CASE adds the candidate allocation where its candidate window
            -- covers the block. A zero size is how a removal contributes
            -- nothing.
            --
            SELECT
                ReservedBlocksByBlock.BlockStart
            FROM
                ReservedBlocksByBlock
            WHERE
                ReservedBlocksByBlock.UsedCores
                >
                (
                    COALESCE(
                        (
                        SELECT
                            SUM(Allocations.cores)
                        FROM
                            AbstractComputeAllocations AS Allocations
                        WHERE
                            Allocations.pool = :pool
                        AND
                            Allocations.uuid <> :excluded
                        AND
                            (Allocations.startseconds / :blockstep) <= ReservedBlocksByBlock.BlockStart
                        AND
                            (
                                Allocations.endseconds IS NULL
                            OR
                                ((Allocations.endseconds + (:blockstep - 1)) / :blockstep) > ReservedBlocksByBlock.BlockStart
                            )
                        ), 0
                    )
                    +
                    CASE
                        WHEN (:startseconds / :blockstep) <= ReservedBlocksByBlock.BlockStart
                        AND  (:endseconds IS NULL OR ((:endseconds + (:blockstep - 1)) / :blockstep) > ReservedBlocksByBlock.BlockStart)
                        THEN :cores
                        ELSE 0
                        END
                )
            OR
                ReservedBlocksByBlock.UsedMemory
                >
                (
                    COALESCE(
                        (
                        SELECT
                            SUM(Allocations.memory)
                        FROM
                            AbstractComputeAllocations AS Allocations
                        WHERE
                            Allocations.pool = :pool
                        AND
                            Allocations.uuid <> :excluded
                        AND
                            (Allocations.startseconds / :blockstep) <= ReservedBlocksByBlock.BlockStart
                        AND
                            (
                                Allocations.endseconds IS NULL
                            OR
                                ((Allocations.endseconds + (:blockstep - 1)) / :blockstep) > ReservedBlocksByBlock.BlockStart
                            )
                        ), 0
                    )
                    +
                    CASE
                        WHEN (:startseconds / :blockstep) <= ReservedBlocksByBlock.BlockStart
                        AND  (:endseconds IS NULL OR ((:endseconds + (:blockstep - 1)) / :blockstep) > ReservedBlocksByBlock.BlockStart)
                        THEN :memory
                        ELSE 0
                        END
                )
            )
        SELECT
            COUNT(*) AS OverCommittedBlocks
        FROM
            OverCommitted
        """;

    /**
     * Build the query for one candidate allocation change.
     *
     * The candidate values are substituted as literals, including NULL for an
     * open end. The excluded allocation is the row the candidate replaces; for
     * a removal it is the row being removed and the sizes are zero.
     *
     */
    public static String build(
        final UUID poolUuid,
        final UUID excludedUuid,
        final long cores,
        final long memory,
        final Instant start,
        final Instant end
        ){
        String query = new String(DATABASE_QUERY);

        query = query.replace(
            ":blockstep",
            String.valueOf(SimpleComputeReservationQuery.BLOCK_STEP_SECONDS)
            );
        query = query.replace(
            ":pool",
            "'" + poolUuid.toString() + "'"
            );
        query = query.replace(
            ":excluded",
            "'" + excludedUuid.toString() + "'"
            );
        query = query.replace(
            ":cores",
            String.valueOf(cores)
            );
        query = query.replace(
            ":memory",
            String.valueOf(memory)
            );
        query = query.replace(
            ":startseconds",
            String.valueOf(start.getEpochSecond())
            );
        if (end == null)
            {
            query = query.replace(
                ":endseconds",
                "NULL"
                );
            }
        else {
            query = query.replace(
                ":endseconds",
                String.valueOf(end.getEpochSecond())
                );
            }
        return query;
        }

    }
