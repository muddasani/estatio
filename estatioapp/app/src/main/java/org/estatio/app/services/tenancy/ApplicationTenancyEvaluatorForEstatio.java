/*
 *  Copyright 2012-2014 Eurocommercial Properties NV
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.estatio.app.services.tenancy;

import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.services.queryresultscache.QueryResultsCache;
import org.isisaddons.module.security.dom.tenancy.ApplicationTenancy;
import org.isisaddons.module.security.dom.tenancy.ApplicationTenancyEvaluator;
import org.isisaddons.module.security.dom.tenancy.WithApplicationTenancy;
import org.isisaddons.module.security.dom.user.ApplicationUser;
import org.isisaddons.module.security.facets.TenantedAuthorizationFacetDefault;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

@DomainService(nature = NatureOfService.DOMAIN, menuOrder = "99")
public class ApplicationTenancyEvaluatorForEstatio implements ApplicationTenancyEvaluator {

    @Inject
    QueryResultsCache queryResultsCache;

    public boolean handles(Class<?> cls) {
        return WithApplicationTenancy.class.isAssignableFrom(cls);
    }

    public String hides(Object domainObject, ApplicationUser applicationUser) {
        final String objectTenancyPath = applicationTenancyPathForCached(domainObject);
        if(objectTenancyPath == null) {
            return null;
        }
        final String userTenancyPath = userTenancyPathForCached(applicationUser);
        if (userTenancyPath == null) {
            return "User has no tenancy";
        }

        if (objectVisibleToUser(objectTenancyPath, userTenancyPath)) {
            return null;
        }
        return String.format("User with tenancy \'%s\' is not permitted to view object with tenancy \'%s\'", userTenancyPath, objectTenancyPath);
    }

    public String disables(Object domainObject, ApplicationUser applicationUser) {
        final String objectTenancyPath = applicationTenancyPathForCached(domainObject);
        if(objectTenancyPath == null) {
            return null;
        }
        final String userTenancyPath = userTenancyPathForCached(applicationUser);
        if (userTenancyPath == null) {
            return "User has no tenancy";
        }

        if (objectEnabledForUser(objectTenancyPath, userTenancyPath)) {
            return null;
        }
        return String.format("User with tenancy \'%s\' is not permitted to edit object with tenancy \'%s\'", userTenancyPath, objectTenancyPath);
    }

    boolean objectVisibleToUser(String objectTenancyPath, String userTenancyPath) {
        final List<String> objectTenancyPathList = split(objectTenancyPath);
        final List<String> userTenancyPathList = split(userTenancyPath);

        for (int i = 0; i < objectTenancyPathList.size(); i++) {
            final String objectTenancyPathPart = objectTenancyPathList.get(i);
            if(i >= userTenancyPathList.size()) {
                // run out of parts for the user tenancy, so the user tenancy is higher than object
                return true;
            }
            final String userTenancyPathPart = userTenancyPathList.get(i);
            if(!partsEqual(objectTenancyPathPart, userTenancyPathPart)) {
                return false;
            }
        }
        // run out of parts for the object tenancy, so the user tenancy is same or lower than the object
        return true;
    }

    boolean objectEnabledForUser(String objectTenancyPath, String userTenancyPath) {
        final List<String> objectTenancyPathList = split(objectTenancyPath);
        final List<String> userTenancyPathList = split(userTenancyPath);

        for (int i = 0; i < objectTenancyPathList.size(); i++) {
            final String objectTenancyPathPart = objectTenancyPathList.get(i);
            if(i >= userTenancyPathList.size()) {
                // run out of parts for the user tenancy, so the user tenancy is higher than object
                return true;
            }
            final String userTenancyPathPart = userTenancyPathList.get(i);
            if(!partsEqual(objectTenancyPathPart, userTenancyPathPart)) {
                return false;
            }
        }
        // run out of parts for the object tenancy, so the user tenancy is same or lower than the object
        final boolean sameSize = objectTenancyPathList.size() == userTenancyPathList.size();
        return sameSize;
    }

    protected boolean partsEqual(String objectTenancyPathPart, String userTenancyPathPart) {
        if (Objects.equals(objectTenancyPathPart, userTenancyPathPart)) {
            return true;
        }
        // eg allow "X-CAR" user to match with "CAR"
        if (!userTenancyPathPart.startsWith("X-")) {
            return false;
        }
        final String baseUserTenancyPathPart = userTenancyPathPart.substring(2);
        return Objects.equals(objectTenancyPathPart, baseUserTenancyPathPart);
    }

    private static List<String> split(String objectTenancyPath) {
        return FluentIterable.from(Splitter.on('/')
                            .split(objectTenancyPath))
                            .filter(s -> !com.google.common.base.Strings.isNullOrEmpty(s))
                            .toList();
    }

    //region > helpers: applicationTenancyPathForCached, applicationTenancyPathFor, userTenancyPathForCached, userTenancyPathFor
    private String applicationTenancyPathForCached(final Object domainObject) {
        return (String)queryResultsCache.execute(new Callable() {
            public String call() throws Exception {
                return applicationTenancyPathFor(domainObject);
            }
        }, TenantedAuthorizationFacetDefault.class, "applicationTenancyPathFor", domainObject);
    }

    private String applicationTenancyPathFor(Object domainObject) {
        WithApplicationTenancy tenantedObject = (WithApplicationTenancy)domainObject;
        ApplicationTenancy objectTenancy = tenantedObject.getApplicationTenancy();
        String objectTenancyPath = objectTenancy == null
                                        ? null
                                        : objectTenancy.getPath();
        return objectTenancyPath;
    }

    private String userTenancyPathForCached(final ApplicationUser applicationUser) {
        return (String)queryResultsCache.execute(new Callable() {
            public String call() throws Exception {
                return userTenancyPathFor(applicationUser);
            }
        }, TenantedAuthorizationFacetDefault.class, "userTenancyPathFor", applicationUser);
    }

    private String userTenancyPathFor(ApplicationUser applicationUser) {
        if(handles(applicationUser.getClass())) {
            return applicationTenancyPathFor(applicationUser);
        } else {
            ApplicationTenancy userTenancy = applicationUser.getTenancy();
            return userTenancy == null
                        ? null
                        : userTenancy.getPath();
        }
    }
    //endregion

}
