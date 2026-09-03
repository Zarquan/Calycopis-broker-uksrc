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
 *       "value": 40,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-27T06:10:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 1,
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
 *     "timestamp": "2026-08-27T09:00:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-08-27T08:52:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-08-27T11:19:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 3,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-08-27T11:40:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 3,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute.simple.docker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponent;
import net.ivoa.calycopis.broker.engine.entities.compute.simple.SimpleComputeResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.data.AbstractDataResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.executable.AbstractExecutableEntity;
import net.ivoa.calycopis.broker.engine.entities.executable.docker.DockerContainer;
import net.ivoa.calycopis.broker.engine.entities.executable.docker.DockerContainerEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionConnectorEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageResource;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.docker.DockerStorageLinkerImpl;
import net.ivoa.calycopis.broker.engine.entities.volume.AbstractVolumeMountEntity;
import net.ivoa.calycopis.broker.engine.entities.volume.simple.SimpleVolumeMountEntity;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceOffer;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.platform.docker.DockerClientFactory;
import net.ivoa.calycopis.broker.engine.functional.platform.docker.DockerPlatform;
import net.ivoa.calycopis.broker.engine.functional.processing.action.ProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingActionBase;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingRequest;
import net.ivoa.calycopis.openapi.spring.model.IvoaLifecyclePhase;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleSessionConnector;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleVolumeMount.ModeEnum;

/**
 * A Docker SimpleComputeResource entity.
 *
 */
@Slf4j
@Entity
@Table(
    name = "dockersimplecomputeresources"
    )
@DiscriminatorValue(
    value = "uri:docker-simple-compute-resources"
    )
public class DockerSimpleComputeResourceEntity
extends SimpleComputeResourceEntity
implements DockerSimpleComputeResource
    {

    /**
     * The kind URI for the Docker container stdout access connector.
     *
     */
    public static final String STDOUT_GET_CONNECTOR_KIND = "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stdout-get.yaml";

    /**
     * The kind URI for the Docker container stderr access connector.
     *
     */
    public static final String STDERR_GET_CONNECTOR_KIND = "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stderr-get.yaml";

    /**
     * The access protocol for the Docker container session connectors.
     *
     */
    public static final String STDIO_GET_CONNECTOR_PROTOCOL = "HTTP";

    /**
     * Protected constructor for JPA entities.
     *
     */
    protected DockerSimpleComputeResourceEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factory.
     *
     */
    protected DockerSimpleComputeResourceEntity(
        final SimpleExecutionSessionEntity session,
        final DockerSimpleComputeResourceValidator.Result result,
        final SimpleComputeResourceOffer offer
        ){
        super(
            session,
            result,
            offer
            );
        //
        // Add connectors to the session for access to the captured
        // container stdout and stderr.
        // These start in the PREPARING state and are updated as the
        // container execution progresses.
        session.addConnector(
            STDOUT_GET_CONNECTOR_KIND,
            IvoaSimpleSessionConnector.StatusEnum.PREPARING,
            STDIO_GET_CONNECTOR_PROTOCOL,
            null
            );
        session.addConnector(
            STDERR_GET_CONNECTOR_KIND,
            IvoaSimpleSessionConnector.StatusEnum.PREPARING,
            STDIO_GET_CONNECTOR_PROTOCOL,
            null
            );
        }
    
    @Column(name="dockercontainerid")
    private String dockerContainerId;
    public String getDockerContainerId()
        {
        return this.dockerContainerId;
        }

    @Column(name="dockercontainerexitcode")
    private Integer dockerContainerExitCode;
    @Override
    public Integer getDockerContainerExitCode()
        {
        return this.dockerContainerExitCode;
        }

    @Lob
    @Column(name="containerstdout")
    private String containerStdout;
    @Override
    public String getContainerStdout()
        {
        return this.containerStdout;
        }

    @Lob
    @Column(name="containerstderr")
    private String containerStderr;
    @Override
    public String getContainerStderr()
        {
        return this.containerStderr;
        }

    /**
     * Set the lifecycle phase.
     * When the compute resource finishes its execution,
     * [COMPLETED, CANCELLED, FAILED], mark the session connectors as FINISHED.
     *
     */
    @Override
    public void setPhase(final IvoaLifecyclePhase newphase)
        {
        super.setPhase(
            newphase
            );
        if (newphase == IvoaLifecyclePhase.COMPLETED
            || newphase == IvoaLifecyclePhase.CANCELLED
            || newphase == IvoaLifecyclePhase.FAILED)
            {
            this.markSessionConnectorsFinished();
            }
        }

    /**
     * Update the connector with the given kind on the parent session.
     * The status is always applied, the location is only applied
     * when it is not null.
     *
     */
    protected void updateSessionConnector(final String kind, final IvoaSimpleSessionConnector.StatusEnum status, final String location)
        {
        if (this.session == null)
            {
            log.warn(
                "No session for compute resource [{}], unable to update connector [{}]",
                this.getUuid(),
                kind
                );
            return;
            }
        for (SimpleExecutionSessionConnectorEntity connector : this.session.getConnectors())
            {
            if (kind.equals(connector.getKind()))
                {
                connector.setStatus(
                    status
                    );
                if (location != null)
                    {
                    connector.setLocation(
                        location
                        );
                    }
                return;
                }
            }
        log.warn(
            "No connector found with kind [{}] for session [{}]",
            kind,
            this.session.getUuid()
            );
        }

    /**
     * Mark the stdout and stderr session connectors as AVAILABLE
     * and set their endpoint locations.
     * Called once the container logs have been captured.
     *
     */
    protected void markSessionConnectorsAvailable()
        {
        if (this.session == null)
            {
            log.warn(
                "No session for compute resource [{}], unable to mark session connectors available",
                this.getUuid()
                );
            return;
            }
        String locationPrefix = "sessions/" + this.session.getUuid() + "/docker/";
        this.updateSessionConnector(
            STDOUT_GET_CONNECTOR_KIND,
            IvoaSimpleSessionConnector.StatusEnum.AVAILABLE,
            locationPrefix + "stdout-get"
            );
        this.updateSessionConnector(
            STDERR_GET_CONNECTOR_KIND,
            IvoaSimpleSessionConnector.StatusEnum.AVAILABLE,
            locationPrefix + "stderr-get"
            );
        }

    /**
     * Mark the stdout and stderr session connectors as FINISHED.
     * Called when the compute resource execution has finished,
     * [COMPLETED, CANCELLED, FAILED].
     * The connector locations are left unchanged so the captured
     * logs remain accessible.
     *
     */
    protected void markSessionConnectorsFinished()
        {
        this.updateSessionConnector(
            STDOUT_GET_CONNECTOR_KIND,
            IvoaSimpleSessionConnector.StatusEnum.FINISHED,
            null
            );
        this.updateSessionConnector(
            STDERR_GET_CONNECTOR_KIND,
            IvoaSimpleSessionConnector.StatusEnum.FINISHED,
            null
            );
        }

    /**
     * Maximum number of characters to capture per stream.
     * Output beyond this limit is truncated from the beginning,
     * keeping only the last MAX_LOG_CHARS characters.
     */
    private static final int MAX_LOG_CHARS = 64 * 1024;

    /**
     * Capture stdout and stderr from a stopped Docker container
     * using the Docker log API.
     */
    private static void captureContainerLogs(
        final DockerClient dockerClient,
        final String containerId,
        final StringBuilder stdoutBuilder,
        final StringBuilder stderrBuilder
        ){
        try {
            dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(false)
                .withTailAll()
                .exec(new ResultCallback.Adapter<Frame>()
                    {
                    @Override
                    public void onNext(Frame frame)
                        {
                        String text = new String(
                            frame.getPayload(),
                            java.nio.charset.StandardCharsets.UTF_8
                            );
                        if (frame.getStreamType() == StreamType.STDOUT)
                            {
                            stdoutBuilder.append(text);
                            }
                        else if (frame.getStreamType() == StreamType.STDERR)
                            {
                            stderrBuilder.append(text);
                            }
                        }
                    })
                .awaitCompletion();
            }
        catch (Exception e)
            {
            // TODO add messages
            log.warn(
                "Failed to capture logs for container [{}]: {}",
                containerId,
                e.getMessage()
                );
            }
        }

    /**
     * Truncate a string to at most maxChars characters,
     * keeping the tail (most recent output).
     */
    private static String truncateLog(final String text, final int maxChars)
        {
        if (text == null || text.length() <= maxChars)
            {
            return text;
            }
        return "...[truncated]...\n" + text.substring(text.length() - maxChars);
        }

    @Override
    protected ProcessingAction makePrepareAction(final Platform platform)
        {
        log.debug(
            "makePrepareAction for compute resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        // Eagerly resolve all data from the Hibernate session while still inside the transaction.
        final Long maxCores = this.getMaxOfferedCores();
        final Long maxMemory = this.getMaxOfferedMemory();

        final List<Bind> bindList = new ArrayList<Bind>();
        log.debug(
            "Resolving volume mounts for compute resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        for (AbstractVolumeMountEntity volumeMount : this.getVolumeMountEntities())
            {
            log.debug(
                "Volume mount [{}] type [{}]",
                volumeMount.getUuid(),
                volumeMount.getClass().getSimpleName()
                );
            if (volumeMount instanceof SimpleVolumeMountEntity)
                {
                SimpleVolumeMountEntity simpleMount = (SimpleVolumeMountEntity) volumeMount;
                AbstractDataResourceEntity dataResource = simpleMount.getDataResource();
                log.debug(
                    "SimpleVolumeMount [{}] dataResource [{}]",
                    simpleMount.getUuid(),
                    dataResource != null ? dataResource.getUuid() + " " + dataResource.getClass().getSimpleName() : "null"
                    );
                if (dataResource != null)
                    {
                    AbstractStorageResource storage = dataResource.getStorage();
                    log.debug(
                        "DataResource [{}] storage [{}]",
                        dataResource.getUuid(),
                        storage != null ? storage.getClass().getSimpleName() : "null"
                        );
                    if (storage != null)
                        {
                        String containerPath = simpleMount.getPath();
                        AccessMode accessMode = AccessMode.rw;
                        if (simpleMount.getMode() == ModeEnum.READONLY)
                            {
                            accessMode = AccessMode.ro;
                            }
                        DockerStorageLinkerImpl linkerBean = new DockerStorageLinkerImpl(
                            containerPath,
                            accessMode
                            );
                        storage.link(linkerBean);
                        if (linkerBean.isComplete())
                            {
                            Bind bind = linkerBean.toBind();
                            log.debug(
                                "Adding bind [{}] for volume mount [{}]",
                                bind,
                                simpleMount.getUuid()
                                );
                            bindList.add(bind);
                            }
                        else {
                            log.error(
                                "Linker bean incomplete for volume mount [{}], storage [{}] - skipping",
                                simpleMount.getUuid(),
                                storage.getClass().getSimpleName()
                                );
                            // TODO add messages
                            this.setPhase(
                                IvoaLifecyclePhase.FAILED
                                );
                            return ProcessingAction.NO_ACTION;
                            }
                        }
                    }
                }
            else {
                log.error(
                    "Unexpected class for volume mount [{}][{}]",
                    volumeMount.getUuid(),
                    volumeMount.getClass().getSimpleName()
                    );
                // TODO add messages
                this.setPhase(
                    IvoaLifecyclePhase.FAILED
                    );
                return ProcessingAction.NO_ACTION;
                }
            }
        log.debug(
            "Resolved [{}] bind mounts for compute resource [{}][{}]",
            bindList.size(),
            this.getUuid(),
            this.getClass().getSimpleName()
            );

        final AbstractExecutableEntity executable = this.session.getExecutable();
        final String imageName;
        final List<String> variablesList = new ArrayList<String>();
        final List<String> commandList = new ArrayList<String>();

        if (executable instanceof DockerContainerEntity)
            {
            DockerContainerEntity dockerExecutable = (DockerContainerEntity) executable;
            DockerContainer.DockerContainerImage image = dockerExecutable.getImage();
            // TODO We should iterate the list rather than just taking the first one.
            if (image != null && image.getLocations() != null && !image.getLocations().isEmpty())
                {
                imageName = image.getLocations().get(0);
                }
            else {
                // TODO add messages
                log.error("Unable to get image location");
                this.setPhase(
                    IvoaLifecyclePhase.FAILED
                    );
                return ProcessingAction.NO_ACTION;
                }
            Map<String, String> environment = dockerExecutable.getEnvironment();
            if (environment != null)
                {
                for (Map.Entry<String, String> entry : environment.entrySet())
                    {
                    variablesList.add(entry.getKey() + "=" + entry.getValue());
                    }
                }
            List<String> command = dockerExecutable.getCommand();
            if (command != null)
                {
                commandList.addAll(command);
                }
            }
        else {
            log.error(
                "Unexpected class for executable [{}][{}]",
                executable.getUuid(),
                executable.getClass().getSimpleName()
                );
            // TODO add messages
            this.setPhase(
                IvoaLifecyclePhase.FAILED
                );
            return ProcessingAction.NO_ACTION;
            }

        final DockerClientFactory clientFactory ;
        if (platform instanceof DockerPlatform)
            {
            clientFactory = ((DockerPlatform) platform).getDockerClientFactory();
            }
        else {
            log.error(
                "Unexpected class for platform [{}]",
                platform.getClass().getSimpleName()
                );
            // TODO add messages
            this.setPhase(
                IvoaLifecyclePhase.FAILED
                );
            return ProcessingAction.NO_ACTION;
            }
        
        return new ComponentProcessingActionBase(this, IvoaLifecyclePhase.RUNNING)
            {

            private String containerId;

            @Override
            public void preProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Pre-processing prepare action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                }
            
            @Override
            public void process()
                {
                log.debug(
                    "Processing prepare action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );

                try {
                    DockerClient dockerClient = clientFactory.getDockerClient();
                    if (dockerClient == null)
                        {
                        // TODO add messages
                        log.error(
                            "Unable to create Docker client. CONTAINER_HOST / DOCKER_HOST environment variable may not be set"
                            );
                        this.setNextPhase(
                            IvoaLifecyclePhase.FAILED
                            );
                        // TODO Add some messages to explain why.
                        return ;
                        }

                    //
                    // Verify the image is in the local cache.
                    // The image should already have been pulled by the
                    // DockerDockerContainerEntity prepare phase.
                    try {
                        dockerClient.inspectImageCmd(imageName).exec();
                        log.debug(
                            "Image [{}] found in local cache",
                            imageName
                            );
                        }
                    catch (NotFoundException e)
                        {
                        // TODO add messages
                        log.error(
                            "Image [{}] not cached for compute resource [{}][{}]",
                            imageName,
                            this.getComponentUuid(),
                            this.getComponentClassName()
                            );
                        this.setNextPhase(
                            IvoaLifecyclePhase.FAILED
                            );
                        return;
                        }

                    //
                    // Configure host resource limits based on offered cores and memory.
                    // TODO also check the minCores and MinMemory.
                    HostConfig hostConfig = HostConfig.newHostConfig();
                    boolean hasResourceLimits = false;
                    if (maxCores != null)
                        {
                        long nanoCpus = maxCores * 1_000_000_000L;
                        hostConfig.withNanoCPUs(nanoCpus);
                        hasResourceLimits = true;
                        }
                    if (maxMemory != null)
                        {
                        long memoryBytes = maxMemory * 1_073_741_824L;
                        hostConfig.withMemory(memoryBytes);
                        hasResourceLimits = true;
                        }

                    //
                    // Add bind mounts to the host configuration.
                    if (!bindList.isEmpty())
                        {
                        hostConfig.withBinds(bindList);
                        }

                    //
                    // Create and start the container, retrying without resource limits
                    // if the cgroup controllers are not available (e.g. nested containers).
                    // TODO Make this a configuration flag - allow resource limits - and fail if this causes a problem.
                    this.containerId = createAndStartContainer(
                        dockerClient,
                        imageName,
                        variablesList,
                        commandList,
                        hostConfig,
                        this.getComponentUuid()
                        );
                    if (this.containerId == null && hasResourceLimits)
                        {
                        log.warn(
                            "Retrying without resource limits for [{}][{}]",
                            this.getComponentUuid(),
                            this.getComponentClassName()
                            );
                        HostConfig retryConfig = HostConfig.newHostConfig();
                        if (!bindList.isEmpty())
                            {
                            retryConfig.withBinds(bindList);
                            }
                        this.containerId = createAndStartContainer(
                            dockerClient,
                            imageName,
                            variablesList,
                            commandList,
                            retryConfig,
                            this.getComponentUuid()
                            );
                        }

                    if (this.containerId != null)
                        {
                        log.debug(
                            "Docker container started [{}] for compute resource [{}][{}]",
                            this.containerId,
                            this.getComponentUuid(),
                            this.getComponentClassName()
                            );
                        this.setNextPhase(
                            IvoaLifecyclePhase.RUNNING
                            );
                        }
                    else {
                        // TODO add messages
                        log.error(
                            "Failed to start Docker container for compute resource [{}][{}]",
                            this.getComponentUuid(),
                            this.getComponentClassName()
                            );
                        this.setNextPhase(
                            IvoaLifecyclePhase.FAILED
                            );
                        }
                    }
                catch (Exception ouch)
                    {
                    // TODO add messages
                    log.error(
                        "Failed to prepare Docker container for resource [{}][{}], exception [{}][{}]",
                        this.getComponentUuid(),
                        this.getComponentClassName(),
                        ouch.getClass().getSimpleName(),
                        ouch.getMessage()
                        );
                    this.setNextPhase(
                        IvoaLifecyclePhase.FAILED
                        );
                    }
                }

            @Override
            public void postProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Post-processing prepare action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                if (component instanceof DockerSimpleComputeResourceEntity)
                    {
                    postProcess(
                        (DockerSimpleComputeResourceEntity) component
                        );
                    }
                else {
                    // TODO add messages
                    log.error(  
                        "Unexpected component type [{}] post processing [{}][{}]",
                        component.getClass().getSimpleName(),
                        component.getUuid(),
                        component.getClass().getSimpleName()
                        );
                    this.setNextPhase(
                        IvoaLifecyclePhase.FAILED
                        );
                    // TODO Add some messages to explain why.
                    }
                }
                
            public void postProcess(final DockerSimpleComputeResourceEntity component)
                {
                log.debug(
                    "Post processing prepare action for compute resource [{}][{}]",
                    component.getUuid(),
                    component.getClass().getSimpleName()
                    );
                component.dockerContainerId = this.containerId;
                component.setPhase(
                    this.getNextPhase()
                    );
                }
            };
        }

    /**
     * Create and start a Docker container, returning the container ID on success or null on failure.
     * TODO Move this code into the main method.
     * 
     */
    private String createAndStartContainer(
        final DockerClient dockerClient,
        final String imageName,
        final List<String> envList,
        final List<String> cmdList,
        final HostConfig hostConfig,
        final UUID resourceUuid
        )
        {
        String id = null;
        try {
            log.debug(
                "Creating Docker container from image [{}]",
                imageName
                );
            var createCmd = dockerClient.createContainerCmd(imageName)
                .withEnv(envList)
                .withHostConfig(hostConfig);
            if (!cmdList.isEmpty())
                {
                createCmd.withCmd(cmdList);
                }
            CreateContainerResponse container = createCmd.exec();
            id = container.getId();

            log.debug(
                "Starting Docker container [{}]",
                id
                );
            dockerClient.startContainerCmd(id).exec();
            return id;
            }
        catch (Exception e)
            {
            log.warn(
                "Failed to create/start Docker container for [{}]: {}",
                resourceUuid,
                e.getMessage()
                );
            if (id != null)
                {
                try {
                    log.debug(
                        "Removing failed Docker container [{}] for resource [{}]",
                        id,
                        resourceUuid
                        );
                    dockerClient.removeContainerCmd(id)
                        .withForce(true)
                        .exec();
                    }
                catch (Exception removeEx)
                    {
                    log.warn(
                        "Failed to remove Docker container [{}] for resource [{}]: {}",
                        id,
                        resourceUuid,
                        removeEx.getMessage()
                        );
                    }
                }
            return null;
            }
        }

    @Override
    public ProcessingAction makeMonitorAction(final Platform platform)
        {
        log.debug(
            "makeMonitorAction for compute resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        final String containerId = this.dockerContainerId;

        if (containerId == null || containerId.isEmpty())
            {
            log.error(
                "No container ID for compute resource [{}][{}]",
                this.getUuid(),
                this.getClass().getSimpleName()
                );
            // TODO add messages
            this.setPhase(
                IvoaLifecyclePhase.FAILED
                );
            return ProcessingAction.NO_ACTION;
            }

        final DockerClientFactory clientFactory ;
        if (platform instanceof DockerPlatform)
            {
            clientFactory = ((DockerPlatform) platform).getDockerClientFactory();
            }
        else {
            clientFactory = null;
            log.error(
                "Unexpected platform type [{}] for compute resource [{}][{}]",
                platform.getClass().getSimpleName(),
                this.getUuid(),
                this.getClass().getSimpleName()
                );
            // TODO add messages
            this.setPhase(
                IvoaLifecyclePhase.FAILED
                );
            return ProcessingAction.NO_ACTION;
            }
        
        return new ComponentProcessingActionBase(this)
            {
            private Integer exitCode;
            private String capturedStdout;
            private String capturedStderr;

            @Override
            public void preProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Pre-processing monitor action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                }

            @Override
            public void process()
                {
                log.debug(
                    "Processing monitor action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                try {
                    DockerClient dockerClient = clientFactory.getDockerClient();
                    if (dockerClient == null)
                        {
                        log.error(
                            "CONTAINER_HOST / DOCKER_HOST environment variable is not set"
                            );
                        // TODO add messages
                        this.setNextPhase(
                            IvoaLifecyclePhase.FAILED
                            );
                        return ;
                        }

                    InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
                    InspectContainerResponse.ContainerState state = inspection.getState();

                    log.debug(
                        "Container [{}] state: status=[{}] running=[{}] exitCode=[{}]",
                        containerId,
                        state.getStatus(),
                        state.getRunning(),
                        state.getExitCode()
                        );

                    if (Boolean.TRUE.equals(state.getRunning()))
                        {
                        this.setNextPhase(
                            IvoaLifecyclePhase.RUNNING
                            );
                        }
                    else {
                        this.exitCode = state.getExitCode();

                        // Capture stdout and stderr before the container is removed.
                        log.debug(
                            "Capturing logs for container [{}]",
                            containerId
                            );
                        StringBuilder stdoutBuilder = new StringBuilder();
                        StringBuilder stderrBuilder = new StringBuilder();
                        captureContainerLogs(
                            dockerClient,
                            containerId,
                            stdoutBuilder,
                            stderrBuilder
                            );
                        this.capturedStdout = truncateLog(
                            stdoutBuilder.toString(),
                            MAX_LOG_CHARS
                            );
                        this.capturedStderr = truncateLog(
                            stderrBuilder.toString(),
                            MAX_LOG_CHARS
                            );
                        log.debug(
                            "Captured [{}] chars stdout, [{}] chars stderr for container [{}]",
                            this.capturedStdout.length(),
                            this.capturedStderr.length(),
                            containerId
                            );
                        log.debug(
                            "Container [{}] stdout: [{}]",
                            containerId,
                            this.capturedStdout.substring(0, Math.min(100, this.capturedStdout.length()))
                            );
                        log.debug(
                            "Container [{}] stderr: [{}]",
                            containerId,
                            this.capturedStderr.substring(0, Math.min(100, this.capturedStderr.length()))
                            );

                        if (this.exitCode != null && this.exitCode == 0)
                            {
                            this.setNextPhase(
                                IvoaLifecyclePhase.RELEASING
                                );
                            }
                        else {
                            log.warn(
                                "Container [{}] exited with non-zero code [{}]",
                                containerId,
                                this.exitCode
                                );
                            // TODO add messages
                            this.setNextPhase(
                                IvoaLifecyclePhase.FAILED
                                );
                            }
                        }
                    }
                catch (Exception ouch)
                    {
                    log.error(
                        "Failed to inspect container [{}] for compute resource [{}][{}], execption [{}][{}]",
                        containerId,
                        this.getComponentUuid(),
                        this.getComponentClassName(),
                        ouch.getClass().getSimpleName(),
                        ouch.getMessage()
                        );
                    // TODO add messages
                    this.setNextPhase(
                        IvoaLifecyclePhase.FAILED
                        );
                    }
                }

            @Override
            public void postProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Post-processing monitor action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                if (component instanceof DockerSimpleComputeResourceEntity)
                    {
                    postProcess(
                        (DockerSimpleComputeResourceEntity) component
                        );
                    }
                else {
                    log.error(  
                        "Unexpected type [{}] for docker container [{}][{}]",
                        component.getClass().getSimpleName(),
                        this.getComponentUuid(),
                        this.getComponentClassName()
                        );
                    // TODO add message details
                    component.addError(
                        "uri:internal-error",
                        "Unexpected component type, see logs for details"
                        );
                    }
                }

            public void postProcess(final DockerSimpleComputeResourceEntity component)
                {
                log.debug(
                    "Post-processing monitor action for compute resource [{}][{}] next phase [{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName(),
                    this.getNextPhase(),
                    this.exitCode
                    );
                component.dockerContainerExitCode = this.exitCode;
                component.containerStdout = this.capturedStdout;
                component.containerStderr = this.capturedStderr;
                //
                // The container logs have been captured, make the
                // session connectors available.
                component.markSessionConnectorsAvailable();
                component.setPhase( 
                    this.getNextPhase()
                    );
                }
            };
        }

    @Override
    public ProcessingAction makeReleaseAction(Platform platform)
        {
        log.debug(
            "makeReleaseAction for compute resource [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        final String containerId = this.dockerContainerId;

        final DockerClientFactory clientFactory;
        if (platform instanceof DockerPlatform)
            {
            clientFactory = ((DockerPlatform) platform).getDockerClientFactory();
            }
        else {
            clientFactory = null;
            log.error(
                "Unexpected platform type [{}] for compute resource [{}][{}]",
                platform.getClass().getSimpleName(),
                this.getUuid(),
                this.getClass().getSimpleName()
                );
            // TODO add messages
            return ProcessingAction.NO_ACTION;
            }

        return new ComponentProcessingActionBase(this)
            {
            @Override
            public void preProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Pre-processing release action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName(),
                    containerId
                    );
                component.setPhase(
                    IvoaLifecyclePhase.RELEASING
                    );
                }

            @Override
            public void process()
                {
                log.debug(
                    "Processing release action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                if (containerId == null || containerId.isEmpty())
                    {
                    log.error(
                        "No Docker container ID to remove for resource [{}][{}]",
                        this.getComponentUuid(),
                        this.getComponentClassName()
                        );
                    // TODO Add messages
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
                            "Unable to create Docker client to release [{}][{}]",
                            this.getComponentUuid(),
                            this.getComponentClassName()
                            );
                        // TODO Add messages
                        this.setNextPhase(
                            IvoaLifecyclePhase.FAILED
                            );
                        return;
                        }
                    log.debug(
                        "Removing container [{}] for compute resource [{}][{}]",
                        containerId,
                        this.getComponentUuid(),
                        this.getComponentClassName()
                        );
                    dockerClient.removeContainerCmd(containerId)
                        .withForce(true)
                        .exec();
                    log.debug(
                        "Removed container [{}] for compute resource [{}][{}]",
                        containerId,
                        this.getComponentUuid(),
                        this.getComponentClassName()
                        );
                    this.setNextPhase(
                        IvoaLifecyclePhase.COMPLETED
                        );
                    }
                catch (Exception ouch)
                    {
                    log.warn(
                        "Failed to remove container [{}] for compute resource [{}][{}], exception []{}[{}]",
                        containerId,
                        this.getComponentUuid(),
                        this.getComponentClassName(),
                        ouch.getClass().getSimpleName(),
                        ouch.getMessage()
                        );
                    // TODO Add messages
                    this.setNextPhase(
                        IvoaLifecyclePhase.FAILED
                        );
                    }
                }

            @Override
            public void postProcess(final LifecycleComponent component)
                {
                log.debug(
                    "Post-processing release action for compute resource [{}][{}]",
                    this.getComponentUuid(),
                    this.getComponentClassName()
                    );
                component.setPhase(
                    this.getNextPhase()
                    );
                }
            };
        }
    }
