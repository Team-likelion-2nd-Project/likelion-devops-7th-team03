package com.example.management.links.controller;

import com.example.management.auth.argument.CurrentUser;
import com.example.management.common.api.GlobalExceptionHandler;
import com.example.management.links.exception.LinkExceptionHandler;
import com.example.management.links.service.LinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LinkControllerValidationTest {

    @Mock
    private LinkService linkService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new LinkController(linkService))
                .setCustomArgumentResolvers(currentUserResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new LinkExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createRejectsInvalidUrlBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"javascript:alert(1)","expiresAt":"2030-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(linkService);
    }

    @Test
    void createRejectsNonUtcExpirationBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.com","expiresAt":"2030-01-01T09:00:00+09:00"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(linkService);
    }

    @Test
    void updateRejectsRequestWithoutAValueBeforeCallingService() throws Exception {
        mockMvc.perform(patch("/api/links/link-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(linkService);
    }

    @Test
    void updateRejectsTooLongTitleBeforeCallingService() throws Exception {
        String body = "{\"title\":\"" + "a".repeat(101) + "\"}";

        mockMvc.perform(patch("/api/links/link-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(linkService);
    }

    @Test
    void getListRejectsInvalidPageBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/links").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(linkService);
    }

    private HandlerMethodArgumentResolver currentUserResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class)
                        && Long.class.equals(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                          org.springframework.web.context.request.NativeWebRequest webRequest,
                                          org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return 1L;
            }
        };
    }
}
