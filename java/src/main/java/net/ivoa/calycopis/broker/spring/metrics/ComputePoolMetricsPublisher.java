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

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.functional.metrics.ComputePoolMetricsQuery;
import net.ivoa.calycopis.broker.engine.functional.metrics.ComputePoolMetricsQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.metrics.ComputePoolMetricsRow;

/**
 * Publishes the compute pool capacity as Prometheus gauges.
 *
 * The publisher owns one gauge for each combination of pool, metric and state.
 * A scheduled refresh reads the current block's allocated and reserved values
 * from the database and updates the gauges in place, so Prometheus sees a
 * continuous time series rather than a new metric on every scrape.
 *
 * The database query returns memory in GiB. The gauges expose bytes, because
 * that is the unit Grafana can format without a per-panel conversion.
 *
 * A refresh failure leaves the previous values in place and logs a warning.
 * A later successful refresh corrects them. Gauges for pools that no longer
 * exist are removed, so a deleted pool cannot leave a stale series behind.
 *
 */
@Slf4j
@Component
public class ComputePoolMetricsPublisher
implements MeterBinder
    {

    /**
     * The gauge for compute pool cores.
     *
     */
    public static final String CORES_METRIC_NAME = "calycopis_compute_pool_cores";

    /**
     * The gauge for compute pool memory in bytes.
     *
     */
    public static final String MEMORY_METRIC_NAME = "calycopis_compute_pool_memory_bytes";

    /**
     * The tag carrying the pool name.
     *
     */
    public static final String POOL_TAG = "pool";

    /**
     * The tag carrying the pool identifier.
     *
     */
    public static final String POOL_UUID_TAG = "pool_uuid";

    /**
     * The tag carrying the resource state.
     *
     */
    public static final String STATE_TAG = "state";

    /**
     * The allocated state.
     *
     */
    public static final String ALLOCATED_STATE = "allocated";

    /**
     * The reserved state.
     *
     */
    public static final String RESERVED_STATE = "reserved";

    /**
     * The available state.
     *
     */
    public static final String AVAILABLE_STATE = "available";

    /**
     * The number of bytes in one GiB.
     *
     */
    private static final long BYTES_PER_GIB = 1_073_741_824L;

    private final ComputePoolMetricsQueryHandler queryHandler;

    private final Map<GaugeKey, PoolGauge> gauges = new HashMap<>();

    private volatile MeterRegistry registry;

    /**
     * Public constructor.
     *
     * @param queryHandler The handler that reads the current pool capacity.
     *
     */
    public ComputePoolMetricsPublisher(
        final ComputePoolMetricsQueryHandler queryHandler
        ){
        this.queryHandler = queryHandler;
        }

    /**
     * Keep the registry so a scheduled refresh can update its gauges.
     *
     */
    @Override
    public void bindTo(final MeterRegistry registry)
        {
        this.registry = registry;
        }

    /**
     * Refresh the gauges from the current database state.
     *
     * The scheduler can start before the meter registry is bound, so this is a
     * no-op until bindTo has run. The next scheduled call picks the values up.
     *
     */
    @Scheduled(
        fixedDelayString = "${calycopis.broker.metrics.pool-refresh-milliseconds:15000}"
        )
    public void refresh()
        {
        if (this.registry == null)
            {
            return;
            }

        final Instant instant = Instant.now();

        final Iterable<ComputePoolMetricsRow> rows;
        try {
            rows = this.queryHandler.query(
                ComputePoolMetricsQuery.build(
                    instant
                    )
                );
            }
        catch (final RuntimeException exception) {
            log.warn(
                "Could not refresh compute pool metrics [{}]",
                exception.getMessage()
                );
            return;
            }

        final Set<UUID> presentPools = new HashSet<>();

        for (final ComputePoolMetricsRow row : rows)
            {
            if (row == null)
                {
                continue;
                }

            presentPools.add(
                row.poolUuid()
                );

            final long allocatedCores = row.allocatedCores();
            final long reservedCores = row.reservedCores();
            final long availableCores = allocatedCores - reservedCores;

            setGauge(
                row.poolUuid(),
                row.poolName(),
                ALLOCATED_STATE,
                CORES_METRIC_NAME,
                allocatedCores
                );
            setGauge(
                row.poolUuid(),
                row.poolName(),
                RESERVED_STATE,
                CORES_METRIC_NAME,
                reservedCores
                );
            setGauge(
                row.poolUuid(),
                row.poolName(),
                AVAILABLE_STATE,
                CORES_METRIC_NAME,
                availableCores
                );

            final long allocatedMemory = row.allocatedMemory() * BYTES_PER_GIB;
            final long reservedMemory = row.reservedMemory() * BYTES_PER_GIB;
            final long availableMemory = allocatedMemory - reservedMemory;

            setGauge(
                row.poolUuid(),
                row.poolName(),
                ALLOCATED_STATE,
                MEMORY_METRIC_NAME,
                allocatedMemory
                );
            setGauge(
                row.poolUuid(),
                row.poolName(),
                RESERVED_STATE,
                MEMORY_METRIC_NAME,
                reservedMemory
                );
            setGauge(
                row.poolUuid(),
                row.poolName(),
                AVAILABLE_STATE,
                MEMORY_METRIC_NAME,
                availableMemory
                );
            }

        removeAbsentPools(
            presentPools
            );
        }

    /**
     * Create or update one gauge.
     *
     */
    private void setGauge(
        final UUID poolUuid,
        final String poolName,
        final String state,
        final String metricName,
        final long value
        ){
        final GaugeKey key = new GaugeKey(
            poolUuid,
            metricName,
            state
            );

        PoolGauge poolGauge = this.gauges.get(
            key
            );

        if (poolGauge == null)
            {
            final AtomicLong holder = new AtomicLong(
                value
                );

            final Gauge gauge = Gauge.builder(
                metricName,
                holder,
                AtomicLong::doubleValue
                )
                .description(
                    descriptionFor(
                        metricName
                        )
                    )
                .baseUnit(
                    unitFor(
                        metricName
                        )
                    )
                .tags(
                    POOL_TAG, poolName,
                    POOL_UUID_TAG, poolUuid.toString(),
                    STATE_TAG, state
                    )
                .strongReference(true)
                .register(
                    this.registry
                    );

            poolGauge = new PoolGauge(
                gauge,
                holder
                );

            this.gauges.put(
                key,
                poolGauge
                );
            }

        poolGauge.value().set(
            value
            );
        }

    /**
     * Remove the gauges for pools that are no longer present.
     *
     */
    private void removeAbsentPools(
        final Set<UUID> presentPools
        ){
        final Iterator<Map.Entry<GaugeKey, PoolGauge>> iterator = this.gauges.entrySet().iterator();

        while (iterator.hasNext())
            {
            final Map.Entry<GaugeKey, PoolGauge> entry = iterator.next();

            if (!presentPools.contains(
                entry.getKey().poolUuid()
                ))
                {
                this.registry.remove(
                    entry.getValue().gauge()
                    );
                iterator.remove();
                }
            }
        }

    /**
     * The description for one metric.
     *
     */
    private static String descriptionFor(
        final String metricName
        ){
        if (CORES_METRIC_NAME.equals(metricName))
            {
            return "Compute pool cores by resource state.";
            }
        else {
            return "Compute pool memory by resource state, in bytes.";
            }
        }

    /**
     * The base unit for one metric.
     *
     */
    private static String unitFor(
        final String metricName
        ){
        if (CORES_METRIC_NAME.equals(metricName))
            {
            return "cores";
            }
        else {
            return "bytes";
            }
        }

    /**
     * The identity of one gauge.
     *
     */
    private record GaugeKey(
        UUID poolUuid,
        String metricName,
        String state
        )
        {
        }

    /**
     * One gauge and the value holder it reads.
     *
     */
    private record PoolGauge(
        Gauge gauge,
        AtomicLong value
        )
        {
        }
    }
