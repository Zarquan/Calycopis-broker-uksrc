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
 *
 */

package net.ivoa.calycopis.broker.engine.functional.processing.action;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponent;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingActionBase;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecyclePhase;

/**
 * A ProcessingAction that simply waits for a specified time before transitioning to a new state.
 * 
 */
@Slf4j
public class SimpleSleepAction
extends ComponentProcessingActionBase
implements ComponentProcessingAction
    {
    private Duration sleepDuration ;

    private IvoaLifecyclePhase waitPhase ;
    private IvoaLifecyclePhase donePhase ;

    public SimpleSleepAction(final LifecycleComponent component, final Duration sleepDuration)
        {
        this(
            component,
            sleepDuration,
            null,
            null
            );
        }

    public SimpleSleepAction(final LifecycleComponent component, final Duration sleepDuration, final IvoaLifecyclePhase waitPhase, final IvoaLifecyclePhase donePhase)
        {
        super(component);
        this.sleepDuration = sleepDuration ;
        this.waitPhase = waitPhase ;
        this.donePhase = donePhase ;
        }

    @Override
    public void preProcess(LifecycleComponent component)
        {
        log.debug(
            "Pre-processing component [{}][{}]",
            this.getComponentUuid(),
            this.getComponentClassName()
            );
        if (this.waitPhase != null)
            {
            component.setPhase(
                this.waitPhase
                );
            }
        }
    
    @Override
    public void process()
        {
        log.debug(
            "Processing component [{}][{}]",
            this.getComponentUuid(),
            this.getComponentClassName()
            );
        if (this.sleepDuration.isPositive())
            {
            try {
                Thread.sleep(
                    this.sleepDuration.toMillis()
                    );
                }
            catch (InterruptedException ouch)
                {
                log.error(
                    "Sleep action for [{}][{}] interrupted [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName(),
                    ouch.getClass().getSimpleName(),
                    ouch.getMessage()
                    );
                }
            }
        }

    @Override
    public void postProcess(LifecycleComponent component)
        {
        log.debug(
            "Post-processing [{}][{}]",
            this.getComponentUuid(),
            this.getComponentClassName()
            );
        if (this.donePhase != null)
            {
            component.setPhase(
                donePhase
                );
            }
        }
    }
