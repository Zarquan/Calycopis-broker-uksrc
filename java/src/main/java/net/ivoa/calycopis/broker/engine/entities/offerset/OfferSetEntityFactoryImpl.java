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
 *     "timestamp": "2026-05-26T16:50:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 2,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-30T06:47:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 12,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-09-05T12:05:01",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 2,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-11T11:15:00/2026-10-11T11:34:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.entities.offerset;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.AbstractEntityRepository;
import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntry;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactory;
import net.ivoa.calycopis.broker.engine.entities.session.AbstractExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;
import net.ivoa.calycopis.openapi.spring.model.IvoaOfferSetResponse.ResultEnum;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleExecutionSessionPhase;

/**
 *
 */
@Slf4j
public class OfferSetEntityFactoryImpl
    extends FactoryBaseImpl
    implements OfferSetEntityFactory
    {

    private final Platform platform;
    private final OfferSetRequestParser offersetRequestParser;
    
    private final AbstractEntityRepository<OfferSetEntity> offersetRepository;

    private final ProfilingEntryFactory profilingEntryFactory;

    /**
     * Public constructor used by our Platform.
     * 
     */
    public OfferSetEntityFactoryImpl(
        final Platform platform,
        final AbstractEntityRepository<OfferSetEntity> offersetRepository,
        final OfferSetRequestParser offersetParser,
        final ProfilingEntryFactory profilingEntryFactory
        ){
        super();
        this.platform = platform;
        this.offersetRepository = offersetRepository;
        this.offersetRequestParser = offersetParser;
        this.profilingEntryFactory = profilingEntryFactory;
        }

    @Override
    public Optional<OfferSetEntity> select(final UUID uuid)
		{
		return this.offersetRepository.findById(
            uuid
            ); 
		}

    @Override
    public OfferSetEntity create(final IvoaExecutionRequest offersetRequest, final IdentityEntity identity)
    	{
        //
        // Validate the request. 
        OfferSetRequestParserContext offersetContext = offersetRequestParser.stageOne(
            platform,
            offersetRequest,
            identity
            );
        //
        // Create the OfferSetEntity from the context.
        return this.create(
            offersetContext,
            0
            );
    	}

    @Override
    public SimpleExecutionSessionEntity direct(final IvoaExecutionRequest executionRequest, final IdentityEntity identity)
        {
        //
        // Validate the request.
        OfferSetRequestParserContext offersetContext = offersetRequestParser.stageOne(
            platform,
            executionRequest,
            identity
            );

        //
        // If the request is valid, create a new OfferSetEntity and return the first offer.
        if (offersetContext.valid())
            {
            OfferSetEntity offerSetEntity = this.create(
                offersetContext,
                1
                );
            //
            // If the OfferSetEntity is valid.
            if (offerSetEntity.getResult() == ResultEnum.YES)
                {
                //
                // If the OfferSetEntity has at least one offer.
                Iterator<AbstractExecutionSessionEntity> offers = offerSetEntity.getOfferEntities().iterator();
                if (offers.hasNext())
                    {
                    // TODO Get rid of the nasty class casts.
                    SimpleExecutionSessionEntity offer = (SimpleExecutionSessionEntity) offers.next();
                    //
                    // Set the phase to ACCEPTED and schedule a PrepareSessionRequest for the offer.
                    offer.setPhase(
                        IvoaSimpleExecutionSessionPhase.ACCEPTED
                        );
                    platform.getProcessingRequestFactory().getSessionProcessingRequestFactory().createPrepareSessionRequest(
                        offer
                        );
                    return offer;
                    }
                }
            }
        //
        // If the request is not valid, return a FAILED ExecutionSessionEntity. 
        SimpleExecutionSessionEntity failed = new SimpleExecutionSessionEntity();
        failed.setPhase(
            IvoaSimpleExecutionSessionPhase.FAILED
            );
        failed.claimMessages(
            offersetContext.getMessages()
            );
        return failed;
        }

    protected OfferSetEntity create(final OfferSetRequestParserContext offersetContext, int offerCount)
        {
        //
        // Start profiling this method.
        ProfilingEntry profiling = profilingEntryFactory.create(
            this.getClass().getName(),
            "create"
            );
        //
        // Create a new OfferSetEntity.
        OfferSetEntity offersetEntity = new OfferSetEntity(
            // tempfix    
            // offersetRequest.getName(),
            // offersetRequest.getDescription(),
            null,
            null,
            Instant.now(),
            Instant.now().plusSeconds(
                this.platform.getSessionTimingSettings().getExpiredTimeoutSeconds()
                ),
            offersetContext.getOwner()
            );
        //
        // Save the OfferSet before we add any offers.
        this.offersetRepository.save(
            offersetEntity
            );
        //
        // Add our profiling entry after the first save, so the final save
        // inserts it once with its end time already set.
        offersetEntity.addProfilingEntry(
            profiling
            );
        //
        // Add the offers to the OfferSetEntity.
        offersetRequestParser.stageTwo(
            platform,
            offersetEntity,
            offersetContext,
            offerCount
            );
        //
        // Stop profiling this method, before the final save.
        // The save runs in its own transaction, so an end time set after it
        // would never reach the database.
        profiling.end();
        //
        // Log the accumulated timings.
        for (ProfilingEntry entry : offersetEntity.getProfilingEntries())
            {
            Duration duration = null;
            if (entry.getEndTime() != null)
                {
                duration = Duration.between(
                    entry.getStartTime(),
                    entry.getEndTime()
                    );
                }
            log.debug(
                "Profiling [{}][{}][{}][{}]",
                entry.getClassName(),
                entry.getMethodName(),
                entry.getStartTime(),
                duration
                );
            }
        //
        // Save the OfferSet and the offers.
        return this.offersetRepository.save(
            offersetEntity
            );
        }
    }

