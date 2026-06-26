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
 *       "value": 20,
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

package net.ivoa.calycopis.broker.engine.entities.storage.simple.docker.bind;

import java.net.URI;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponent;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageLinker;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageResourceValidator;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.SimpleStorageResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.docker.DockerStorageLinker;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.processing.ProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingActionBase;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecyclePhase;

/**
 * 
 */
@Slf4j
@Entity
@Table(
    name = "dockerbindmountstorage"
    )
@DiscriminatorValue(
    value="uri:docker-bind-storage"
    )
public class DockerBindMountStorageEntity
extends SimpleStorageResourceEntity
implements DockerBindMountStorage
    {

    /**
     * 
     */
    public DockerBindMountStorageEntity()
        {
        super();
        }

    /**
     * 
     */
    public DockerBindMountStorageEntity(
        final SimpleExecutionSessionEntity session,
        final AbstractStorageResourceValidator.Result result,
        final String hostPath
        ){
        super(
            session,
            result
            );
        this.hostPath = hostPath;
        }

    // The host path for the volume.
    private String hostPath;
    @Override
    public String getMountPath()
        {
        return this.hostPath;
        }
    
    @Override
    public void link(final AbstractStorageLinker linker)
        {
        if (linker instanceof DockerStorageLinker)
            {
            DockerStorageLinker dockerLinker = (DockerStorageLinker) linker;
            String sourcePath = this.hostPath;
            if (sourcePath != null && sourcePath.startsWith("file:"))
                {
                sourcePath = URI.create(sourcePath).getPath();
                }
            log.debug(
                "DockerBindMountStorageEntity linking source path [{}]",
                sourcePath
                );
            dockerLinker.setSourcePath(sourcePath);
            }
        }

    @Override
    protected ProcessingAction makePrepareAction(
        final Platform platform
        ){
        return new ComponentProcessingActionBase(this)
            {
            @Override
            public void preProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Pre-processing prepare action for component [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                }

            @Override
            public void process()
                {
                log.debug(
                    "Processing prepare action for component [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                }

            @Override
            public void postProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Post-processing prepare action for component [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                component.setPhase(
                    IvoaLifecyclePhase.AVAILABLE
                    );
                }
            };
        }

    @Override
    protected ProcessingAction makeReleaseAction(
        final Platform platform
        ){
        return new ComponentProcessingActionBase(this)
            {
            @Override
            public void preProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Pre-processing release action for component [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                }

            @Override
            public void process()
                {
                log.debug(
                    "Processing release action for component [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                }

            @Override
            public void postProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Post-processing release action for component [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                component.setPhase(
                    IvoaLifecyclePhase.COMPLETED
                    );
                }
            };
        }
    }
