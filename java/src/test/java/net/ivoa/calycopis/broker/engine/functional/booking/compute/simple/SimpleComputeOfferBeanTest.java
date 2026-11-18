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
 *     "timestamp": "2026-10-13T10:22:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-15T10:30:00/2026-10-15T17:00:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-17T18:30:00/2026-11-17T18:50:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 5,
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

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;

/**
 * Unit tests for the SimpleComputeOfferBean start time clamp.
 *
 */
public class SimpleComputeOfferBeanTest
    {

    /**
     * The start of our test blocks.
     * 2026-01-01T00:00:00Z.
     *
     */
    private static final long BLOCK_START = 1767225600L;

    /**
     * The test pool every offer belongs to.
     *
     */
    private final SimpleComputeResourcePoolEntity pool = new SimpleComputeResourcePoolEntity(
        "test-pool",
        "test-pool"
        );

    /**
     * Create a raw offer for a block of whole seconds.
     *
     */
    private SimpleComputeOfferBean makeOffer(final long lengthSeconds)
        {
        return new SimpleComputeOfferBean(
            "offer-0",
            Interval.of(
                Instant.ofEpochSecond(BLOCK_START),
                Duration.ofSeconds(0)
                ),
            Duration.ofSeconds(lengthSeconds),
            1L,
            1L,
            this.pool
            );
        }

    /**
     * A start time that is before the earliest start time must be moved
     * forward to the earliest start time, and the end must not move.
     *
     */
    @Test
    public void testClampMovesStartForwardToEarliestStart()
        {
        SimpleComputeOfferBean offer = SimpleComputeOfferBean.clamp(
            makeOffer(360L),
            Instant.ofEpochSecond(BLOCK_START + 58L)
            );
        Assertions.assertEquals(
            Instant.ofEpochSecond(BLOCK_START + 58L),
            offer.getOfferStartInstant(),
            "The offer start must move forward to the earliest start time"
            );
        Assertions.assertEquals(
            Duration.ofSeconds(302L),
            offer.getOfferDuration(),
            "The offer duration must shorten by the amount the start moved"
            );
        Assertions.assertEquals(
            Instant.ofEpochSecond(BLOCK_START + 360L),
            offer.getOfferStartInstant().plus(offer.getOfferDuration()),
            "The end of the offer must stay where the query placed it"
            );
        Assertions.assertEquals(
            "offer-0",
            offer.getName(),
            "The offer name must be preserved"
            );
        }

    /**
     * A start time that is not before the earliest start time must be left
     * alone.
     *
     */
    @Test
    public void testClampLeavesFutureStartUnchanged()
        {
        SimpleComputeOfferBean future = SimpleComputeOfferBean.clamp(
            makeOffer(360L),
            Instant.ofEpochSecond(BLOCK_START - 10L)
            );
        Assertions.assertEquals(
            Instant.ofEpochSecond(BLOCK_START),
            future.getOfferStartInstant(),
            "An offer that starts after the earliest start time must not move"
            );
        Assertions.assertEquals(
            Duration.ofSeconds(360L),
            future.getOfferDuration(),
            "An offer that starts after the earliest start time must keep its duration"
            );

        SimpleComputeOfferBean exact = SimpleComputeOfferBean.clamp(
            makeOffer(360L),
            Instant.ofEpochSecond(BLOCK_START)
            );
        Assertions.assertEquals(
            Instant.ofEpochSecond(BLOCK_START),
            exact.getOfferStartInstant(),
            "An offer that starts at the earliest start time must not move"
            );
        Assertions.assertEquals(
            Duration.ofSeconds(360L),
            exact.getOfferDuration(),
            "An offer that starts at the earliest start time must keep its duration"
            );
        }

    /**
     * The worst case clamp must still cover the requested duration.
     * Six blocks is a five minute request plus the one compensation block.
     *
     */
    @Test
    public void testClampCoversTheRequestedDuration()
        {
        SimpleComputeOfferBean offer = SimpleComputeOfferBean.clamp(
            makeOffer(6L * 60L),
            Instant.ofEpochSecond(BLOCK_START + 59L)
            );
        Assertions.assertTrue(
            offer.getOfferDuration().getSeconds() >= 300L,
            "The clamped offer must still cover the five minute request"
            );
        }

    /**
     * A block that has already ended must be left alone, rather than
     * extended past the free run the query found.
     *
     */
    @Test
    public void testClampLeavesExpiredBlockUnchanged()
        {
        SimpleComputeOfferBean offer = SimpleComputeOfferBean.clamp(
            makeOffer(360L),
            Instant.ofEpochSecond(BLOCK_START + 360L)
            );
        Assertions.assertEquals(
            Instant.ofEpochSecond(BLOCK_START),
            offer.getOfferStartInstant(),
            "An offer whose block has ended must not be moved past its end"
            );
        Assertions.assertEquals(
            Duration.ofSeconds(360L),
            offer.getOfferDuration(),
            "An offer whose block has ended must keep its duration"
            );
        }

    /**
     * Clamping must preserve the pool, and the pool copy must preserve the
     * offer's sizes and window.
     *
     */
    @Test
    public void testPoolIsPreservedAndCopied()
        {
        SimpleComputeOfferBean offer = SimpleComputeOfferBean.clamp(
            makeOffer(360L),
            Instant.ofEpochSecond(BLOCK_START + 58L)
            );
        Assertions.assertSame(
            this.pool,
            offer.getPool(),
            "The clamped offer must keep the pool it came from"
            );

        SimpleComputeOfferBean copied = SimpleComputeOfferBean.withPool(
            offer,
            this.pool
            );
        Assertions.assertSame(
            this.pool,
            copied.getPool(),
            "The copied offer must carry the requested pool"
            );
        Assertions.assertEquals(
            offer.getMinCores(),
            copied.getMinCores(),
            "The copied offer must keep its minimum cores"
            );
        Assertions.assertEquals(
            offer.getMaxCores(),
            copied.getMaxCores(),
            "The copied offer must keep its maximum cores"
            );
        Assertions.assertEquals(
            offer.getMinMemory(),
            copied.getMinMemory(),
            "The copied offer must keep its minimum memory"
            );
        Assertions.assertEquals(
            offer.getMaxMemory(),
            copied.getMaxMemory(),
            "The copied offer must keep its maximum memory"
            );
        Assertions.assertEquals(
            offer.getOfferStartInstant(),
            copied.getOfferStartInstant(),
            "The copied offer must keep its start"
            );
        Assertions.assertEquals(
            offer.getOfferDuration(),
            copied.getOfferDuration(),
            "The copied offer must keep its duration"
            );
        }

    /**
     * An offer whose whole window is before the earliest allowed start time has
     * ended, and the offer factory drops it.
     *
     */
    @Test
    public void testHasEndedForAWindowInThePast()
        {
        Assertions.assertFalse(
            SimpleComputeOfferBean.hasEnded(
                makeOffer(360L),
                Instant.ofEpochSecond(BLOCK_START + 359L)
                ),
            "A window that ends after the earliest start time is still open"
            );
        Assertions.assertTrue(
            SimpleComputeOfferBean.hasEnded(
                makeOffer(360L),
                Instant.ofEpochSecond(BLOCK_START + 360L)
                ),
            "A window that ends at the earliest start time has already ended"
            );
        Assertions.assertTrue(
            SimpleComputeOfferBean.hasEnded(
                makeOffer(360L),
                Instant.ofEpochSecond(BLOCK_START + 361L)
                ),
            "A window that ends before the earliest start time has already ended"
            );
        }

    /**
     * An offer that starts at or after the end of the requested start window is
     * outside it, and the offer factory drops it.
     *
     */
    @Test
    public void testStartsOutsideWindow()
        {
        Assertions.assertFalse(
            SimpleComputeOfferBean.startsOutsideWindow(
                makeOffer(360L),
                Instant.ofEpochSecond(BLOCK_START + 1L)
                ),
            "An offer that starts before the window end is inside the window"
            );
        Assertions.assertTrue(
            SimpleComputeOfferBean.startsOutsideWindow(
                makeOffer(360L),
                Instant.ofEpochSecond(BLOCK_START)
                ),
            "An offer that starts at the window end is outside the window"
            );
        Assertions.assertTrue(
            SimpleComputeOfferBean.startsOutsideWindow(
                makeOffer(360L),
                Instant.ofEpochSecond(BLOCK_START - 1L)
                ),
            "An offer that starts after the window has closed is outside it"
            );
        Assertions.assertFalse(
            SimpleComputeOfferBean.startsOutsideWindow(
                makeOffer(360L),
                Instant.MAX
                ),
            "An open ended window has no end to be outside of"
            );
        }
    }
