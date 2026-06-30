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

package net.ivoa.calycopis.broker.engine.functional.processing.component;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponent;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecyclePhase;

/**
 * 
 */
@Slf4j
public class ComponentProcessingActionBase
implements ComponentProcessingAction
    {
    private final UUID componentUuid ;
    public UUID getComponentUuid()
        {
        return this.componentUuid ;
        }
    
    private final String componentClassName ;
    public String getComponentClassName()
        {
        return this.componentClassName ;
        }

    private IvoaLifecyclePhase nextPhase ;
    public IvoaLifecyclePhase getNextPhase()
        {
        return this.nextPhase ;
        }
    public void setNextPhase(IvoaLifecyclePhase nextPhase)
        {
        this.nextPhase = nextPhase ;
        }

    /**
     * Public constructor.
     *   
     */
    public ComponentProcessingActionBase(final LifecycleComponent component)
        {
        this(
            component,
            null
            );
        }

    /**
     * Public constructor.
     *   
     */
    public ComponentProcessingActionBase(final LifecycleComponent component, final IvoaLifecyclePhase nextPhase)
        {
        this.componentUuid = component.getUuid();
        this.componentClassName = component.getClass().getSimpleName();
        this.nextPhase = nextPhase;
        }
    
    @Override
    public void preProcess(final LifecycleComponent component)
        {
        log.debug(
            "Pre-processing action for component [{}][{}]",
            this.getComponentUuid(),
            this.getComponentClassName()
            );
        }

    @Override
    public void process()
        {
        log.debug(
            "Processing action for component [{}][{}]",
            this.getComponentUuid(),
            this.getComponentClassName()
            );
        }
    
    @Override
    public void postProcess(final LifecycleComponent component)
        {
        log.debug(
            "Post-processing action for component [{}][{}]",
            this.getComponentUuid(),
            this.getComponentClassName()
            );
        if (this.getNextPhase() != null)
            {
            component.setPhase(
                this.getNextPhase()
                );
            }
        }
    }
