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
 * Unit tests for the SimpleComputeResourceOfferBean start time clamp.
 *
 */
public class SimpleComputeResourceOfferBeanTest
    {

    /**
     * The start of our test blocks.
     * 2026-01-01T00:00:00Z.
     *
     */
    private static final long BLOCK_START = 1767225600L;

    /**
     * Create a raw offer for a block of whole seconds.
     *
     */
    private SimpleComputeResourceOfferBean makeOffer(final long lengthSeconds)
        {
        return new SimpleComputeResourceOfferBean(
            "offer-0",
            Interval.of(
                Instant.ofEpochSecond(BLOCK_START),
                Duration.ofSeconds(0)
                ),
            Duration.ofSeconds(lengthSeconds),
            1L,
            1L
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
        SimpleComputeResourceOfferBean offer = SimpleComputeResourceOfferBean.clamp(
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
        SimpleComputeResourceOfferBean future = SimpleComputeResourceOfferBean.clamp(
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

        SimpleComputeResourceOfferBean exact = SimpleComputeResourceOfferBean.clamp(
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
        SimpleComputeResourceOfferBean offer = SimpleComputeResourceOfferBean.clamp(
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
        SimpleComputeResourceOfferBean offer = SimpleComputeResourceOfferBean.clamp(
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
    }
