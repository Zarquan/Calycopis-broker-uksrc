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
 *     },
 *     {
 *     "interval": "2026-11-18T14:55:00/2026-11-18T17:00:00",
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
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyKind;
import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.message.Message;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContextImpl;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOffer;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOfferBean;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceCriteria;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;

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
        private final OfferSetRequestParserContext request;
        private final long memory;
        private final Interval startInterval;

        private TestCriteria(final int offerLimit)
            {
            this(
                offerLimit,
                null,
                1L,
                Interval.of(
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Duration.ofMinutes(5)
                    )
                );
            }

        private TestCriteria(
            final int offerLimit,
            final OfferSetRequestParserContext request,
            final long memory,
            final Interval startInterval
            ){
            this.offerLimit = offerLimit;
            this.request = request;
            this.memory = memory;
            this.startInterval = startInterval;
            }

        @Override
        public OfferSetRequestParserContext getRequest()
            {
            return this.request;
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
            return this.startInterval;
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
            return this.memory;
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

    /**
     * A pool with no policies accepts everything and runs its query.
     *
     */
    @Test
    public void testPoolWithoutPoliciesRunsTheQuery()
        {
        TestPool pool = new TestPool(POOL_A_UUID, "pool-a");
        TestCriteria criteria = new TestCriteria(3);
        RecordingQueryHandler handler = new RecordingQueryHandler(
            makeQueryOffers(1)
            );

        List<SimpleComputeOffer> offers = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer offer : pool.makeOffers(criteria, handler))
            {
            offers.add(offer);
            }

        Assertions.assertEquals(
            1,
            offers.size(),
            "A pool with no policies must accept and run the query"
            );
        Assertions.assertEquals(
            1,
            handler.getQueries().size(),
            "The pool must run its query once"
            );
        }

    /**
     * A policy that accepts lets the pool run its query.
     *
     */
    @Test
    public void testAcceptingPolicyRunsTheQuery()
        {
        TestPool pool = new TestPool(POOL_A_UUID, "pool-a");
        pool.addPolicy(
            new SimpleComputePoolPolicyEntity(
                pool,
                SimpleComputePoolPolicyKind.MEMORY_FLOOR,
                "floor",
                1L,
                null,
                null,
                null,
                null
                )
            );
        OfferSetRequestParserContext request = new OfferSetRequestParserContextImpl(
            new IvoaExecutionRequest(),
            new IdentityEntity("alice", null)
            );
        TestCriteria criteria = new TestCriteria(
            3,
            request,
            1L,
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                )
            );
        RecordingQueryHandler handler = new RecordingQueryHandler(
            makeQueryOffers(1)
            );

        List<SimpleComputeOffer> offers = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer offer : pool.makeOffers(criteria, handler))
            {
            offers.add(offer);
            }

        Assertions.assertEquals(
            1,
            offers.size(),
            "An accepting policy must let the pool make offers"
            );
        Assertions.assertEquals(
            1,
            handler.getQueries().size(),
            "An accepting policy must let the pool run its query"
            );
        Assertions.assertSame(
            pool,
            offers.get(0).getPool(),
            "The offer must carry the pool that made it"
            );
        }

    /**
     * A declining policy leaves the query uncalled and records the pool message
     * followed by the policy reason.
     *
     */
    @Test
    public void testDecliningPolicySkipsTheQueryAndRecordsMessages()
        {
        TestPool pool = new TestPool(POOL_A_UUID, "pool-a");
        pool.addPolicy(
            new SimpleComputePoolPolicyEntity(
                pool,
                SimpleComputePoolPolicyKind.MEMORY_FLOOR,
                "floor",
                10L,
                null,
                null,
                null,
                null
                )
            );
        OfferSetRequestParserContext request = new OfferSetRequestParserContextImpl(
            new IvoaExecutionRequest(),
            new IdentityEntity("alice", null)
            );
        TestCriteria criteria = new TestCriteria(
            3,
            request,
            1L,
            Interval.of(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofMinutes(5)
                )
            );
        RecordingQueryHandler handler = new RecordingQueryHandler(
            makeQueryOffers(1)
            );

        List<SimpleComputeOffer> offers = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer offer : pool.makeOffers(criteria, handler))
            {
            offers.add(offer);
            }

        Assertions.assertTrue(
            offers.isEmpty(),
            "A declining policy must leave the pool with no offers"
            );
        Assertions.assertTrue(
            handler.getQueries().isEmpty(),
            "A declining policy must stop the pool before it runs a query"
            );

        List<Message> messages = new ArrayList<Message>();
        for (Message message : request.getMessages())
            {
            messages.add(message);
            }
        Assertions.assertEquals(
            2,
            messages.size(),
            "A decline must record one pool message and one policy reason"
            );
        Assertions.assertEquals(
            "urn:compute-pool-declined",
            messages.get(0).getType(),
            "The first message must name the declining pool"
            );
        Assertions.assertTrue(
            messages.get(0).getTemplate().contains("pool-a"),
            "The pool message must name the pool"
            );
        Assertions.assertTrue(
            messages.get(0).getTemplate().contains("floor"),
            "The pool message must name the policy"
            );
        Assertions.assertEquals(
            "urn:compute-policy-memory-floor",
            messages.get(1).getType(),
            "The second message must be the policy reason"
            );
        Assertions.assertTrue(
            messages.get(1).getTemplate().contains("10"),
            "The reason must carry the floor value"
            );
        }
    }
