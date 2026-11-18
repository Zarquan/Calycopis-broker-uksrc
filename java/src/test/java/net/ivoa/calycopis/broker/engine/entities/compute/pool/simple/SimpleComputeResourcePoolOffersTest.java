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
 *     "timestamp": "2026-11-18T12:05:00",
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

package net.ivoa.calycopis.broker.engine.entities.compute.pool.simple;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;

import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationValidator;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOffer;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOfferBean;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceCriteria;

/**
 * Unit tests for the pool's makeOffers method.
 *
 */
public class SimpleComputeResourcePoolOffersTest
    {

    private static final UUID POOL_A_UUID = UUID.fromString(
        "00000000-0000-4000-8000-00000000000a"
        );

    private static final UUID POOL_B_UUID = UUID.fromString(
        "00000000-0000-4000-8000-00000000000b"
        );

    /**
     * A test pool with a persisted identifier.
     *
     */
    private static class TestPool
    extends SimpleComputeResourcePoolEntity
        {
        private TestPool(final UUID uuid, final String name)
            {
            super(
                name,
                name
                );
            this.uuid = uuid;
            }
        }

    /**
     * Criteria with fixed values, so the test does not need a request context.
     *
     */
    private static class TestCriteria
    implements SimpleComputeResourceCriteria
        {
        private final int offerLimit;

        private TestCriteria(final int offerLimit)
            {
            this.offerLimit = offerLimit;
            }

        @Override
        public OfferSetRequestParserContext getRequest()
            {
            return null;
            }

        @Override
        public AbstractComputeReservationValidator.Result getResource()
            {
            return null;
            }

        @Override
        public int getOfferLimit()
            {
            return this.offerLimit;
            }

        @Override
        public long getPrepareSeconds()
            {
            return 0L;
            }

        @Override
        public Interval getStartInterval()
            {
            return Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                );
            }

        @Override
        public Duration getExecutionDuration()
            {
            return Duration.ofHours(2);
            }

        @Override
        public long getMinCores()
            {
            return 1L;
            }

        @Override
        public long getMemory()
            {
            return 1L;
            }
        }

    /**
     * A query handler that records the query and returns fixed offers.
     *
     */
    private static class RecordingQueryHandler
    implements SimpleComputeReservationQueryHandler
        {
        private final List<SimpleComputeOffer> offers;
        private final List<String> queries = new ArrayList<String>();

        private RecordingQueryHandler(final List<SimpleComputeOffer> offers)
            {
            this.offers = offers;
            }

        public List<String> getQueries()
            {
            return this.queries;
            }

        @Override
        public UUID getUuid()
            {
            return UUID.randomUUID();
            }

        @Override
        public Iterable<SimpleComputeOffer> query(final String query)
            {
            this.queries.add(query);
            return this.offers;
            }

        @Override
        public Iterable<SimpleComputeOffer> query(final String query, final Map<String, Object> params)
            {
            this.queries.add(query);
            return this.offers;
            }
        }

    /**
     * A raw offer, without a pool, as a row mapper would build it.
     *
     */
    private static class RawOffer
    extends SimpleComputeOfferBean
        {
        private RawOffer(final String name, final Instant start)
            {
            super(
                name,
                Interval.of(
                    start,
                    Duration.ofSeconds(0)
                    ),
                Duration.ofHours(2),
                1L,
                1L
                );
            }
        }

    /**
     * Build raw offers for the query handler to return.
     *
     * The number of rows stands in for the free capacity the query found.
     *
     */
    private List<SimpleComputeOffer> makeQueryOffers(final int count)
        {
        List<SimpleComputeOffer> offers = new ArrayList<SimpleComputeOffer>();
        Instant start = Instant.parse("2026-01-01T00:10:00Z");
        for (int index = 0; index < count; index++)
            {
            offers.add(
                new RawOffer(
                    "offer-" + index,
                    start.plus(Duration.ofHours(index))
                    )
                );
            }
        return offers;
        }

    /**
     * Two pools with different free capacity scope their queries to themselves,
     * apply the criteria limit, and stamp their own pool onto every offer they
     * return.
     *
     */
    @Test
    public void testMakeOffersScopesAndStampsThePool()
        {
        TestPool poolA = new TestPool(POOL_A_UUID, "pool-a");
        TestPool poolB = new TestPool(POOL_B_UUID, "pool-b");
        TestCriteria criteria = new TestCriteria(3);

        RecordingQueryHandler handlerA = new RecordingQueryHandler(
            makeQueryOffers(2)
            );
        RecordingQueryHandler handlerB = new RecordingQueryHandler(
            makeQueryOffers(1)
            );

        List<SimpleComputeOffer> offersA = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer offer : poolA.makeOffers(criteria, handlerA))
            {
            offersA.add(offer);
            }

        Assertions.assertEquals(
            2,
            offersA.size(),
            "The pool must return the query handler's offers"
            );
        for (SimpleComputeOffer offer : offersA)
            {
            Assertions.assertSame(
                poolA,
                offer.getPool(),
                "Every offer must be stamped with the pool that made it"
                );
            }

        Assertions.assertEquals(
            1,
            handlerA.getQueries().size(),
            "The pool must run its query once"
            );
        String queryA = handlerA.getQueries().get(0);
        Assertions.assertTrue(
            queryA.contains("Allocations.pool = '" + POOL_A_UUID + "'"),
            "The allocations must be scoped to the pool"
            );
        Assertions.assertTrue(
            queryA.contains("AbstractComputeReservations.pool = '" + POOL_A_UUID + "'"),
            "The reservations must be scoped to the pool"
            );
        Assertions.assertTrue(
            queryA.contains("LIMIT 3"),
            "The query limit must come from the criteria"
            );
        Assertions.assertFalse(
            queryA.contains(":querylimit"),
            "The query limit placeholder must be substituted"
            );

        List<SimpleComputeOffer> offersB = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer offer : poolB.makeOffers(criteria, handlerB))
            {
            offersB.add(offer);
            }

        Assertions.assertEquals(
            1,
            offersB.size(),
            "The second pool must return its own query's offers"
            );
        for (SimpleComputeOffer offer : offersB)
            {
            Assertions.assertSame(
                poolB,
                offer.getPool(),
                "The second pool must stamp its own offers"
                );
            }

        String queryB = handlerB.getQueries().get(0);
        Assertions.assertTrue(
            queryB.contains("Allocations.pool = '" + POOL_B_UUID + "'"),
            "The second pool must use its own allocation scope"
            );
        Assertions.assertTrue(
            queryB.contains("AbstractComputeReservations.pool = '" + POOL_B_UUID + "'"),
            "The second pool must use its own reservation scope"
            );
        Assertions.assertFalse(
            queryB.contains(POOL_A_UUID.toString()),
            "The second pool's query must not mention the first pool"
            );
        }
    }
