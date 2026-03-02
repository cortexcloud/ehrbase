/*
 * Copyright (c) 2024 vitasystems GmbH.
 *
 * This file is part of project EHRbase
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehrbase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ehrbase.cache.CacheProvider;
import org.ehrbase.cache.CacheProviderImp;
import org.ehrbase.repository.PartyProxyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UserServiceTransactionTest {

    private AnnotationConfigApplicationContext context;
    private UserService userService;
    private TestPartyProxyRepository partyProxyRepository;
    private MutableAuthenticationFacade authenticationFacade;
    private CacheProvider cacheProvider;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        userService = context.getBean(UserService.class);
        partyProxyRepository = context.getBean(TestPartyProxyRepository.class);
        authenticationFacade = context.getBean(MutableAuthenticationFacade.class);
        cacheProvider = context.getBean(CacheProvider.class);

        CacheProvider.USER_ID_CACHE.clear(cacheProvider);
        partyProxyRepository.reset();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void getCurrentUserIdStartsTransactionForCacheMissesAcrossUsers() {
        authenticationFacade.setAuthentication("first-user");
        UUID firstUserId = userService.getCurrentUserId();

        authenticationFacade.setAuthentication("second-user");
        UUID secondUserId = userService.getCurrentUserId();

        assertThat(AopUtils.isAopProxy(userService)).isTrue();
        assertThat(firstUserId).isNotEqualTo(secondUserId);
        assertThat(partyProxyRepository.findCalls()).isEqualTo(2);
        assertThat(partyProxyRepository.createCalls()).isEqualTo(2);
        assertThat(partyProxyRepository.findInternalUserId("first-user")).contains(firstUserId);
        assertThat(partyProxyRepository.findInternalUserId("second-user")).contains(secondUserId);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

                @Override
                protected Object doGetTransaction() {
                    return active.get();
                }

                @Override
                protected boolean isExistingTransaction(Object transaction) {
                    return Boolean.TRUE.equals(transaction);
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                    active.set(true);
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {}

                @Override
                protected void doRollback(DefaultTransactionStatus status) {}

                @Override
                protected void doCleanupAfterCompletion(Object transaction) {
                    active.remove();
                }
            };
        }

        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager cacheManager = new SimpleCacheManager();
            cacheManager.setCaches(List.of(new ConcurrentMapCache(CacheProvider.USER_ID_CACHE.name())));
            cacheManager.initializeCaches();
            return cacheManager;
        }

        @Bean
        CacheProvider cacheProvider(CacheManager cacheManager) {
            return new CacheProviderImp(cacheManager);
        }

        @Bean
        MutableAuthenticationFacade authenticationFacade() {
            return new MutableAuthenticationFacade();
        }

        @Bean
        TestPartyProxyRepository partyProxyRepository() {
            return new TestPartyProxyRepository();
        }

        @Bean
        UserService userService(
                IAuthenticationFacade authenticationFacade,
                CacheProvider cacheProvider,
                PartyProxyRepository partyProxyRepository) {
            return new UserServiceImp(authenticationFacade, cacheProvider, partyProxyRepository);
        }
    }

    static class MutableAuthenticationFacade implements IAuthenticationFacade {
        private Authentication authentication = new UsernamePasswordAuthenticationToken("default-user", "test");

        @Override
        public Authentication getAuthentication() {
            return authentication;
        }

        void setAuthentication(String username) {
            this.authentication = new UsernamePasswordAuthenticationToken(username, "test");
        }
    }

    static class TestPartyProxyRepository extends PartyProxyRepository {
        private final Map<String, UUID> users = new LinkedHashMap<>();
        private int findCalls;
        private int createCalls;

        TestPartyProxyRepository() {
            super(null);
        }

        @Override
        public Optional<UUID> findInternalUserId(String username) {
            findCalls++;
            return Optional.ofNullable(users.get(username));
        }

        @Override
        public UUID createInternalUser(String username) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            createCalls++;
            UUID userId = UUID.randomUUID();
            users.put(username, userId);
            return userId;
        }

        void reset() {
            users.clear();
            findCalls = 0;
            createCalls = 0;
        }

        int findCalls() {
            return findCalls;
        }

        int createCalls() {
            return createCalls;
        }
    }
}
