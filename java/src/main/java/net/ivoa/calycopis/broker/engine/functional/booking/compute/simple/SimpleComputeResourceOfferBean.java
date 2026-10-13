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
 *     "interval": "2026-10-13T10:19:00/2026-10-13T10:35:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 30,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import org.threeten.extra.Interval;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.functional.booking.AbstractResourceOfferBean;

/**
 * A SimpleComputeResourcesOffer bean implementation.
 * 
 */
@Slf4j
public class SimpleComputeResourceOfferBean
extends AbstractResourceOfferBean
implements SimpleComputeResourceOffer
    {
    /**
     * Create a SimpleComputeResourceOfferBean from a JDBC ResultSet.
     * 
     */
    public static SimpleComputeResourceOfferBean create(final ResultSet resultSet, int rowNumber)
    throws SQLException
        {
        log.debug("create(ResultSet, int)");
        log.debug("Row number [{}]", rowNumber);
        try {
            SimpleComputeResourceOfferBean offer = new SimpleComputeResourceOfferBean(
                "offer-" + Integer.toString(rowNumber),
                Interval.of(
                    Instant.ofEpochSecond(
                        resultSet.getLong("BlockStart") * SimpleComputeResourceQuery.BLOCK_STEP_SECONDS
                        ),
                Duration.ofSeconds(
                        0
                        )
                    ),
                Duration.ofSeconds(
                    resultSet.getLong("BlockLength") * SimpleComputeResourceQuery.BLOCK_STEP_SECONDS
                    ),
                resultSet.getLong("BlockCores"),
                resultSet.getLong("BlockMemory")
                );
            return offer;
            }
        catch (IllegalArgumentException ouch)
            {
            throw new SQLException(
                ouch
                );
            }
        }
    
    /**
     * Move an offer start time that is before the earliest allowed start time
     * forward to that time, keeping the end of the offer block.
     *
     * The booking query returns whole blocks of time, and the first block
     * starts at, or before, the requested start time. Moving the start forward
     * shortens the offer, so the query adds one extra block to the requested
     * duration and the shortened offer still covers the full duration the
     * client asked for. The end of the offer is left where the query placed
     * it, so the offered window always stays inside the free run the query
     * found.
     *
     */
    public static SimpleComputeResourceOfferBean clamp(final SimpleComputeResourceOffer offer, final Instant earliestStart)
        {
        long blockStart = offer.getOfferStartInstant().getEpochSecond();
        long blockEnd   = blockStart + offer.getOfferDuration().getSeconds();
        long earliest   = earliestStart.getEpochSecond();

        long start = blockStart;
        if ((earliest > blockStart) && (earliest < blockEnd))
            {
            log.debug(
                "Moving offer [{}] start from [{}] to [{}]",
                offer.getName(),
                Instant.ofEpochSecond(blockStart),
                Instant.ofEpochSecond(earliest)
                );
            start = earliest;
            }
        return new SimpleComputeResourceOfferBean(
            offer.getName(),
            Interval.of(
                Instant.ofEpochSecond(start),
                Duration.ofSeconds(
                    0
                    )
                ),
            Duration.ofSeconds(
                blockEnd - start
                ),
            offer.getMinCores(),
            offer.getMinMemory()
            );
        }

    /**
     * Protected constructor.
     * 
     */
    protected SimpleComputeResourceOfferBean(final String offerName, final Interval offerStart, final Duration offerDuration, final Long cpucores, final Long memory)
        {
        super(
            offerName,
            offerStart,
            offerDuration
            );
        this.minCpuCores = cpucores;
        this.maxCpuCores = cpucores;
        this.minMemory   = memory;
        this.maxMemory   = memory;
        }

    private final Long minCpuCores;
    @Override
    public Long getMinCores()
        {
        return this.minCpuCores;
        }

    private final Long maxCpuCores;
    @Override
    public Long getMaxCores()
        {
        return this.maxCpuCores;
        }
    
    private final Long minMemory;
    @Override
    public Long getMinMemory()
        {
        return this.minMemory;
        }

    private final Long maxMemory;
    @Override
    public Long getMaxMemory()
        {
        return this.maxMemory;
        }
    }
