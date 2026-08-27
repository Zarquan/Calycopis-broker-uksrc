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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "timestamp": "2026-08-27T09:00:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.webapp;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.compute.simple.docker.DockerSimpleComputeResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;

/**
 * HTTP endpoints for the Docker container stdout and stderr session connectors.
 *
 * Provides HTTP GET access to read the contents of the containerStdout and
 * containerStderr properties of a session that executes a
 * DockerSimpleComputeResourceEntity.
 *
 * GET /sessions/{uuid}/docker/stdout-get
 * GET /sessions/{uuid}/docker/stderr-get
 *
 */
@Slf4j
@RestController
@RequestMapping("/sessions")
public class DockerSessionConnectorController
    {

    private final Platform platform;

    @Autowired
    public DockerSessionConnectorController(final Platform platform)
        {
        this.platform = platform;
        this.platform.initialize();
        }

    /**
     * GET /sessions/{uuid}/docker/stdout-get
     * Returns the captured container stdout for the session.
     *
     */
    @GetMapping(
        path = "/{uuid}/docker/stdout-get",
        produces = MediaType.TEXT_PLAIN_VALUE
        )
    public ResponseEntity<String> getContainerStdout(
        @PathVariable("uuid") final UUID uuid
        ){
        DockerSimpleComputeResourceEntity dockerCompute = this.selectDockerCompute(
            uuid
            );
        if (dockerCompute == null)
            {
            return new ResponseEntity<String>(
                HttpStatus.NOT_FOUND
                );
            }
        String stdout = dockerCompute.getContainerStdout();
        if (stdout == null)
            {
            stdout = "";
            }
        return new ResponseEntity<String>(
            stdout,
            HttpStatus.OK
            );
        }

    /**
     * GET /sessions/{uuid}/docker/stderr-get
     * Returns the captured container stderr for the session.
     *
     */
    @GetMapping(
        path = "/{uuid}/docker/stderr-get",
        produces = MediaType.TEXT_PLAIN_VALUE
        )
    public ResponseEntity<String> getContainerStderr(
        @PathVariable("uuid") final UUID uuid
        ){
        DockerSimpleComputeResourceEntity dockerCompute = this.selectDockerCompute(
            uuid
            );
        if (dockerCompute == null)
            {
            return new ResponseEntity<String>(
                HttpStatus.NOT_FOUND
                );
            }
        String stderr = dockerCompute.getContainerStderr();
        if (stderr == null)
            {
            stderr = "";
            }
        return new ResponseEntity<String>(
            stderr,
            HttpStatus.OK
            );
        }

    /**
     * Look up a session and return its compute resource when it is a
     * DockerSimpleComputeResourceEntity.
     * Returns null when the session cannot be found or when the compute
     * resource is not a Docker compute resource.
     *
     */
    protected DockerSimpleComputeResourceEntity selectDockerCompute(final UUID uuid)
        {
        final Optional<SimpleExecutionSessionEntity> found = this.platform.getExecutionSessionEntityFactory().select(
            uuid
            );
        if (found.isEmpty())
            {
            log.warn(
                "Session not found [{}] for docker connector",
                uuid
                );
            return null;
            }
        if (found.get().getComputeResource() instanceof DockerSimpleComputeResourceEntity dockerCompute)
            {
            return dockerCompute;
            }
        if (found.get().getComputeResource() == null)
            {
            log.warn(
                "Session [{}] has no compute resource",
                uuid
                );
            return null;
            }
        log.warn(
            "Session [{}] compute resource is not a Docker compute resource [{}]",
            uuid,
            found.get().getComputeResource().getClass().getSimpleName()
            );
        return null;
        }
    }
