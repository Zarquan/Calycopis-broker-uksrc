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

package net.ivoa.calycopis.broker.engine.entities.executable.docker.docker;

import java.util.List;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponent;
import net.ivoa.calycopis.broker.engine.entities.executable.docker.DockerContainerEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.platform.docker.DockerClientFactory;
import net.ivoa.calycopis.broker.engine.functional.platform.docker.DockerPlatform;
import net.ivoa.calycopis.broker.engine.functional.processing.action.ProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingActionBase;
import net.ivoa.calycopis.openapi.spring.model.IvoaLifecyclePhase;

/**
 * Docker platform specific DockerContainer entity.
 * Handles image pulling and digest verification when preparing the executable.
 * 
 */
@Slf4j
@Entity
@Table(
    name = "dockerdockercontainers"
    )
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public class DockerDockerContainerEntity
extends DockerContainerEntity
implements DockerDockerContainer
    {

    /**
     * Protected constructor for JPA entities.
     * 
     */
    protected DockerDockerContainerEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factory.
     *
     */
    protected DockerDockerContainerEntity(
        final SimpleExecutionSessionEntity session,
        final DockerDockerContainerValidator.Result result
        ){
        super(
            session,
            result
            );
        }

    @Column(name="imagedownloadmillis")
    private Long imageDownloadMillis;

    @Override
    public Long getImageDownloadMillis()
        {
        return this.imageDownloadMillis;
        }

    @Override
    protected ProcessingAction makePrepareAction(final Platform platform)
        {
        log.debug(
            "makePrepareAction for docker container [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        // Eagerly resolve data from the Hibernate session while still in a transaction.
        final DockerContainerImage dockerContainerImage = this.getImage();
        final String imageName;
        final String requestedDigest;

        if (dockerContainerImage == null)
            {
            // TODO fail the prepare step
            return ProcessingAction.NO_ACTION;
            }
        else {
            // TODO We should iterate the list rather than just taking the first one.
            List<String> locations = dockerContainerImage.getLocations();
            if (locations != null && !locations.isEmpty())
                {
                imageName = locations.get(0);
                }
            else {
                imageName = null;
                }
            requestedDigest = dockerContainerImage.getDigest();
            }

        final DockerClientFactory clientFactory ;
        if (platform instanceof DockerPlatform)
            {
            clientFactory = ((DockerPlatform) platform).getDockerClientFactory();
            }
        else {
            clientFactory = null;
            log.error(
                "Unexpected platform type [{}] docker container [{}][{}]",
                platform.getClass().getSimpleName(),
                this.getUuid(),
                this.getClass().getSimpleName()
                );
            // TODO fail the prepare step
            return ProcessingAction.NO_ACTION;
            }
        
        return new ComponentProcessingActionBase(this)
            {
            private long downloadTimeMillis = 0L;

            @Override
            public void preProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Pre-processing prepare action for docker container [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                }
            
            @Override
            public void process()
                {
                log.debug(
                    "Processing prepare action for docker container [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );

                if (imageName == null)
                    {
                    log.error(
                        "No image location for docker container [{}][{}]",
                        this.getComponentUuid(),
                        this.getComponentClassName()
                        );
                    // TODO Add a message explaining why it failed.
                    this.setNextPhase(
                        IvoaLifecyclePhase.FAILED
                        );
                    return;
                    }

                try {
                    DockerClient dockerClient = clientFactory.getDockerClient();
                    if (dockerClient == null)
                        {
                        log.error(
                            "CONTAINER_HOST / DOCKER_HOST environment variable is not set"
                            );
                        // TODO Add a message explaining why it failed.
                        this.setNextPhase(
                            IvoaLifecyclePhase.FAILED
                            );
                        return;
                        }

                    // Check if the image is already in the local cache.
                    boolean imageAvailable = false;
                    try {
                        InspectImageResponse imageInfo = dockerClient.inspectImageCmd(imageName).exec();
                        log.debug(
                            "Image [{}] found in local cache, id [{}]",
                            imageName,
                            imageInfo.getId()
                            );

                        // Verify the digest if one was requested.
                        if (requestedDigest != null && !requestedDigest.isEmpty())
                            {
                            boolean digestMatch = checkDigest(
                                imageInfo,
                                requestedDigest
                                );
                            if (digestMatch)
                                {
                                log.debug(
                                    "Image [{}] digest matches [{}][{}]",
                                    imageName,
                                    requestedDigest,
                                    imageInfo.getId()
                                    );
                                imageAvailable = true;
                                }
                            else {
                                log.error(
                                    "Image [{}] found in local cache, but digest does not match [{}][{}]",
                                    imageName,
                                    requestedDigest,
                                    imageInfo.getId()
                                    );
                                // TODO Add a message explaining why it failed.
                                this.setNextPhase(
                                    IvoaLifecyclePhase.FAILED
                                    );
                                return;
                                }
                            }
                        else {
                            imageAvailable = true;
                            }
                        }
                    catch (NotFoundException ouch)
                        {
                        log.debug(
                            "Image [{}] not in local cache, [{}][{}]",
                            imageName,
                            ouch.getClass().getSimpleName(),
                            ouch.getMessage()
                            );
                        }

                    // Pull the image if it's not already available.
                    if (!imageAvailable)
                        {
                        log.debug(
                            "Pulling Docker image [{}]",
                            imageName
                            );
                        long pullStart = System.currentTimeMillis();
                        dockerClient.pullImageCmd(imageName)
                            .exec(new PullImageResultCallback())
                            .awaitCompletion();
                        this.downloadTimeMillis = System.currentTimeMillis() - pullStart;
                        log.debug(
                            "Image [{}] pulled in [{}] ms",
                            imageName,
                            this.downloadTimeMillis
                            );

                        // Verify the digest after download if requested.
                        if (requestedDigest != null && !requestedDigest.isEmpty())
                            {
                            InspectImageResponse downloadedImage = dockerClient.inspectImageCmd(imageName).exec();
                            boolean digestMatch = checkDigest(
                                downloadedImage,
                                requestedDigest
                                );
                            if (!digestMatch)
                                {
                                log.error(
                                    "Digest does not match [{}][{}] for docker container [{}][{}] image [{}]",
                                    requestedDigest,
                                    downloadedImage.getId(),
                                    this.getComponentUuid(),
                                    this.getComponentClassName(),
                                    imageName
                                    );
                                // TODO Add a message explaining why it failed.
                                this.setNextPhase(
                                    IvoaLifecyclePhase.FAILED
                                    );
                                return;
                                }
                            }
                        }
                    }
                catch (Exception ouch)
                    {
                    log.error(
                        "Failed to prepare image [{}] for docker container [{}][{}], exception [{}][{}]",
                        imageName,
                        this.getComponentUuid(),
                        this.getComponentClassName(),
                        ouch.getClass().getSimpleName(),
                        ouch.getMessage()
                        );
                    // TODO Add a message explaining why it failed.
                    this.setNextPhase(
                        IvoaLifecyclePhase.FAILED
                        );
                    return;
                    }
                // If we got this far, the image is available.
                this.setNextPhase(
                    IvoaLifecyclePhase.AVAILABLE
                    );
                }

            private boolean checkDigest(
                final InspectImageResponse imageInfo,
                final String digest
                ){
                String localId = imageInfo.getId();
                if (localId != null && localId.contains(digest))
                    {
                    return true;
                    }
                List<String> repoDigests = imageInfo.getRepoDigests();
                if (repoDigests != null)
                    {
                    for (String repoDigest : repoDigests)
                        {
                        if (repoDigest != null && repoDigest.contains(digest))
                            {
                            return true;
                            }
                        }
                    }
                return false;
                }

            @Override
            public void postProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Post-processing prepare action for docker container [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                if (component instanceof DockerDockerContainerEntity)
                    {
                    this.postProcess(
                        (DockerDockerContainerEntity) component
                        );
                    }
                else {
                    log.error(  
                        "Unexpected type for docker container [{}][{}]",
                        component.getUuid(),
                        component.getClass().getSimpleName()
                        );
                    component.addError(
                        "uri:internal-error",
                        "Unexpected component type, see logs for details"
                        );
                    component.setPhase(
                        IvoaLifecyclePhase.FAILED
                        );
                    }
                }

            public void postProcess(final DockerDockerContainerEntity component)
                {
                log.debug(
                    "Post-processing prepare action for docker container [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                component.imageDownloadMillis = this.downloadTimeMillis;
                component.setPhase(
                    this.getNextPhase()
                    );
                }
            };
        }

    @Override
    protected ProcessingAction makeMonitorAction(Platform platform)
        {
        log.debug(
            "makeMonitorAction for docker container [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        return new ComponentProcessingActionBase(this)
            {
            @Override
            public void process()
                {
                log.debug(
                    "Processing monitor action for docker container [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                //
                // Check the image is healthy ?
                //
                }
            };
        }

    @Override
    protected ProcessingAction makeReleaseAction(Platform platform)
        {
        log.debug(
            "makeReleaseAction for docker container [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        return new ComponentProcessingActionBase(this)
            {
            @Override
            public void process()
                {
                log.debug(
                    "Processing release action for docker container [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                //
                // Release our lease on the image.
                //
                this.setNextPhase(
                    IvoaLifecyclePhase.COMPLETED
                    );
                }
            };
        }
    }
