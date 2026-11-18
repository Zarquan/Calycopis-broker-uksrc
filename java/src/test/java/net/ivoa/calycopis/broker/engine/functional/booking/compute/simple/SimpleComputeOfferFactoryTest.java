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
 *     "interval": "2026-11-17T17:55:00/2026-11-17T20:30:00",
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

package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePool;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyKind;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContextImpl;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntry;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactory;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingTarget;
import net.ivoa.calycopis.broker.engine.functional.testing.TestingSettings;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQueryHandler;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;
/**
 * Unit tests for the offer factory's pool selection and post-processing.
 *
 */
public class SimpleComputeOfferFactoryTest
    {

    /**
     * A test pool with an identifier, as a persisted pool would have.
     *
     */
    private static class TestPool
    extends SimpleComputeResourcePoolEntity
        {
        private TestPool()
            {
            this(
                "test-pool",
                UUID.randomUUID()
                );
            }

        private TestPool(final String name, final UUID uuid)
            {
            super(
                name,
                name,
                0,
                true
                );
            this.uuid = uuid;
            }

        private void addFloorPolicy(final long minimumMemory)
            {
            this.addPolicy(
                new SimpleComputePoolPolicyEntity(
                    this,
                    SimpleComputePoolPolicyKind.MEMORY_FLOOR,
                    "floor",
                    minimumMemory,
                    null,
                    null,
                    null,
                    null
                    )
                );
            }
        }

    /**
     * The test pool every generated offer belongs to.
     *
     */
    private final TestPool pool = new TestPool();

    /**
     * A query handler that returns fixed offers, ignoring the SQL text.
     *
     */
    private static class StubQueryHandler
    implements SimpleComputeReservationQueryHandler
        {
        private final List<SimpleComputeOffer> offers;

        private StubQueryHandler(final List<SimpleComputeOffer> offers)
            {
            this.offers = offers;
            }

        @Override
        public UUID getUuid()
            {
            return UUID.randomUUID();
            }

        @Override
        public Iterable<SimpleComputeOffer> query(final String query)
            {
            return this.offers;
            }

        @Override
        public Iterable<SimpleComputeOffer> query(final String query, final Map<String, Object> params)
            {
            return this.offers;
            }
        }

    /**
     * A pool factory that returns one test pool, or none when asked to.
     *
     */
    private static class StubPoolFactory
    implements SimpleComputeResourcePoolEntityFactory
        {
        private final SimpleComputeResourcePoolEntity pool;
        private final boolean found;

        private StubPoolFactory(final SimpleComputeResourcePoolEntity pool, final boolean found)
            {
            this.pool = pool;
            this.found = found;
            }

        @Override
        public Optional<SimpleComputeResourcePoolEntity> select(final UUID uuid)
            {
            return Optional.empty();
            }

        @Override
        public Optional<SimpleComputeResourcePoolEntity> findByName(final String name)
            {
            if (!this.found)
                {
                return Optional.empty();
                }
            if (!AbstractComputeResourcePool.DEFAULT_POOL_NAME.equals(name))
                {
                return Optional.empty();
                }
            return Optional.of(this.pool);
            }

        @Override
        public List<SimpleComputeResourcePoolEntity> list()
            {
            List<SimpleComputeResourcePoolEntity> pools = new ArrayList<SimpleComputeResourcePoolEntity>();
            if (this.found)
                {
                pools.add(this.pool);
                }
            return pools;
            }

        @Override
        public List<SimpleComputeResourcePoolEntity> listEnabled()
            {
            return this.list();
            }

        @Override
        public SimpleComputeResourcePoolEntity create(final String name, final String description)
            {
            throw new UnsupportedOperationException(
                "The stub pool factory does not create pools"
                );
            }

        @Override
        public SimpleComputeResourcePoolEntity create(
            final String name,
            final String description,
            final int priority,
            final boolean enabled
            ){
            throw new UnsupportedOperationException(
                "The stub pool factory does not create pools"
                );
            }

        @Override
        public SimpleComputeResourcePoolEntity update(
            final SimpleComputeResourcePoolEntity entity,
            final int priority,
            final boolean enabled
            ){
            throw new UnsupportedOperationException(
                "The stub pool factory does not update pools"
                );
            }
        }

    /**
     * A pool factory that returns a fixed list of pools in offer order.
     *
     */
    private static class MultiPoolFactory
    implements SimpleComputeResourcePoolEntityFactory
        {
        private final List<SimpleComputeResourcePoolEntity> pools;

        private MultiPoolFactory(final List<SimpleComputeResourcePoolEntity> pools)
            {
            this.pools = pools;
            }

        @Override
        public Optional<SimpleComputeResourcePoolEntity> select(final UUID uuid)
            {
            return Optional.empty();
            }

        @Override
        public Optional<SimpleComputeResourcePoolEntity> findByName(final String name)
            {
            return Optional.empty();
            }

        @Override
        public List<SimpleComputeResourcePoolEntity> list()
            {
            return this.pools;
            }

        @Override
        public List<SimpleComputeResourcePoolEntity> listEnabled()
            {
            return this.pools;
            }

        @Override
        public SimpleComputeResourcePoolEntity create(final String name, final String description)
            {
            throw new UnsupportedOperationException(
                "The stub pool factory does not create pools"
                );
            }

        @Override
        public SimpleComputeResourcePoolEntity create(
            final String name,
            final String description,
            final int priority,
            final boolean enabled
            ){
            throw new UnsupportedOperationException(
                "The stub pool factory does not create pools"
                );
            }

        @Override
        public SimpleComputeResourcePoolEntity update(
            final SimpleComputeResourcePoolEntity entity,
            final int priority,
            final boolean enabled
            ){
            throw new UnsupportedOperationException(
                "The stub pool factory does not update pools"
                );
            }
        }

    /**
     * A query handler that returns a different list on each call, so a
     * multi-pool test can tell which pool asked.
     *
     */
    private static class SequenceQueryHandler
    implements SimpleComputeReservationQueryHandler
        {
        private final List<List<SimpleComputeOffer>> results;
        private final List<String> queries = new ArrayList<String>();

        private SequenceQueryHandler(final List<List<SimpleComputeOffer>> results)
            {
            this.results = results;
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
            int index = this.queries.size() - 1;
            if (index >= this.results.size())
                {
                return new ArrayList<SimpleComputeOffer>();
                }
            return this.results.get(index);
            }

        @Override
        public Iterable<SimpleComputeOffer> query(final String query, final Map<String, Object> params)
            {
            return this.query(query);
            }
        }

    /**
     * A profiling entry that records enough for the factory to use it.
     *
     */
    private static class StubProfilingEntry
    implements ProfilingEntry
        {
        private final String className;
        private final String methodName;
        private Instant startTime = Instant.now();
        private Instant endTime = null;

        private StubProfilingEntry(final String className, final String methodName)
            {
            this.className = className;
            this.methodName = methodName;
            }

        @Override
        public String getClassName()
            {
            return this.className;
            }

        @Override
        public String getMethodName()
            {
            return this.methodName;
            }

        @Override
        public Instant getStartTime()
            {
            return this.startTime;
            }

        @Override
        public Instant getEndTime()
            {
            return this.endTime;
            }

        @Override
        public void start()
            {
            this.startTime = Instant.now();
            }

        @Override
        public void end()
            {
            this.endTime = Instant.now();
            }
        }

    /**
     * A profiling factory that creates stub entries.
     *
     */
    private static class StubProfilingEntryFactory
    implements ProfilingEntryFactory
        {
        @Override
        public UUID getUuid()
            {
            return UUID.randomUUID();
            }

        @Override
        public ProfilingEntry create(final String className, final String methodName)
            {
            return new StubProfilingEntry(className, methodName);
            }
        }

    /**
     * A profiling target that collects the entries it is given.
     *
     */
    private static class StubProfilingTarget
    implements ProfilingTarget
        {
        private final List<ProfilingEntry> entries = new ArrayList<ProfilingEntry>();

        @Override
        public void addProfilingEntry(final ProfilingEntry entry)
            {
            this.entries.add(entry);
            }

        @Override
        public List<ProfilingEntry> getProfilingEntries()
            {
            return this.entries;
            }
        }

    /**
     * A testing-settings stub with the mechanism switched off.
     *
     */
    private static class StubTestingSettings
    implements TestingSettings
        {
        @Override
        public boolean isEnabled()
            {
            return false;
            }

        @Override
        public long getBookingQueryToSaveDelaySeconds()
            {
            return 0L;
            }
        }

    /**
     * Build a raw offer from the query handler, without a pool.
     *
     */
    private SimpleComputeOffer makeQueryOffer(final Instant start)
        {
        return new SimpleComputeOfferBean(
            "offer-0",
            Interval.of(
                start,
                Duration.ofSeconds(0)
                ),
            Duration.ofSeconds(360L),
            1L,
            1L
            );
        }

    /**
     * Generate offers through the factory and return them as a list.
     *
     */
    private List<SimpleComputeOffer> generate(
        final SimpleComputeResourcePoolEntityFactory poolFactory,
        final List<SimpleComputeOffer> queryOffers
        ){
        return this.generate(
            poolFactory,
            new StubQueryHandler(queryOffers),
            10
            );
        }

    /**
     * Generate offers through the factory with a chosen request limit.
     *
     */
    private List<SimpleComputeOffer> generate(
        final SimpleComputeResourcePoolEntityFactory poolFactory,
        final List<SimpleComputeOffer> queryOffers,
        final int requestLimit
        ){
        return this.generate(
            poolFactory,
            new StubQueryHandler(queryOffers),
            requestLimit
            );
        }

    /**
     * Generate offers through the factory with a chosen query handler and
     * request limit.
     *
     */
    private List<SimpleComputeOffer> generate(
        final SimpleComputeResourcePoolEntityFactory poolFactory,
        final SimpleComputeReservationQueryHandler queryHandler,
        final int requestLimit
        ){
        SimpleComputeOfferFactoryImpl factory = new SimpleComputeOfferFactoryImpl(
            new StubProfilingEntryFactory(),
            queryHandler,
            poolFactory,
            new StubTestingSettings()
            );
        OfferSetRequestParserContextImpl request = new OfferSetRequestParserContextImpl(
            new IvoaExecutionRequest(),
            null
            );
        request.setStartInterval(
            Interval.of(
                Instant.now().plusSeconds(3600L),
                Duration.ofSeconds(86400L)
                )
            );
        request.setExecutionDuration(
            Duration.ofSeconds(300L)
            );
        request.addMinCores(1L);
        request.addMinMemory(1L);

        Iterable<SimpleComputeOffer> generated = factory.generate(
            request,
            requestLimit,
            new StubProfilingTarget()
            );
        List<SimpleComputeOffer> offers = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer offer : generated)
            {
            offers.add(offer);
            }
        return offers;
        }

    /**
     * Every offer returned by the factory carries the resolved default pool.
     *
     */
    @Test
    public void testGenerateUsesTheDefaultPool()
        {
        Instant start = Instant.now().plusSeconds(7200L);
        List<SimpleComputeOffer> queryOffers = new ArrayList<SimpleComputeOffer>();
        queryOffers.add(
            makeQueryOffer(start)
            );
        queryOffers.add(
            makeQueryOffer(start.plusSeconds(720L))
            );

        List<SimpleComputeOffer> offers = generate(
            new StubPoolFactory(this.pool, true),
            queryOffers
            );

        Assertions.assertEquals(
            2,
            offers.size(),
            "The factory must return both query offers"
            );
        for (SimpleComputeOffer offer : offers)
            {
            Assertions.assertSame(
                this.pool,
                offer.getPool(),
                "Every generated offer must carry the resolved pool"
                );
            }
        }

    /**
     * A missing default pool still yields no offers.
     *
     */
    @Test
    public void testMissingPoolYieldsNoOffers()
        {
        List<SimpleComputeOffer> queryOffers = new ArrayList<SimpleComputeOffer>();
        queryOffers.add(
            makeQueryOffer(Instant.now().plusSeconds(7200L))
            );

        List<SimpleComputeOffer> offers = generate(
            new StubPoolFactory(this.pool, false),
            queryOffers
            );

        Assertions.assertTrue(
            offers.isEmpty(),
            "A missing default pool must yield no offers"
            );
        }

    /**
     * Two pools both bid, and the merged list is sorted by start time.
     *
     */
    @Test
    public void testMergedOffersAreSortedByStart()
        {
        Instant start = Instant.now().plusSeconds(7200L);
        TestPool first = new TestPool(
            "first",
            UUID.fromString("00000000-0000-4000-8000-000000000001")
            );
        TestPool second = new TestPool(
            "second",
            UUID.fromString("00000000-0000-4000-8000-000000000002")
            );
        MultiPoolFactory pools = new MultiPoolFactory(
            List.of(first, second)
            );
        SequenceQueryHandler handler = new SequenceQueryHandler(
            List.of(
                List.of(makeQueryOffer(start.plusSeconds(3600L))),
                List.of(makeQueryOffer(start))
                )
            );

        List<SimpleComputeOffer> offers = generate(
            pools,
            handler,
            10
            );

        Assertions.assertEquals(
            2,
            offers.size(),
            "Both pools must contribute an offer"
            );
        Assertions.assertEquals(
            start.getEpochSecond(),
            offers.get(0).getOfferStartInstant().getEpochSecond(),
            "The merged list must be sorted by start time"
            );
        Assertions.assertTrue(
            offers.get(0).getOfferStartInstant().isBefore(
                offers.get(1).getOfferStartInstant()
                ),
            "The merged list must be sorted by start time"
            );
        Assertions.assertSame(
            second,
            offers.get(0).getPool(),
            "The earlier offer must carry its own pool"
            );
        Assertions.assertSame(
            first,
            offers.get(1).getPool(),
            "The later offer must carry its own pool"
            );
        }

    /**
     * The merged list is filtered before the cap is applied.
     *
     */
    @Test
    public void testCapAppliesAfterFiltering()
        {
        Instant start = Instant.now().plusSeconds(7200L);
        TestPool first = new TestPool(
            "first",
            UUID.fromString("00000000-0000-4000-8000-000000000001")
            );
        TestPool second = new TestPool(
            "second",
            UUID.fromString("00000000-0000-4000-8000-000000000002")
            );
        MultiPoolFactory pools = new MultiPoolFactory(
            List.of(first, second)
            );
        Instant ended = Instant.now().minusSeconds(3600L);
        SequenceQueryHandler handler = new SequenceQueryHandler(
            List.of(
                List.of(
                    makeQueryOffer(ended),
                    makeQueryOffer(start.plusSeconds(3600L))
                    ),
                List.of(
                    makeQueryOffer(start.plusSeconds(7200L)),
                    makeQueryOffer(start.plusSeconds(10800L))
                    )
                )
            );

        List<SimpleComputeOffer> offers = generate(
            pools,
            handler,
            2
            );

        Assertions.assertEquals(
            2,
            offers.size(),
            "The cap must be applied after the ended offer is dropped"
            );
        Assertions.assertEquals(
            start.plusSeconds(3600L).getEpochSecond(),
            offers.get(0).getOfferStartInstant().getEpochSecond(),
            "The first surviving offer must be the earliest"
            );
        Assertions.assertEquals(
            start.plusSeconds(7200L).getEpochSecond(),
            offers.get(1).getOfferStartInstant().getEpochSecond(),
            "The second surviving offer must be the next earliest"
            );
        }

    /**
     * A request limit of zero falls back to the query limit.
     *
     */
    @Test
    public void testEffectiveLimitFallback()
        {
        Instant start = Instant.now().plusSeconds(7200L);
        TestPool pool = new TestPool(
            "pool",
            UUID.fromString("00000000-0000-4000-8000-000000000001")
            );
        List<SimpleComputeOffer> queryOffers = new ArrayList<SimpleComputeOffer>();
        for (int index = 0; index < 5; index++)
            {
            queryOffers.add(
                makeQueryOffer(start.plusSeconds(index * 600L))
                );
            }

        List<SimpleComputeOffer> offers = generate(
            new MultiPoolFactory(List.of(pool)),
            queryOffers,
            0
            );

        Assertions.assertEquals(
            4,
            offers.size(),
            "A zero request limit must fall back to the query limit"
            );
        }

    /**
     * A declining pool contributes no offers and does not run its query.
     *
     */
    @Test
    public void testDecliningPoolIsNotQueried()
        {
        Instant start = Instant.now().plusSeconds(7200L);
        TestPool declining = new TestPool(
            "declining",
            UUID.fromString("00000000-0000-4000-8000-000000000001")
            );
        declining.addFloorPolicy(10L);
        TestPool accepting = new TestPool(
            "accepting",
            UUID.fromString("00000000-0000-4000-8000-000000000002")
            );
        MultiPoolFactory pools = new MultiPoolFactory(
            List.of(declining, accepting)
            );
        SequenceQueryHandler handler = new SequenceQueryHandler(
            List.of(
                List.of(makeQueryOffer(start))
                )
            );

        List<SimpleComputeOffer> offers = generate(
            pools,
            handler,
            10
            );

        Assertions.assertEquals(
            1,
            offers.size(),
            "Only the accepting pool must contribute an offer"
            );
        Assertions.assertSame(
            accepting,
            offers.get(0).getPool(),
            "The offer must come from the accepting pool"
            );
        Assertions.assertEquals(
            1,
            handler.getQueries().size(),
            "The declining pool must not run a query"
            );
        }
    }
