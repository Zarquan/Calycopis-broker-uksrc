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
 *     },
 *     {
 *     "timestamp": "2026-11-19T17:35:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 25,
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
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageLinker;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageResourceValidator;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.SimpleStorageResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.docker.DockerStorageLinker;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.processing.action.ProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingRequest;
import net.ivoa.calycopis.openapi.spring.model.IvoaLifecyclePhase;

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
    
    /**
     * Resolve a location to the source path for a bind mount.
     *
     * Returns null when the location is null, has no path, or cannot be
     * parsed as a URI. A file://None location resolves to an empty path,
     * which callers treat as missing.
     *
     */
    private static String resolveSourcePath(
        final String location
        ){
        if (location == null)
            {
            return null;
            }
        if (! location.startsWith("file:"))
            {
            return location;
            }
        try {
            return URI.create(location).getPath();
            }
        catch (IllegalArgumentException ouch)
            {
            log.debug(
                "Unable to parse bind mount source [{}][{}]",
                location,
                ouch.getMessage()
                );
            return null;
            }
        }

    @Override
    public void link(final AbstractStorageLinker linker)
        {
        log.debug(
            "Link request for storage resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        if (linker instanceof DockerStorageLinker)
            {
            DockerStorageLinker dockerLinker = (DockerStorageLinker) linker;
            String sourcePath = resolveSourcePath(this.hostPath);
            if ((sourcePath == null) || sourcePath.isBlank())
                {
                log.error(
                    "Unable to resolve bind mount source [{}] for storage resource [{}][{}]",
                    this.hostPath,
                    this.getUuid(),
                    this.getClass().getSimpleName()
                    );
                sourcePath = null;
                }
            log.debug(
                "Linking path [{}] for storage resource [{}][{}]",
                sourcePath,
                this.getUuid(),
                this.getClass().getSimpleName()
                );
            dockerLinker.setSourcePath(
                sourcePath
                );
            }
        else {
            log.error(
                "Unexpected linker class [{}] storage resource [{}][{}]",
                linker.getClass().getSimpleName(),
                this.getUuid(),
                this.getClass().getSimpleName()
                );
            }
        }

    @Override
    protected ProcessingAction makePrepareAction(
        final Platform platform
        ){
        log.debug(
            "makePrepareAction for storage resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        //
        // The broker cannot check the Podman host filesystem from inside
        // its own container, so check the source path shape here. A path
        // that does not exist on the host is reported by the Docker API
        // when the compute container is created.
        String sourcePath = resolveSourcePath(this.hostPath);
        if ((sourcePath == null) || sourcePath.isBlank())
            {
            log.error(
                "Missing bind mount source [{}] for storage resource [{}][{}]",
                this.hostPath,
                this.getUuid(),
                this.getClass().getSimpleName()
                );
            this.addError(
                "urn:missing-required-value",
                "File bind mount source path required"
                );
            this.setPhase(
                IvoaLifecyclePhase.FAILED
                );
            return ProcessingAction.NO_ACTION;
            }
        this.setPhase(
            IvoaLifecyclePhase.AVAILABLE
            );
        return ProcessingAction.NO_ACTION;
        }

    @Override
    protected ProcessingAction makeMonitorAction(Platform platform)
        {
        log.debug(
            "makeMonitorAction for storage resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        // As long as it still exists, we are good.
        return ProcessingAction.NO_ACTION;
        }
    
    @Override
    protected ProcessingAction makeReleaseAction(
        final Platform platform
        ){
        log.debug(
            "makeReleaseAction for storage resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        // We are done.
        this.setPhase(
            IvoaLifecyclePhase.COMPLETED
            );
        return ProcessingAction.NO_ACTION;
        }
    }
