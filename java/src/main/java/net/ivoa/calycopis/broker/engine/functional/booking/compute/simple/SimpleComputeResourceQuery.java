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
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-11T13:32:00/2026-10-11T13:35:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-13T10:19:00/2026-10-13T10:35:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 14,
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
 *       "value": 40,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.time.Duration;
import java.time.Instant;

import org.threeten.extra.Interval;

import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;

/**
 * Resources for the SimpleComputeResource database query.
 *
 */
public interface SimpleComputeResourceQuery
    {

    /**
     * The the granularity of block steps in the database.
     * Set to 60 seconds.
     *
     */
    public static final Long BLOCK_STEP_SECONDS  = 60L ;

    /**
     * How far to look ahead in the schedule.
     * Set to 2 hours.
     * TODO This should be set by the Duration of the requested start Interval.
     *
     */
    public static final Long BLOCK_RANGE_SECONDS = 2L * 60L * 60L;

    /**
     * The default start range if none is specified in the request.
     * Set to 5 minutes.
     *
     */
    public static final Duration DEFAULT_START_RANGE = Duration.ofMinutes(5);

    /**
     * The default execution duration.
     * Set to 2 hours.
     *
     */
    public static final Duration DEFAULT_DURATION = Duration.ofHours(2);

    /**
     * How much more time we are allowed to offer over the requested duration.
     * Set to twice the requested value.
     *
     */
    public static final int OFFER_DURATION_SCALE = 2;

    /**
     * The maximum execution duration we are allowed to offer.
     * Set to 4 hours.
     *
     */
    public static final Duration MAXIMUM_DURATION = Duration.ofHours(4);

    /**
     * The default number of CPU cores if none is specified in the request.
     * Set to 1 core.
     *
     */
    public static final Long DEFAULT_CPU_CORES_REQUEST = 1L ;

    /**
     * How many more CPU cores we are allowed to offer over the requested number.
     * Set to twice the requested value.
     *
     */
    public static final int OFFER_CPU_CORES_SCALE = 2;

    /**
     * The default amount of CPU memory.
     * Set to 1 GiB.
     *
     */
    public static final Long DEFAULT_CPU_MEMORY_REQUEST = 1L ;

    /**
     * How much more memory we are allowed to offer over the requested amount.
     * Set to twice the requested value.
     *
     */
    public static final int OFFER_CPU_MEMORY_SCALE = 2;

    /**
     * The number of rows in each category to select.
     * Set to 4 rows.
     *
     */
    public static final int QUERY_LIMIT = 4;

    /**
     * The database query.
     * The booking blocks are derived from each compute resource's own
     * reservation window, the hull of its preparing, available and releasing
     * windows, rather than from its parent session timing.
     * https://github.com/uksrc/Calycopis-broker/issues/149
     * https://github.com/uksrc/Calycopis-broker/issues/158
     *
     */
    public static final String DATABASE_QUERY =
        """
        WITH ExecutionBlocks AS
            (
            --
            -- One row per existing compute resource, covering the blocks
            -- spanned by the resource's reservation.
            --
            -- The reservation is the hull of the three planned windows, for
            -- preparing, available and releasing, because the resource is
            -- held for all three. Counting only the available window let two
            -- sessions with adjacent available windows pass the query while
            -- overlapping in reality: one session's preparing, or the
            -- other's releasing, fell outside the window that was counted.
            -- See issue #158.
            --
            -- The hull is derived from the existing columns rather than
            -- stored, so there is one definition of the reservation. If the
            -- allocation model in issue #32 stores it, it should be derived
            -- to match this.
            --
            -- The parent session is joined only to filter out offers that
            -- are no longer live. A compute resource whose offer was
            -- REJECTED or EXPIRED stays in INITIALIZING (releasing an
            -- INITIALIZING component is a no-op), so the component phase
            -- alone cannot distinguish a live offer from a dead one.
            --
            -- The hull is rounded out to whole blocks, so a window that does
            -- not sit on block boundaries still reserves every block it
            -- touches. An offer that starts at the requested time, rather
            -- than on a block boundary, needs this.
            --
            SELECT
                LifecycleComponents.phase AS BlockPhase,
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
                -
                    (
                    least(
                        LifecycleComponents.prepare_start_instant_seconds,
                        LifecycleComponents.available_start_instant_seconds,
                        LifecycleComponents.release_start_instant_seconds
                        ) / :blockstep
                    )
                ) AS BlockLength,
                COALESCE(SimpleComputeResources.maxofferedcores,  SimpleComputeResources.maxrequestedcores)  AS UsedCores,
                COALESCE(SimpleComputeResources.maxofferedmemory, SimpleComputeResources.maxrequestedmemory) AS UsedMemory
            FROM
                AbstractComputeResources
            JOIN
                LifecycleComponents
            ON
                LifecycleComponents.uuid = AbstractComputeResources.uuid
            JOIN
                SimpleComputeResources
            ON
                SimpleComputeResources.uuid = AbstractComputeResources.uuid
            JOIN
                SimpleExecutionSessions
            ON
                SimpleExecutionSessions.uuid = AbstractComputeResources.session
            WHERE
                SimpleExecutionSessions.phase IN ('OFFERED', 'ACCEPTED', 'WAITING', 'PREPARING', 'AVAILABLE', 'RUNNING', 'RELEASING')
            ),
        AvailableBlocks AS
            (
            SELECT
                StartRange.StartRow AS StartRow,
                (:totalcores  - COALESCE(sum(ExecutionBlocks.UsedCores),  0)) AS FreeCores,
                (:totalmemory - COALESCE(sum(ExecutionBlocks.UsedMemory), 0)) AS FreeMemory
            FROM
                (
                SELECT
                    generate_series + :rangeoffset AS StartRow
                FROM
                     generate_series(:rangestart, :rangeend)
                ) AS StartRange
            LEFT OUTER JOIN
                ExecutionBlocks
            ON  (
                    (ExecutionBlocks.BlockStart <= StartRange.StartRow)
                AND
                    ((ExecutionBlocks.BlockStart + ExecutionBlocks.BlockLength) > StartRange.StartRow)
                )
            GROUP BY
                StartRange.StartRow
            ),
        ConsecutiveBlocks AS (
            SELECT
                AvailableBlocks.StartRow,
                (AvailableBlocks.StartRow + 1) -
                    (
                    ROW_NUMBER() OVER (
                        PARTITION BY (
                            AvailableBlocks.FreeCores  >= :mincores
                            AND
                            AvailableBlocks.FreeMemory >= :minmemory
                            )
                        ORDER BY AvailableBlocks.StartRow
                        )
                    ) AS BlockGroup,
                FreeCores,
                FreeMemory
            FROM
                AvailableBlocks
            WHERE
                AvailableBlocks.FreeCores  >= :mincores
                AND
                AvailableBlocks.FreeMemory >= :minmemory
            ),
        CombinedBlocks AS (
            SELECT
                COUNT(*) AS BlockLength,
                MIN(ConsecutiveBlocks.StartRow)   AS BlockStart,
                MIN(ConsecutiveBlocks.FreeCores)  AS FreeCores,
                MIN(ConsecutiveBlocks.FreeMemory) AS FreeMemory
            FROM
                ConsecutiveBlocks
            WHERE
                ConsecutiveBlocks.BlockGroup IS NOT NULL
            GROUP BY
                ConsecutiveBlocks.BlockGroup
            HAVING
                COUNT(*) >= :minblocklength
            ),
        SplitBlocks AS (
            SELECT
                (CombinedBlocks.BlockStart + (:maxblocklength * (n - 1))) AS BlockStart,
                LEAST(
                    :maxblocklength,
                    (CombinedBlocks.BlockLength - (:maxblocklength * (n - 1)))
                    ) AS BlockLength,
                CombinedBlocks.FreeCores  AS FreeCores,
                CombinedBlocks.FreeMemory AS FreeMemory
            FROM
                CombinedBlocks,
                (
                SELECT
                    generate_series AS n
                FROM
                    generate_series(1, :maxblocklength)
                ) AS Numbers
            WHERE
                (CombinedBlocks.BlockStart + (:maxblocklength * (n - 1))) < (BlockStart + BlockLength)
            ),
        MatchingBlocks AS (
            SELECT
                AvailableBlocks.StartRow,
                SplitBlocks.BlockStart,
                SplitBlocks.BlockLength,
                SplitBlocks.FreeCores,
                SplitBlocks.FreeMemory
            FROM
                AvailableBlocks
            CROSS JOIN
                SplitBlocks
            WHERE
                AvailableBlocks.StartRow >= SplitBlocks.BlockStart
            AND
                AvailableBlocks.StartRow < (SplitBlocks.BlockStart + SplitBlocks.BlockLength)
            AND
                SplitBlocks.BlockLength >= :minblocklength
            AND
                SplitBlocks.BlockLength <= :maxblocklength
            ),
        GroupedBlocks AS (
            SELECT
                MatchingBlocks.BlockStart,
                MatchingBlocks.BlockLength,
                MIN(MatchingBlocks.FreeCores)  AS FreeCores,
                MIN(MatchingBlocks.FreeMemory) AS FreeMemory
            FROM
                MatchingBlocks
            GROUP BY
                MatchingBlocks.BlockStart,
                MatchingBlocks.BlockLength
            ),
        ScaledBlocks AS (
            SELECT
                GroupedBlocks.BlockStart,
                GroupedBlocks.BlockLength,
                LEAST(
                    :maxcores,
                    GroupedBlocks.FreeCores
                    ) AS BlockCores,
                LEAST(
                    :maxmemory,
                    GroupedBlocks.FreeMemory
                    ) AS BlockMemory
            FROM
                GroupedBlocks
            ),
        EarlyBlocks AS (
            SELECT
                *
            FROM
                ScaledBlocks
            ORDER BY
                ScaledBlocks.BlockStart    ASC,
                ScaledBlocks.BlockCores    DESC,
                ScaledBlocks.BlockMemory   DESC,
                ScaledBlocks.BlockLength   DESC
            LIMIT :querylimit
            )

        SELECT * FROM EarlyBlocks

        """;

    /**
     * Resolve the start of the booking window.
     * This applies the same default as build().
     * An offer start time that is before this time is moved forward to it,
     * so the offered window never starts before the requested start time.
     *
     */
    public static Instant getRangeStartInstant(final Interval requestStart)
        {
        // If no start time, use the current time.
        if (requestStart == null)
            {
            return Instant.now();
            }
        return requestStart.getStart();
        }

    /**
     * Apply defaults and parameters to build a database query, using the
     * default platform capacity.
     *
     * @see #build(Interval, Duration, Long, Long, int, long, long)
     *
     */
    public static String build(
        Interval requestStart,
        Duration requestDuration,
        Long requestMinCores,
        Long requestMemory,
        int requestLimit
        ){
        return build(
            requestStart,
            requestDuration,
            requestMinCores,
            requestMemory,
            requestLimit,
            BookingCapacitySettings.DEFAULT_TOTAL_CORES,
            BookingCapacitySettings.DEFAULT_TOTAL_MEMORY
            );
        }

    /**
     * Apply defaults and parameters to build a database query, with an explicit
     * total capacity.
     *
     * The capacity is the total amount of compute resource the platform
     * provides. The query compares it against the resources reserved by live
     * sessions to work out what is free in each block of time. The values are
     * bound from the calycopis.broker.booking.capacity section of the
     * application configuration, and default to
     * {@link BookingCapacitySettings#DEFAULT_TOTAL_CORES} cores and
     * {@link BookingCapacitySettings#DEFAULT_TOTAL_MEMORY} GiB of memory.
     *
     */
    public static String build(
        Interval requestStart,
        Duration requestDuration,
        Long requestMinCores,
        Long requestMemory,
        int requestLimit,
        final long totalCores,
        final long totalMemory
        ){
        String query = new String(DATABASE_QUERY);

        // Resolve the start of the booking window.
        Instant rangeStart = getRangeStartInstant(requestStart);

        // If no start time, use the default.
        if (requestStart == null)
            {
            requestStart = Interval.of(
                rangeStart,
                DEFAULT_START_RANGE
                );
            }

        // If no start end, use the default.
        if (requestStart.getEnd() == Instant.MAX)
            {
            requestStart = Interval.of(
                requestStart.getStart(),
                DEFAULT_START_RANGE
                );
            }

        // If no duration, use the default.
        if (requestDuration == null)
            {
            requestDuration = DEFAULT_DURATION;
            }

        // TODO Check for maximum duration.

        // If no minimum cores, use the default.
        if (requestMinCores == null)
            {
            requestMinCores = DEFAULT_CPU_CORES_REQUEST;
            }

        // TODO Check for maximum cores.

        // If no minimum memort, use the default.
        if (requestMemory == null)
            {
            requestMemory = DEFAULT_CPU_MEMORY_REQUEST;
            }

        // TODO Check for maximum memory.

        // Calculate the maximum duration.
        Duration maxduration = Duration.ofSeconds(
            requestDuration.getSeconds() * OFFER_DURATION_SCALE
            );
        if (maxduration.getSeconds() >= MAXIMUM_DURATION.getSeconds())
            {
            maxduration = MAXIMUM_DURATION;
            }

        // This is vulnerable to SQL injection.
        // Ideally we should use JdbcTemplate named parameters,m but that didn't work.
        // TODO Move the parameter replacement into Spring JdbcTemplate.

        query = query.replace(":blockstep",   String.valueOf(BLOCK_STEP_SECONDS));
        query = query.replace(":totalcores",  String.valueOf(totalCores));
        query = query.replace(":totalmemory", String.valueOf(totalMemory));
        query = query.replace(":rangeoffset", String.valueOf(
            rangeStart.getEpochSecond() / BLOCK_STEP_SECONDS
            ));
        //
        // Start the search at the block that contains the requested start
        // time, rather than at the block after it. The offer post-processing
        // moves a start time that is before the requested start time forward,
        // so an immediate request can be offered the current block.
        //
        query = query.replace(":rangestart",  String.valueOf(0));
        query = query.replace(":rangeend",    String.valueOf(
            BLOCK_RANGE_SECONDS  / BLOCK_STEP_SECONDS
            ));
        query = query.replace(":mincores",   String.valueOf(requestMinCores));
        query = query.replace(":minmemory",  String.valueOf(requestMemory));
        //
        // Round the requested and maximum durations up to whole blocks, with a
        // minimum of one block, so the offered duration always covers the
        // requested duration.
        //
        // Add one block to the minimum, to compensate for the block that
        // contains the requested start time. The offer post-processing moves
        // the start of that block forward to the requested start time, which
        // shortens the offered duration by less than one block, so an
        // allocation that starts in the current block needs one more block to
        // still cover the full requested duration.
        //
        // The maximum is not increased, so MAXIMUM_DURATION still caps the
        // longest offer, but it must never be smaller than the minimum.
        //
        long minBlockLength = Math.max(1L,
            (requestDuration.getSeconds() + BLOCK_STEP_SECONDS - 1) / BLOCK_STEP_SECONDS
            ) + 1L;
        long maxBlockLength = Math.max(1L,
            (maxduration.getSeconds() + BLOCK_STEP_SECONDS - 1) / BLOCK_STEP_SECONDS
            );
        maxBlockLength = Math.max(minBlockLength, maxBlockLength);

        query = query.replace(":minblocklength", String.valueOf(minBlockLength));
        query = query.replace(":maxblocklength", String.valueOf(maxBlockLength));

        query = query.replace(":maxcores", String.valueOf(
            requestMinCores * OFFER_CPU_CORES_SCALE
            ));
        query = query.replace(":maxmemory", String.valueOf(
            requestMemory * OFFER_CPU_MEMORY_SCALE
            ));
        if (requestLimit > 0)
            {
            query = query.replace(":querylimit", String.valueOf(
                requestLimit
                ));
            }
        else {
            query = query.replace(":querylimit", String.valueOf(
                QUERY_LIMIT
                ));
            }

        return query;
        }
    }
