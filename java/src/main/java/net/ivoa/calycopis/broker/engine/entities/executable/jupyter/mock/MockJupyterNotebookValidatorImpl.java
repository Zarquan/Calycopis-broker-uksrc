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
 *     "interval": "2026-11-18T22:55:00/2026-11-18T23:45:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 1,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.executable.jupyter.mock;

import java.util.List;
import java.util.Map;

import net.ivoa.calycopis.broker.engine.entities.executable.jupyter.JupyterNotebookEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.executable.jupyter.JupyterNotebookValidatorImpl;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.openapi.spring.model.IvoaJupyterNotebook;

/**
 * 
 */
public class MockJupyterNotebookValidatorImpl
extends JupyterNotebookValidatorImpl
implements MockJupyterNotebookValidator
    {

    /**
     * Public constructor used by our Platform.
     * 
     */
    public MockJupyterNotebookValidatorImpl(
        final JupyterNotebookEntityFactory entityFactory
        ){
        super(entityFactory);
        }

    /**
     * List of locations to exclude from validation.
     * 
     */
    public static final List<String> LOCATION_EXCLUDE_LIST = List.of(
        "http://example.com/excluded-one.ipynb",
        "http://example.com/excluded-two.ipynb"
        ); 

    @Override
    protected boolean validateLocation(String location, OfferSetRequestParserContext context)
        {
        if (LOCATION_EXCLUDE_LIST.contains(location))
            {
            context.addWarning(
                "urn:invalid-value",
                "JupyterNotebook - location is excluded [${value}]",
                Map.of(
                    "value",
                    location
                    )
                );
            return false;
            }
        else {
            return true;
            }
        }

    /**
     * Default prepare duration, 30 seconds.
     * 
     */
    public static final Long DEFAULT_PREPARE_ESTIMATE = 5L;

    /**
     * Get the prepare duration for a resource.
     * Returns DEFAULT_PREPARE_ESTIMATE if the request does not specify a value.
     * 
     */
    protected Long getPrepareDuration(final IvoaJupyterNotebook validated)
        {
        Long duration = getPrepareDuration(
            validated.getSchedule()
            );
        if (duration != null)
            {
            return duration ;
            }
        else {
            return DEFAULT_PREPARE_ESTIMATE ;
            }
        }

    /**
     * Default release duration, 0 seconds.
     * 
     */
    public static final Long DEFAULT_RELEASE_ESTIMATE = 0L;

    /**
     * Get the release duration for a resource.
     * 
     */
    protected Long getReleaseDuration(final IvoaJupyterNotebook validated)
        {
        return DEFAULT_RELEASE_ESTIMATE ;
        }
    }
