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
 *     "timestamp": "2026-10-20T11:57:26",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.webapp.error;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import net.ivoa.calycopis.openapi.spring.model.IvoaMessageItem;
import net.ivoa.calycopis.openapi.spring.model.IvoaProblemDetails;

/**
 * Builds the schema-generated problem details model used in error responses.
 *
 * Spring's ProblemDetail is used internally by the MVC exception handling.
 * This factory converts it to the model generated from the OpenAPI schema,
 * so the wire format is the published schema rather than a framework class.
 *
 */
@Component
public class ProblemDetailsFactory
    {

    private static final String ABOUT_BLANK = "about:blank";

    /**
     * Build a problem details object for one of our own problem types.
     *
     */
    public IvoaProblemDetails create(
        final ProblemType type,
        final String detail,
        final HttpServletRequest request
        ){
        return this.create(
            type,
            detail,
            request,
            null
            );
        }

    /**
     * Build a problem details object with an optional list of IVOA messages.
     *
     */
    public IvoaProblemDetails create(
        final ProblemType type,
        final String detail,
        final HttpServletRequest request,
        final List<IvoaMessageItem> messages
        ){
        IvoaProblemDetails details = new IvoaProblemDetails();
        details.setType(
            type.getTypeUri()
            );
        details.setTitle(
            type.getTitle()
            );
        details.setStatus(
            Integer.valueOf(
                type.getStatus().value()
                )
            );
        details.setDetail(
            detail
            );
        details.setInstance(
            this.instance(
                request
                )
            );
        if (messages != null && !messages.isEmpty())
            {
            details.setMessages(
                messages
                );
            }
        return details;
        }

    /**
     * Convert a Spring ProblemDetail into the schema-generated model.
     *
     * Spring's ProblemDetail is what the framework exception handlers
     * produce. We keep the framework class internal and publish the
     * schema model on the wire.
     *
     */
    public IvoaProblemDetails fromProblemDetail(
        final ProblemDetail problem,
        final HttpServletRequest request
        ){
        IvoaProblemDetails details = new IvoaProblemDetails();

        URI type = problem.getType();
        if (type == null)
            {
            type = URI.create(
                ABOUT_BLANK
                );
            }
        details.setType(
            type
            );

        String title = problem.getTitle();
        if (title == null)
            {
            HttpStatus status = HttpStatus.resolve(
                problem.getStatus()
                );
            if (status != null)
                {
                title = status.getReasonPhrase();
                }
            }
        details.setTitle(
            title
            );

        details.setStatus(
            Integer.valueOf(
                problem.getStatus()
                )
            );
        details.setDetail(
            problem.getDetail()
            );

        URI instance = problem.getInstance();
        if (instance == null)
            {
            instance = this.instance(
                request
                );
            }
        details.setInstance(
            instance
            );

        Map<String, Object> properties = problem.getProperties();
        if (properties != null)
            {
            for (Map.Entry<String, Object> entry : properties.entrySet())
                {
                if ("messages".equals(entry.getKey()) && entry.getValue() instanceof List<?> list)
                    {
                    List<IvoaMessageItem> messages = new ArrayList<>();
                    for (Object item : list)
                        {
                        if (item instanceof IvoaMessageItem message)
                            {
                            messages.add(message);
                            }
                        }
                    details.setMessages(
                        messages
                        );
                    }
                else {
                    details.putAdditionalProperty(
                        entry.getKey(),
                        entry.getValue()
                        );
                    }
                }
            }
        return details;
        }

    /**
     * Build an IVOA message item for a problem details extension member.
     *
     */
    public IvoaMessageItem message(
        final String kind,
        final String template,
        final String text,
        final Map<String, String> values
        ){
        IvoaMessageItem message = new IvoaMessageItem();
        message.setKind(
            kind
            );
        message.setTime(
            Instant.now()
            );
        message.setLevel(
            IvoaMessageItem.LevelEnum.ERROR
            );
        message.setTemplate(
            template
            );
        message.setValues(
            values
            );
        message.setMessage(
            text
            );
        return message;
        }

    /**
     * The request path as a URI reference, or null when it is unavailable.
     *
     */
    private URI instance(final HttpServletRequest request)
        {
        if (request == null)
            {
            return null;
            }
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty())
            {
            return null;
            }
        return URI.create(
            uri
            );
        }
    }
