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
 *     "timestamp": "2026-04-14T17:00:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-06-23T14:03:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute.simple.mock;

import java.time.Duration;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.compute.simple.SimpleComputeResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.simple.SimpleComputeResourceValidator;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceOffer;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.platform.mock.MockPlatform;
import net.ivoa.calycopis.broker.engine.functional.platform.mock.MockPlatformSettings;
import net.ivoa.calycopis.broker.engine.functional.processing.action.ProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.action.SimpleSleepAction;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecyclePhase;

/**
 * A Simple compute resource.
 *
 */
@Slf4j
@Entity
@Table(
    name = "mocksimplecomputeresources"
    )
@DiscriminatorValue(
    value = "uri:mock-simple-compute-resources"
    )
public class MockSimpleComputeResourceEntity
extends SimpleComputeResourceEntity
implements MockSimpleComputeResource
    {

    /**
     * Protected constructor for JPA entities.
     *
     */
    protected MockSimpleComputeResourceEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factory.
     *
     */
    protected MockSimpleComputeResourceEntity(
        final SimpleExecutionSessionEntity session,
        final SimpleComputeResourceValidator.Result result,
        final SimpleComputeResourceOffer offer
        ){
        super(
            session,
            result,
            offer
            );
        }

    @Column(name = "prepare_action_count")
    protected long prepareActionCount = 0;

    // TODO Add in the prepare duration from the request.
    @Override
    protected ProcessingAction makePrepareAction(final Platform platform)
        {
        log.debug(
            "makePrepareAction for compute resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        MockPlatformSettings settings = ((MockPlatform) platform).getMockEntitySettings();

        IvoaLifecyclePhase waitPhase = null ;
        IvoaLifecyclePhase donePhase = null ;
        
        if (this.prepareActionCount++ >= settings.getPrepareCount())
            {
            donePhase = IvoaLifecyclePhase.AVAILABLE;
            }
        
        return new SimpleSleepAction(
            this,
            Duration.ofMillis(
                settings.getPrepareDelay()
                ),
            waitPhase,
            donePhase
            );
        }

    @Column(name = "monitor_action_count")
    protected long monitorActionCount = 0;

    // TODO Add in the available duration from the request.
    @Override
    public ProcessingAction makeMonitorAction(Platform platform)
        {
        log.debug(
            "makeMonitorAction for compute resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        MockPlatformSettings settings = ((MockPlatform) platform).getMockEntitySettings();
 
        IvoaLifecyclePhase waitPhase = null ;
        IvoaLifecyclePhase donePhase = null ;
        
        if (this.monitorActionCount++ >= settings.getMonitorCount())
            {
            donePhase = IvoaLifecyclePhase.RELEASING;
            }
        
        return new SimpleSleepAction(
            this,
            Duration.ofMillis(
                settings.getMonitorDelay()
                ),
            waitPhase,
            donePhase
            );
        }

    @Column(name = "release_action_count")
    protected long releaseActionCount = 0;

    // TODO Add in the release duration from the request.
    @Override
    public ProcessingAction makeReleaseAction(final Platform platform)
        {
        log.debug(
            "makeReleaseAction for compute resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        MockPlatformSettings settings = ((MockPlatform) platform).getMockEntitySettings();

        IvoaLifecyclePhase waitPhase = null ;
        IvoaLifecyclePhase donePhase = null ;
        
        if (this.releaseActionCount++ >= settings.getReleaseCount())
            {
            donePhase = IvoaLifecyclePhase.COMPLETED;
            }

        return new SimpleSleepAction(
            this,
            Duration.ofMillis(
                settings.getReleaseDelay()
                    ),
            waitPhase,
            donePhase
            );
        }
    }
