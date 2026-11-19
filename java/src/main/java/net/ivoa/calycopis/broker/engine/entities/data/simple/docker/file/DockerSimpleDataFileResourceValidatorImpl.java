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
 *     "timestamp": "2026-11-19T17:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 40,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.data.simple.docker.file;

import java.net.URI;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.data.AbstractDataResourceValidator;
import net.ivoa.calycopis.broker.engine.entities.data.AbstractDataStorageLinker;
import net.ivoa.calycopis.broker.engine.entities.data.simple.SimpleDataResourceValidatorImpl;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleDataResource;

/**
 * 
 */
@Slf4j
public class DockerSimpleDataFileResourceValidatorImpl 
extends SimpleDataResourceValidatorImpl
implements DockerSimpleDataFileResourceValidator
    {
    
    /**
     * Public constructor used by our Platform.
     * 
     */
    public DockerSimpleDataFileResourceValidatorImpl(
        final DockerSimpleDataFileResourceEntityFactory entityFactory,
        final AbstractDataStorageLinker storageLinker)
        {
        super(
            entityFactory,
            storageLinker
            );
        }

    @Override
    public AbstractDataResourceValidator.Result validate(
        final IvoaSimpleDataResource requested,
        final OfferSetRequestParserContext context
        ){
        log.debug("validate(IvoaSimpleDataResource, Context)");
        log.debug("Resource [{}][{}]", requested.getMeta(), requested.getClass().getName());
    
        var location = requested.getLocation();
        if ((location != null) && (location.startsWith("file://")))
            {
            return super.validate(
                requested,
                context
                );
            }
        else {
            return new AbstractDataResourceValidator.ResultBean(
                ResultEnum.CONTINUE
                );
            }
        }
    
    /**
     * Check whether a location is a usable local file URI.
     *
     * The broker runs in a container and the Podman service resolves bind
     * mounts against the host filesystem, so the location must name an
     * absolute host path. A missing path, an authority-only URI such as
     * file://None, and the host root are all rejected.
     *
     */
    public static boolean isUsableFileLocation(
        final String location
        ){
        if (location == null)
            {
            return false;
            }
        try {
            URI uri = URI.create(location);
            if (! "file".equalsIgnoreCase(uri.getScheme()))
                {
                return false;
                }
            String path = uri.getPath();
            if ((path == null) || path.isBlank())
                {
                return false;
                }
            if (! path.startsWith("/"))
                {
                return false;
                }
            if ("/".equals(path))
                {
                return false;
                }
            return true;
            }
        catch (IllegalArgumentException ouch)
            {
            log.debug(
                "Unable to parse file location [{}][{}]",
                location,
                ouch.getMessage()
                );
            return false;
            }
        }

    @Override
    protected boolean validateLocation(
        final String location,
        final OfferSetRequestParserContext context
        ){
        log.debug("validate(String, Context)");
        log.debug("Location [{}]", location);
        if (isUsableFileLocation(location))
            {
            return true;
            }
        context.addWarning(
            "urn:invalid-value",
            "File data location [${location}] must be a file URI with an absolute path",
            Map.of(
                "location",
                String.valueOf(location)
                )
            );
        return false;
        }

    @Override
    protected Long getPrepareDuration(final IvoaSimpleDataResource resource)
        {
        return 0L;
        }
    
    @Override
    protected Long getReleaseDuration(final IvoaSimpleDataResource resource)
        {
        return 0L;       
        }
    }
