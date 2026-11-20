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

package net.ivoa.calycopis.broker.spring.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import net.ivoa.calycopis.broker.engine.functional.metrics.ComputePoolMetricsQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.metrics.ComputePoolMetricsRow;

/**
 * Unit tests for the ComputePoolMetricsPublisher.
 *
 */
public class ComputePoolMetricsPublisherTest
    {

    private static final UUID POOL_A = UUID.fromString(
        "00000000-0000-4000-8000-00000000000a"
        );

    private static final UUID POOL_B = UUID.fromString(
        "00000000-0000-4000-8000-00000000000b"
        );

    /**
     * A query handler whose result can be replaced by each test.
     *
     */
    private static class FakeQueryHandler
    implements ComputePoolMetricsQueryHandler
        {

        private final UUID uuid = UUID.fromString(
            "00000000-0000-4000-8000-0000000000ff"
            );

        private List<ComputePoolMetricsRow> rows = new ArrayList<>();

        private RuntimeException failure;

        private int calls;

        @Override
        public UUID getUuid()
            {
            return this.uuid;
            }

        @Override
        public Iterable<ComputePoolMetricsRow> query(
            final String query
            ){
            this.calls++;
            if (this.failure != null)
                {
                throw this.failure;
                }
            return this.rows;
            }

        @Override
        public Iterable<ComputePoolMetricsRow> query(
            final String query,
            final Map<String, Object> params
            ){
            return query(
                query
                );
            }
        }

    /**
     * Find a gauge by its identifying tags.
     *
     */
    private static Gauge findGauge(
        final SimpleMeterRegistry registry,
        final String metricName,
        final UUID poolUuid,
        final String poolName,
        final String state
        ){
        return registry.find(
            metricName
            )
            .tags(
                ComputePoolMetricsPublisher.POOL_TAG, poolName,
                ComputePoolMetricsPublisher.POOL_UUID_TAG, poolUuid.toString(),
                ComputePoolMetricsPublisher.STATE_TAG, state
                )
            .gauge();
        }

    /**
     * The publisher must register every gauge and expose the expected values.
     *
     */
    @Test
    public void testRefreshRegistersGauges()
        {
        final FakeQueryHandler handler = new FakeQueryHandler();
        handler.rows = List.of(
            new ComputePoolMetricsRow(
                POOL_A,
                "pool-a",
                8L,
                3L,
                4L,
                1L
                )
            );

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ComputePoolMetricsPublisher publisher = new ComputePoolMetricsPublisher(
            handler
            );

        publisher.bindTo(
            registry
            );
        publisher.refresh();

        Assertions.assertEquals(
            1,
            handler.calls
            );

        Assertions.assertEquals(
            8.0,
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.ALLOCATED_STATE).value(),
            0.0001
            );
        Assertions.assertEquals(
            3.0,
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.RESERVED_STATE).value(),
            0.0001
            );
        Assertions.assertEquals(
            5.0,
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.AVAILABLE_STATE).value(),
            0.0001
            );

        Assertions.assertEquals(
            4.0 * 1_073_741_824L,
            findGauge(registry, ComputePoolMetricsPublisher.MEMORY_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.ALLOCATED_STATE).value(),
            0.0001
            );
        Assertions.assertEquals(
            1.0 * 1_073_741_824L,
            findGauge(registry, ComputePoolMetricsPublisher.MEMORY_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.RESERVED_STATE).value(),
            0.0001
            );
        Assertions.assertEquals(
            3.0 * 1_073_741_824L,
            findGauge(registry, ComputePoolMetricsPublisher.MEMORY_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.AVAILABLE_STATE).value(),
            0.0001
            );
        }

    /**
     * Available capacity must be negative when the pool is over-committed.
     *
     */
    @Test
    public void testAvailableCanBeNegative()
        {
        final FakeQueryHandler handler = new FakeQueryHandler();
        handler.rows = List.of(
            new ComputePoolMetricsRow(
                POOL_A,
                "pool-a",
                2L,
                5L,
                2L,
                5L
                )
            );

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ComputePoolMetricsPublisher publisher = new ComputePoolMetricsPublisher(
            handler
            );

        publisher.bindTo(
            registry
            );
        publisher.refresh();

        Assertions.assertEquals(
            -3.0,
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.AVAILABLE_STATE).value(),
            0.0001
            );
        Assertions.assertEquals(
            -3.0 * 1_073_741_824L,
            findGauge(registry, ComputePoolMetricsPublisher.MEMORY_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.AVAILABLE_STATE).value(),
            0.0001
            );
        }

    /**
     * A pool that disappears between refreshes must lose its gauges.
     *
     */
    @Test
    public void testRefreshRemovesStalePools()
        {
        final FakeQueryHandler handler = new FakeQueryHandler();
        handler.rows = List.of(
            new ComputePoolMetricsRow(POOL_A, "pool-a", 8L, 3L, 4L, 1L),
            new ComputePoolMetricsRow(POOL_B, "pool-b", 2L, 0L, 2L, 0L)
            );

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ComputePoolMetricsPublisher publisher = new ComputePoolMetricsPublisher(
            handler
            );

        publisher.bindTo(
            registry
            );
        publisher.refresh();

        Assertions.assertNotNull(
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_B, "pool-b", ComputePoolMetricsPublisher.ALLOCATED_STATE)
            );

        handler.rows = List.of(
            new ComputePoolMetricsRow(POOL_A, "pool-a", 8L, 3L, 4L, 1L)
            );
        publisher.refresh();

        Assertions.assertNull(
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_B, "pool-b", ComputePoolMetricsPublisher.ALLOCATED_STATE)
            );
        Assertions.assertNotNull(
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.ALLOCATED_STATE)
            );
        }

    /**
     * A refresh before bindTo must be harmless.
     *
     */
    @Test
    public void testRefreshBeforeBindIsIgnored()
        {
        final FakeQueryHandler handler = new FakeQueryHandler();
        final ComputePoolMetricsPublisher publisher = new ComputePoolMetricsPublisher(
            handler
            );

        Assertions.assertDoesNotThrow(
            publisher::refresh
            );
        Assertions.assertEquals(
            0,
            handler.calls
            );
        }

    /**
     * A failed refresh must keep the previous snapshot and not kill the
     * scheduler.
     *
     */
    @Test
    public void testRefreshFailureKeepsPreviousValues()
        {
        final FakeQueryHandler handler = new FakeQueryHandler();
        handler.rows = List.of(
            new ComputePoolMetricsRow(POOL_A, "pool-a", 8L, 3L, 4L, 1L)
            );

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ComputePoolMetricsPublisher publisher = new ComputePoolMetricsPublisher(
            handler
            );

        publisher.bindTo(
            registry
            );
        publisher.refresh();

        handler.failure = new IllegalStateException(
            "database unavailable"
            );

        Assertions.assertDoesNotThrow(
            publisher::refresh
            );
        Assertions.assertEquals(
            8.0,
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.ALLOCATED_STATE).value(),
            0.0001
            );
        }

    /**
     * The publisher must update an existing gauge rather than register a
     * second one with the same tags.
     *
     */
    @Test
    public void testRefreshUpdatesExistingGauge()
        {
        final FakeQueryHandler handler = new FakeQueryHandler();
        handler.rows = List.of(
            new ComputePoolMetricsRow(POOL_A, "pool-a", 8L, 3L, 4L, 1L)
            );

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ComputePoolMetricsPublisher publisher = new ComputePoolMetricsPublisher(
            handler
            );

        publisher.bindTo(
            registry
            );
        publisher.refresh();

        handler.rows = List.of(
            new ComputePoolMetricsRow(POOL_A, "pool-a", 10L, 4L, 5L, 2L)
            );
        publisher.refresh();

        Assertions.assertEquals(
            10.0,
            findGauge(registry, ComputePoolMetricsPublisher.CORES_METRIC_NAME, POOL_A, "pool-a", ComputePoolMetricsPublisher.ALLOCATED_STATE).value(),
            0.0001
            );
        Assertions.assertEquals(
            1,
            registry.find(
                ComputePoolMetricsPublisher.CORES_METRIC_NAME
                ).tags(
                    ComputePoolMetricsPublisher.POOL_TAG, "pool-a",
                    ComputePoolMetricsPublisher.POOL_UUID_TAG, POOL_A.toString(),
                    ComputePoolMetricsPublisher.STATE_TAG, ComputePoolMetricsPublisher.ALLOCATED_STATE
                    ).gauges().size()
            );
        }
    }
