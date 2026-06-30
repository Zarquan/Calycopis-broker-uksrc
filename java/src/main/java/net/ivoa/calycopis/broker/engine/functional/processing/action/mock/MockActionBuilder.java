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

package net.ivoa.calycopis.broker.engine.functional.processing.action.mock;

import java.time.Duration;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponentEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.platform.mock.MockPlatform;
import net.ivoa.calycopis.broker.engine.functional.platform.mock.MockPlatformSettings;
import net.ivoa.calycopis.broker.engine.functional.processing.action.ProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.action.SimpleSleepAction;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecyclePhase;

/**
 * 
 */
@Slf4j
@Embeddable
public class MockActionBuilder
    {
    private LifecycleComponentEntity component;
    
    /**
     * 
     */
    public MockActionBuilder(final LifecycleComponentEntity component)
        {
        this.component = component;
        }

    @Column(name = "prepare_action_count")
    private long prepareActionCount = 0;

    // TODO Add in the prepare duration from the request.
    public ProcessingAction makePrepareAction(final Platform platform)
        {
        log.debug(
            "Making prepare action for [{}][{}]",
            this.component.getUuid(),
            this.component.getClass().getSimpleName()
            );
        MockPlatformSettings settings = ((MockPlatform) platform).getMockEntitySettings();

        IvoaLifecyclePhase waitPhase = null ;
        IvoaLifecyclePhase donePhase = null ;
        
        if (this.prepareActionCount++ >= settings.getPrepareCount())
            {
            donePhase = IvoaLifecyclePhase.AVAILABLE;
            }
        
        return new SimpleSleepAction(
            this.component,
            Duration.ofMillis(
                settings.getPrepareDelay()
                ),
            waitPhase,
            donePhase
            );
        }

    @Column(name = "monitor_action_count")
    private long monitorActionCount = 0;

    // TODO Add in the available duration from the request.
    public ProcessingAction makeMonitorAction(Platform platform)
        {
        log.debug(
            "Making monitor action for [{}][{}]",
            this.component.getUuid(),
            this.component.getClass().getSimpleName()
            );
        MockPlatformSettings settings = ((MockPlatform) platform).getMockEntitySettings();
 
        IvoaLifecyclePhase waitPhase = null ;
        IvoaLifecyclePhase donePhase = null ;
        
        return new SimpleSleepAction(
            this.component,
            Duration.ofMillis(
                settings.getMonitorDelay()
                ),
            waitPhase,
            donePhase
            );
        }

    @Column(name = "release_action_count")
    private long releaseActionCount = 0;

    // TODO Add in the release duration from the request.
    public ProcessingAction makeReleaseAction(final Platform platform)
        {
        log.debug(
            "Making release action for [{}][{}]",
            this.component.getUuid(),
            this.component.getClass().getSimpleName()
            );
        MockPlatformSettings settings = ((MockPlatform) platform).getMockEntitySettings();

        IvoaLifecyclePhase waitPhase = null ;
        IvoaLifecyclePhase donePhase = null ;
        
        if (this.releaseActionCount++ >= settings.getReleaseCount())
            {
            donePhase = IvoaLifecyclePhase.COMPLETED;
            }

        return new SimpleSleepAction(
            this.component,
            Duration.ofMillis(
                settings.getReleaseDelay()
                    ),
            waitPhase,
            donePhase
            );
        }
    }
