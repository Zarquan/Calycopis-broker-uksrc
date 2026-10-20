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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.ivoa.calycopis.broker.engine.entities.session.simple.InvalidExecutionSessionUpdateException;

/**
 * Tests for the RFC 9457 problem details advice.
 *
 */
public class ProblemDetailsAdviceTest
    {

    private MockMvc mockMvc;

    @BeforeEach
    public void setup()
        {
        ProblemDetailsAdvice advice = new ProblemDetailsAdvice(
            new ProblemDetailsFactory()
            );
        advice.setMessageSource(
            new StaticMessageSource()
            );
        this.mockMvc = MockMvcBuilders.standaloneSetup(
            new TestController()
            ).setControllerAdvice(
                advice
            ).build();
        }

    @Test
    public void invalidUpdateReturnsProblemDetails() throws Exception
        {
        this.mockMvc.perform(
            get("/test/invalid-update")
            ).andExpect(
                status().isBadRequest()
            ).andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            ).andExpect(
                jsonPath("$.type").value("urn:ivoa:calycopis:error:invalid-update")
            ).andExpect(
                jsonPath("$.title").value("Invalid execution session update")
            ).andExpect(
                jsonPath("$.status").value(400)
            ).andExpect(
                jsonPath("$.instance").value("/test/invalid-update")
            ).andExpect(
                jsonPath("$.messages[0].kind").value("urn:ivoa:calycopis:message:invalid-update")
            ).andExpect(
                jsonPath("$.messages[0].level").value("ERROR")
            );
        }

    @Test
    public void sessionNotFoundReturnsProblemDetails() throws Exception
        {
        this.mockMvc.perform(
            get("/test/session-not-found")
            ).andExpect(
                status().isNotFound()
            ).andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            ).andExpect(
                jsonPath("$.type").value("urn:ivoa:calycopis:error:session-not-found")
            ).andExpect(
                jsonPath("$.status").value(404)
            );
        }

    @Test
    public void accessDeniedReturnsProblemDetails() throws Exception
        {
        this.mockMvc.perform(
            get("/test/access-denied")
            ).andExpect(
                status().isForbidden()
            ).andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            ).andExpect(
                jsonPath("$.type").value("urn:ivoa:calycopis:error:access-denied")
            ).andExpect(
                jsonPath("$.status").value(403)
            );
        }

    @Test
    public void unexpectedExceptionIsMasked() throws Exception
        {
        this.mockMvc.perform(
            get("/test/boom")
            ).andExpect(
                status().isInternalServerError()
            ).andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            ).andExpect(
                jsonPath("$.type").value("urn:ivoa:calycopis:error:internal-error")
            ).andExpect(
                jsonPath("$.status").value(500)
            ).andExpect(
                content().string(
                    not(
                        containsString("secret-detail")
                        )
                    )
            ).andExpect(
                content().string(
                    not(
                        containsString("IllegalStateException")
                        )
                    )
            );
        }

    @Test
    public void frameworkExceptionUsesProblemDetails() throws Exception
        {
        this.mockMvc.perform(
            get("/test/missing-parameter")
            ).andExpect(
                status().isBadRequest()
            ).andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            ).andExpect(
                jsonPath("$.status").value(400)
            ).andExpect(
                jsonPath("$.instance").value("/test/missing-parameter")
            );
        }

    /**
     * Endpoints that throw the exceptions we want to map.
     *
     */
    @RestController
    static class TestController
        {

        @GetMapping("/test/invalid-update")
        public String invalidUpdate()
            {
            throw InvalidExecutionSessionUpdateException.unknownPath(
                "/bogus"
                );
            }

        @GetMapping("/test/session-not-found")
        public String sessionNotFound()
            {
            throw new BrokerProblemException(
                ProblemType.SESSION_NOT_FOUND,
                "No execution session with UUID [test]."
                );
            }

        @GetMapping("/test/access-denied")
        public String accessDenied()
            {
            throw new BrokerProblemException(
                ProblemType.ACCESS_DENIED,
                "The authenticated identity is not allowed to update this execution session."
                );
            }

        @GetMapping("/test/boom")
        public String boom()
            {
            throw new IllegalStateException(
                "secret-detail"
                );
            }

        @GetMapping("/test/missing-parameter")
        public String missingParameter(
            @RequestParam("value") final String value
            ){
            return value;
            }
        }
    }
