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
 *     "interval": "2026-11-16T07:05:00/2026-11-16T08:20:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
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
 *     },
 *     {
 *     "interval": "2026-11-18T18:50:00/2026-11-18T20:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.pools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePool;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyKind;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;

/**
 * Tests for the startup seed.
 *
 * The factories here are stand-ins that remember what they hold by name, which
 * is enough to check the properties that matter: a second run creates nothing,
 * the default pool is always seeded, and a pool without allocations gets the
 * bootstrap fallback allocation.
 *
 */
public class ComputePoolBootstrapTest
    {

    /**
     * Running the seed twice must leave one pool and one allocation, not two.
     *
     */
    @Test
    public void testSeedIsIdempotent() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.<ComputePoolSpec>of(),
            pools,
            allocations
            );

        bootstrap.run(null);
        bootstrap.run(null);

        Assertions.assertEquals(
            List.of(AbstractComputeResourcePool.DEFAULT_POOL_NAME),
            pools.created,
            "Running the seed twice must create one pool"
            );
        Assertions.assertEquals(
            1,
            allocations.created.size(),
            "Running the seed twice must create one allocation"
            );
        Assertions.assertEquals(
            ComputePoolBootstrap.DEFAULT_ALLOCATION_CORES,
            allocations.created.get(0).cores,
            "The fallback allocation must use the bootstrap cores"
            );
        Assertions.assertEquals(
            ComputePoolBootstrap.DEFAULT_ALLOCATION_MEMORY,
            allocations.created.get(0).memory,
            "The fallback allocation must use the bootstrap memory"
            );
        Assertions.assertEquals(
            Instant.EPOCH,
            allocations.created.get(0).start,
            "The fallback allocation must start at the epoch"
            );
        Assertions.assertNull(
            allocations.created.get(0).end,
            "The fallback allocation must be open ended"
            );
        }

    /**
     * A configured list is seeded, and the default pool is added to it.
     *
     */
    @Test
    public void testSeedUsesConfiguredPoolsAndAddsTheDefault() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specOf("alpha"),
                specOf("beta")
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            List.of("alpha", "beta", AbstractComputeResourcePool.DEFAULT_POOL_NAME),
            pools.created,
            "The configured pools must be created in order, followed by the default"
            );
        Assertions.assertEquals(
            3,
            allocations.created.size(),
            "Every seeded pool must get an allocation"
            );
        }

    /**
     * A pool without a name is skipped rather than stopping the broker.
     *
     */
    @Test
    public void testSeedSkipsANamelessPool() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specOf("alpha"),
                specOf("  ")
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            List.of("alpha", AbstractComputeResourcePool.DEFAULT_POOL_NAME),
            pools.created,
            "A pool with no name must be skipped"
            );
        }

    /**
     * Declared allocations are seeded instead of the fallback.
     *
     */
    @Test
    public void testSeedUsesDeclaredAllocations() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specWithAllocation(
                    "alpha",
                    8L,
                    16L,
                    Instant.parse("2026-12-01T00:00:00Z"),
                    Instant.parse("2026-12-02T00:00:00Z")
                    )
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            2,
            allocations.created.size(),
            "The declared allocation and the default fallback must both be created"
            );
        AssertionRecord alpha = allocations.created.get(0);
        Assertions.assertEquals("alpha", alpha.pool);
        Assertions.assertEquals(8L, alpha.cores);
        Assertions.assertEquals(16L, alpha.memory);
        Assertions.assertEquals(
            Instant.parse("2026-12-01T00:00:00Z"),
            alpha.start
            );
        Assertions.assertEquals(
            Instant.parse("2026-12-02T00:00:00Z"),
            alpha.end
            );
        }

    /**
     * An explicit allocation for the default pool wins over the fallback.
     *
     */
    @Test
    public void testSeedUsesAnExplicitDefaultAllocation() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specWithAllocation(
                    AbstractComputeResourcePool.DEFAULT_POOL_NAME,
                    128L,
                    64L,
                    null,
                    null
                    )
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            1,
            allocations.created.size(),
            "The default pool must get only its declared allocation"
            );
        AssertionRecord defaultAllocation = allocations.created.get(0);
        Assertions.assertEquals(
            AbstractComputeResourcePool.DEFAULT_POOL_NAME,
            defaultAllocation.pool
            );
        Assertions.assertEquals(128L, defaultAllocation.cores);
        Assertions.assertEquals(64L, defaultAllocation.memory);
        Assertions.assertEquals(
            ComputePoolBootstrap.SEEDED_ALLOCATION_START,
            defaultAllocation.start
            );
        Assertions.assertNull(defaultAllocation.end);
        }

    /**
     * An allocation with no positive size is skipped, and the pool is kept.
     *
     */
    @Test
    public void testSeedSkipsAnInvalidAllocation() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specWithAllocation(
                    "alpha",
                    0L,
                    16L,
                    null,
                    null
                    )
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            List.of("alpha", AbstractComputeResourcePool.DEFAULT_POOL_NAME),
            pools.created,
            "The pool must still be created"
            );
        Assertions.assertEquals(
            1,
            allocations.created.size(),
            "Only the default pool's fallback allocation must be created"
            );
        Assertions.assertEquals(
            AbstractComputeResourcePool.DEFAULT_POOL_NAME,
            allocations.created.get(0).pool,
            "The fallback allocation belongs to the default pool"
            );
        }

    /**
     * A configured pool keeps its priority and enabled flag, and its policy is
     * seeded.
     *
     */
    @Test
    public void testSeedUsesOfferSettingsAndPolicies() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        StubPolicyFactory policies = new StubPolicyFactory();
        ComputePoolSpec spec = specOf("alpha");
        spec.setPriority(7);
        spec.setEnabled(false);
        spec.setPolicies(
            List.of(
                floorPolicy("floor", 128L)
                )
            );
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(spec),
            pools,
            allocations,
            policies
            );

        bootstrap.run(null);

        SimpleComputeResourcePoolEntity alpha = pools.pools.get("alpha");
        Assertions.assertEquals(
            7,
            alpha.getPriority(),
            "The seeded pool must keep its configured priority"
            );
        Assertions.assertFalse(
            alpha.isEnabled(),
            "The seeded pool must keep its configured enabled flag"
            );
        Assertions.assertEquals(
            1,
            policies.created.size(),
            "The seeded pool must create its configured policy"
            );
        Assertions.assertEquals(
            SimpleComputePoolPolicyKind.MEMORY_FLOOR,
            policies.created.get(0).kind(),
            "The seeded policy must keep its kind"
            );
        Assertions.assertEquals(
            128L,
            policies.created.get(0).minimumMemory().longValue(),
            "The seeded policy must keep its floor"
            );
        }

    /**
     * An invalid policy disables a new pool rather than leaving it permissive.
     *
     */
    @Test
    public void testInvalidPolicyDisablesNewPool() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        StubPolicyFactory policies = new StubPolicyFactory();
        ComputePoolSpec spec = specOf("alpha");
        ComputePolicySpec invalid = new ComputePolicySpec();
        invalid.setKind("memory-floor");
        invalid.setName("broken");
        spec.setPolicies(
            List.of(invalid)
            );
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(spec),
            pools,
            allocations,
            policies
            );

        bootstrap.run(null);

        Assertions.assertFalse(
            pools.pools.get("alpha").isEnabled(),
            "An invalid policy must disable its new pool"
            );
        Assertions.assertTrue(
            policies.created.isEmpty(),
            "An invalid policy must not be seeded"
            );
        }

    /**
     * An invalid policy disables an existing pool too.
     *
     */
    @Test
    public void testInvalidPolicyDisablesExistingPool() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap first = bootstrapOf(
            List.of(specOf("alpha")),
            pools,
            allocations
            );
        first.run(null);
        Assertions.assertTrue(
            pools.pools.get("alpha").isEnabled(),
            "The first seed must leave the pool enabled"
            );

        ComputePoolSpec spec = specOf("alpha");
        ComputePolicySpec invalid = new ComputePolicySpec();
        invalid.setKind("memory-floor");
        invalid.setName("broken");
        spec.setPolicies(
            List.of(invalid)
            );
        ComputePoolBootstrap second = bootstrapOf(
            List.of(spec),
            pools,
            allocations,
            new StubPolicyFactory()
            );
        second.run(null);

        Assertions.assertFalse(
            pools.pools.get("alpha").isEnabled(),
            "An invalid policy must disable an existing pool"
            );
        }

    private static ComputePoolBootstrap bootstrapOf(
        final List<ComputePoolSpec> specs,
        final SimpleComputeResourcePoolEntityFactory pools,
        final SimpleComputeAllocationEntityFactory allocations
        ){
        return bootstrapOf(
            specs,
            pools,
            allocations,
            new StubPolicyFactory()
            );
        }

    private static ComputePoolBootstrap bootstrapOf(
        final List<ComputePoolSpec> specs,
        final SimpleComputeResourcePoolEntityFactory pools,
        final SimpleComputeAllocationEntityFactory allocations,
        final StubPolicyFactory policies
        ){
        return new ComputePoolBootstrap(
            settingsOf(
                specs
                ),
            pools,
            allocations,
            policies
            );
        }

    private static ComputePolicySpec floorPolicy(
        final String name,
        final Long minimumMemory
        ){
        ComputePolicySpec policy = new ComputePolicySpec();
        policy.setKind("memory-floor");
        policy.setName(name);
        policy.setMinimumMemory(minimumMemory);
        return policy;
        }

    private static ComputePoolSpec specOf(final String name)
        {
        ComputePoolSpec spec = new ComputePoolSpec();
        spec.setName(name);
        spec.setDescription("A test pool.");
        return spec;
        }

    private static ComputePoolSpec specWithAllocation(
        final String name,
        final Long cores,
        final Long memory,
        final Instant start,
        final Instant end
        ){
        ComputePoolSpec spec = specOf(
            name
            );
        ComputeAllocationSpec allocation = new ComputeAllocationSpec();
        allocation.setCores(cores);
        allocation.setMemory(memory);
        allocation.setStart(start);
        allocation.setEnd(end);
        spec.setAllocations(
            List.of(allocation)
            );
        return spec;
        }

    private static ComputePoolsSettings settingsOf(final List<ComputePoolSpec> specs)
        {
        return new ComputePoolsSettings()
            {
            @Override
            public List<ComputePoolSpec> getCompute()
                {
                return specs;
                }
            };
        }

    /**
     * A factory that behaves like the real pool factory, without a database.
     *
     */
    static class StubPoolFactory
    implements SimpleComputeResourcePoolEntityFactory
        {
        final Map<String, SimpleComputeResourcePoolEntity> pools = new LinkedHashMap<>();
        final List<String> created = new ArrayList<>();

        @Override
        public Optional<SimpleComputeResourcePoolEntity> select(final UUID uuid)
            {
            return Optional.empty();
            }

        @Override
        public Optional<SimpleComputeResourcePoolEntity> findByName(final String name)
            {
            return Optional.ofNullable(
                this.pools.get(name)
                );
            }

        @Override
        public List<SimpleComputeResourcePoolEntity> list()
            {
            return new ArrayList<>(
                this.pools.values()
                );
            }

        @Override
        public List<SimpleComputeResourcePoolEntity> listEnabled()
            {
            List<SimpleComputeResourcePoolEntity> enabled = new ArrayList<>();
            for (SimpleComputeResourcePoolEntity entity : this.pools.values())
                {
                if (entity.isEnabled())
                    {
                    enabled.add(
                        entity
                        );
                    }
                }
            return enabled;
            }

        @Override
        public SimpleComputeResourcePoolEntity create(final String name, final String description)
            {
            return this.create(
                name,
                description,
                0,
                true
                );
            }

        @Override
        public SimpleComputeResourcePoolEntity create(
            final String name,
            final String description,
            final int priority,
            final boolean enabled
            ){
            SimpleComputeResourcePoolEntity entity = new SimpleComputeResourcePoolEntity(
                name,
                description,
                priority,
                enabled
                );
            this.pools.put(
                name,
                entity
                );
            this.created.add(
                name
                );
            return entity;
            }

        @Override
        public SimpleComputeResourcePoolEntity update(
            final SimpleComputeResourcePoolEntity entity,
            final int priority,
            final boolean enabled
            ){
            entity.setPriority(
                priority
                );
            entity.setEnabled(
                enabled
                );
            return entity;
            }
        }

    /**
     * A factory that behaves like the real allocation factory, without a
     * database.
     *
     */
    static class StubAllocationFactory
    implements SimpleComputeAllocationEntityFactory
        {
        final List<AssertionRecord> created = new ArrayList<>();

        @Override
        public Optional<SimpleComputeAllocationEntity> select(final UUID uuid)
            {
            return Optional.empty();
            }

        @Override
        public List<SimpleComputeAllocationEntity> list(final AbstractComputeResourcePoolEntity pool)
            {
            return new ArrayList<>();
            }

        @Override
        public SimpleComputeAllocationEntity create(
            final AbstractComputeResourcePoolEntity pool,
            final long cores,
            final long memory,
            final Instant start,
            final Instant end
            ){
            this.created.add(
                new AssertionRecord(
                    pool.getName(),
                    cores,
                    memory,
                    start,
                    end
                    )
                );
            return new SimpleComputeAllocationEntity(
                pool,
                cores,
                memory,
                start,
                end
                );
            }

        @Override
        public SimpleComputeAllocationEntity update(
            final SimpleComputeAllocationEntity entity,
            final long cores,
            final long memory,
            final Instant start,
            final Instant end
            ){
            entity.update(
                cores,
                memory,
                start,
                end
                );
            return entity;
            }

        @Override
        public void delete(final SimpleComputeAllocationEntity entity)
            {
            }

        @Override
        public boolean wouldOverCommit(
            final UUID poolUuid,
            final UUID excludedUuid,
            final long cores,
            final long memory,
            final Instant start,
            final Instant end
            ){
            return false;
            }
        }

    /**
     * A factory that behaves like the real policy factory, without a database.
     *
     */
    static class StubPolicyFactory
    implements SimpleComputePoolPolicyEntityFactory
        {
        final List<PolicyRecord> created = new ArrayList<>();

        @Override
        public Optional<SimpleComputePoolPolicyEntity> select(final UUID uuid)
            {
            return Optional.empty();
            }

        @Override
        public List<SimpleComputePoolPolicyEntity> list(
            final AbstractComputeResourcePoolEntity pool
            ){
            return new ArrayList<>();
            }

        @Override
        public Optional<SimpleComputePoolPolicyEntity> findByName(
            final AbstractComputeResourcePoolEntity pool,
            final String name
            ){
            return Optional.empty();
            }

        @Override
        public SimpleComputePoolPolicyEntity create(
            final AbstractComputeResourcePoolEntity pool,
            final SimpleComputePoolPolicyKind kind,
            final String name,
            final Long minimumMemory,
            final Long maximumMemory,
            final String username,
            final Integer startHour,
            final Integer endHour
            ){
            this.created.add(
                new PolicyRecord(
                    pool.getName(),
                    kind,
                    name,
                    minimumMemory,
                    maximumMemory,
                    username,
                    startHour,
                    endHour
                    )
                );
            return new SimpleComputePoolPolicyEntity(
                pool,
                kind,
                name,
                minimumMemory,
                maximumMemory,
                username,
                startHour,
                endHour
                );
            }

        @Override
        public SimpleComputePoolPolicyEntity update(
            final SimpleComputePoolPolicyEntity entity,
            final SimpleComputePoolPolicyKind kind,
            final String name,
            final Long minimumMemory,
            final Long maximumMemory,
            final String username,
            final Integer startHour,
            final Integer endHour
            ){
            entity.update(
                kind,
                name,
                minimumMemory,
                maximumMemory,
                username,
                startHour,
                endHour
                );
            return entity;
            }

        @Override
        public void delete(final SimpleComputePoolPolicyEntity entity)
            {
            }
        }

    /**
     * What the stub policy factory remembers about one creation.
     *
     */
    record PolicyRecord(
        String pool,
        SimpleComputePoolPolicyKind kind,
        String name,
        Long minimumMemory,
        Long maximumMemory,
        String username,
        Integer startHour,
        Integer endHour
        )
        {
        }

    /**
     * What the stub allocation factory remembers about one creation.
     *
     */
    record AssertionRecord(
        String pool,
        long cores,
        long memory,
        Instant start,
        Instant end
        )
        {
        }
    }
