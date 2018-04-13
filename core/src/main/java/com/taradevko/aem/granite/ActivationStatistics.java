/*
   Copyright 2018 Oleksandr Tarasenko

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package com.taradevko.aem.granite;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;

@Model(adaptables = SlingHttpServletRequest.class)
public class ActivationStatistics {

    @Self
    private SlingHttpServletRequest request;

    private final IsPage isPage = new IsPage();
    private final NotActivated notActivated = new NotActivated();
    private final IsMarket isMarket = new IsMarket();
    private Map<String, Map<String, Integer>> activationStatistics;

    @PostConstruct
    public void init() {
        //skipped permissions check
        ResourceResolver resourceResolver = request.getResourceResolver();

        String projectPath = request.getRequestPathInfo().getSuffix();

        Resource siteRootResource = resourceResolver.getResource(getContentPath(projectPath));
        activationStatistics = getActivationStatistics(siteRootResource);
    }

    public Map<String, Map<String, Integer>> getActivationStatistics() {
        return activationStatistics;
    }

    private Map<String, Map<String, Integer>> getActivationStatistics(final Resource siteRootResource) {
        final Map<String, Map<String, Integer>> results = new TreeMap<>();
        for (Resource market : siteRootResource.getChildren()) {
            if (isMarket.and(isPage).test(market)) {
                Map<String, Integer> marketStats = processMarket(market);
                results.put(market.getName(), marketStats);
            }
        }

        return results;
    }

    private Map<String, Integer> processMarket(final Resource market) {
        final Map<String, Integer> results = new TreeMap<>();
        for (Resource locale : market.getChildren()) {
            if (isPage.test(locale)) {
                Integer localeStats = processPageTree(locale);
                results.put(locale.getName(), localeStats);
            }
        }

        return results;
    }

    private Integer processPageTree(final Resource page) {
        Integer stats = 0;
        for (Resource child : page.getChildren()) {
            if (isPage.test(child)) {
                stats += processPageTree(child);
            }
        }

        if (notActivated.test(page)) {
            stats += 1;
        }

        return stats;
    }

    //ideally this should come from project settings.
    private String getContentPath(final String projectPath) {
        return projectPath.replace("/projects", "");
    }

    class IsPage implements Predicate<Resource> {

        @Override
        public boolean test(Resource resource) {
            return "cq:Page".equals(resource.adaptTo(ValueMap.class).get("jcr:primaryType"));
        }
    }

    class NotActivated implements Predicate<Resource> {

        @Override
        public boolean test(Resource resource) {
            return !"Activate".equals(resource.getChild("jcr:content")
                    .adaptTo(ValueMap.class).get("cq:lastReplicationAction"));
        }
    }

    class IsMarket implements Predicate<Resource> {

        @Override
        public boolean test(Resource resource) {
            return resource.getName().length() == 2;
        }
    }
}
