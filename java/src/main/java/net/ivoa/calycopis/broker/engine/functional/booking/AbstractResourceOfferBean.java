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
 * AIMetrics: []
 *
 */
package net.ivoa.calycopis.broker.engine.functional.booking;

import java.time.Duration;
import java.time.Instant;

import org.threeten.extra.Interval;

import lombok.extern.slf4j.Slf4j;

/**
 * A ResourceOffer implementation.
 * 
 */
@Slf4j
public class AbstractResourceOfferBean
implements AbstractResourceOffer
    {

    /**
     * Protected constructor.
     * 
     */
    protected AbstractResourceOfferBean(final String offerName, final Interval offerStart, final Duration offerDuration)
        {
        log.debug("AbstractResourceOfferBean(...)");
        log.debug("values [{}][{}][{}]", offerName, offerStart, offerDuration);
        this.offerName = offerName;
        this.offerStart  = offerStart;
        this.offerDuration  = offerDuration;
        }

    protected final String offerName;
    /**
     * Get the offer name.
     * 
     */
    public String getName()
        {
        return this.offerName;
        }

    protected final Interval offerStart;
    /**
     * Get the start time as an Interval.
     * 
     */
    public Interval getOfferStartInterval()
        {
        return this.offerStart;
        }

    /**
     * Get the start time as an Instant.
     * This just returns the start of the start Interval.
     * 
     */
    public Instant getOfferStartInstant()
        {
        return this.offerStart.getStart();
        }
    
    protected final Duration offerDuration;
    /**
     * Get the offer Duration.
     * 
     */
    public Duration getOfferDuration()
        {
        return this.offerDuration;
        }
    }
