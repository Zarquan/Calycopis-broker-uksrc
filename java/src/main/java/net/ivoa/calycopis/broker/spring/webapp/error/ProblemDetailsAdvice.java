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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.session.simple.InvalidExecutionSessionUpdateException;
import net.ivoa.calycopis.openapi.spring.model.IvoaMessageItem;
import net.ivoa.calycopis.openapi.spring.model.IvoaProblemDetails;

/**
 * Maps broker exceptions to RFC 9457 problem details responses.
 *
 * The response body is the schema-generated IvoaProblemDetails model.
 * Spring's ProblemDetail is only used internally, and is converted at
 * this boundary so the published OpenAPI schema is the wire contract.
 *
 * The content type is set explicitly on every response, so an error is
 * always application/problem+json even when the request asked for XML
 * or YAML.
 *
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProblemDetailsAdvice
extends ResponseEntityExceptionHandler
    {

    private static final int MAX_DETAIL_LENGTH = 200;

    private static final String INVALID_UPDATE_MESSAGE_KIND =
        "urn:ivoa:calycopis:message:invalid-update";

    private static final String INVALID_UPDATE_MESSAGE_TEMPLATE =
        "Invalid execution session update";

    private static final String INTERNAL_ERROR_DETAIL =
        "An unexpected error occurred while processing the request.";

    private final ProblemDetailsFactory factory;

    public ProblemDetailsAdvice(
        final ProblemDetailsFactory factory
        ){
        this.factory = factory;
        }

    /**
     * An execution session update that the engine refused.
     *
     */
    @ExceptionHandler(InvalidExecutionSessionUpdateException.class)
    public ResponseEntity<IvoaProblemDetails> handleInvalidUpdate(
        final InvalidExecutionSessionUpdateException exception,
        final HttpServletRequest request
        ){
        log.warn(
            "Invalid execution session update [{}]",
            exception.getMessage()
            );
        IvoaProblemDetails details = this.factory.create(
            ProblemType.INVALID_UPDATE,
            this.truncate(
                exception.getMessage()
                ),
            request,
            this.invalidUpdateMessages(
                exception
                )
            );
        return this.response(
            details
            );
        }

    /**
     * A web-layer problem that maps directly to one of our problem types.
     *
     */
    @ExceptionHandler(BrokerProblemException.class)
    public ResponseEntity<IvoaProblemDetails> handleBrokerProblem(
        final BrokerProblemException exception,
        final HttpServletRequest request
        ){
        ProblemType type = exception.getProblemType();
        String detail = exception.getMessage();
        if (detail == null)
            {
            detail = type.getTitle();
            }
        log.warn(
            "Problem response [{}] [{}]",
            type.getStatus().value(),
            type.getTypeUri()
            );
        IvoaProblemDetails details = this.factory.create(
            type,
            detail,
            request,
            exception.getMessages()
            );
        return this.response(
            details
            );
        }

    /**
     * Any exception that the more specific handlers do not catch.
     *
     * The caller gets a generic detail. The full exception is logged
     * server side and is never included in the response.
     *
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<IvoaProblemDetails> handleUnexpectedException(
        final Exception exception,
        final HttpServletRequest request
        ){
        log.error(
            "Unexpected exception while processing [{}]",
            request.getRequestURI(),
            exception
            );
        IvoaProblemDetails details = this.factory.create(
            ProblemType.INTERNAL_ERROR,
            INTERNAL_ERROR_DETAIL,
            request
            );
        return this.response(
            details
            );
        }

    /**
     * Force the problem media type for the framework exception handlers.
     *
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
        final Object body,
        final HttpHeaders headers,
        final HttpStatusCode statusCode,
        final WebRequest request
        ){
        if (body instanceof ProblemDetail problem)
            {
            HttpServletRequest servletRequest = null;
            if (request instanceof ServletWebRequest servletWebRequest)
                {
                servletRequest = servletWebRequest.getRequest();
                }
            IvoaProblemDetails details = this.factory.fromProblemDetail(
                problem,
                servletRequest
                );
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(
                headers
                );
            responseHeaders.setContentType(
                MediaType.APPLICATION_PROBLEM_JSON
                );
            return new ResponseEntity<>(
                details,
                responseHeaders,
                statusCode
                );
            }
        return super.createResponseEntity(
            body,
            headers,
            statusCode,
            request
            );
        }

    private ResponseEntity<IvoaProblemDetails> response(
        final IvoaProblemDetails details
        ){
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (details.getStatus() != null)
            {
            HttpStatus resolved = HttpStatus.resolve(
                details.getStatus()
                );
            if (resolved != null)
                {
                status = resolved;
                }
            }
        return ResponseEntity.status(
            status
            ).contentType(
                MediaType.APPLICATION_PROBLEM_JSON
            ).body(
                details
                );
        }

    private List<IvoaMessageItem> invalidUpdateMessages(
        final InvalidExecutionSessionUpdateException exception
        ){
        Map<String, String> values = new LinkedHashMap<>();
        if (exception.getPath() != null)
            {
            values.put(
                "path",
                this.truncate(
                    exception.getPath()
                    )
                );
            }
        if (exception.getValue() != null)
            {
            values.put(
                "value",
                this.truncate(
                    exception.getValue()
                    )
                );
            }
        return List.of(
            this.factory.message(
                INVALID_UPDATE_MESSAGE_KIND,
                INVALID_UPDATE_MESSAGE_TEMPLATE,
                this.truncate(
                    exception.getMessage()
                    ),
                values
                )
            );
        }

    private String truncate(
        final String value
        ){
        if (value == null)
            {
            return null;
            }
        if (value.length() <= MAX_DETAIL_LENGTH)
            {
            return value;
            }
        return value.substring(
            0,
            MAX_DETAIL_LENGTH
            ) + "...";
        }
    }
