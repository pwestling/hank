/**
 *  Copyright 2011 Rapleaf
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.rapleaf.hank.coordinator;

import com.rapleaf.hank.partition_server.RuntimeStatisticsAggregator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class RingGroups {
  private RingGroups() {}

  public static boolean isUpdating(RingGroup ringGroup) throws IOException {
    return ringGroup.getUpdatingToVersion() != null;
  }

  public static boolean isUpToDate(RingGroup ringGroup, DomainGroupVersion domainGroupVersion) throws IOException {
    for (Ring ring : ringGroup.getRings()) {
      if (!Rings.isUpToDate(ring, domainGroupVersion)) {
        return false;
      }
    }
    return true;
  }

  public static UpdateProgress computeUpdateProgress(RingGroup ringGroup,
                                                     DomainGroupVersion domainGroupVersion) throws IOException {
    UpdateProgress result = new UpdateProgress();
    for (Ring ring : ringGroup.getRings()) {
      result.aggregate(Rings.computeUpdateProgress(ring, domainGroupVersion));
    }
    return result;
  }

  public static int getNumHosts(RingGroup ringGroup) {
    int result = 0;
    for (Ring ring : ringGroup.getRings()) {
      result += ring.getHosts().size();
    }
    return result;
  }

  public static Set<Host> getHostsInState(RingGroup ringGroup, HostState state) throws IOException {
    Set<Host> result = new TreeSet<Host>();
    for (Ring ring : ringGroup.getRings()) {
      result.addAll(Rings.getHostsInState(ring, state));
    }
    return result;
  }

  public static DomainGroupVersion getMostRecentVersion(RingGroup ringGroup) throws IOException {
    // Use updating to version if there is one, current version otherwise
    if (ringGroup.getUpdatingToVersion() != null) {
      return ringGroup.getUpdatingToVersion();
    } else if (ringGroup.getCurrentVersion() != null) {
      return ringGroup.getCurrentVersion();
    }
    return null;
  }

  public static ServingStatusAggregator
  computeServingStatusAggregator(RingGroup ringGroup, DomainGroupVersion domainGroupVersion) throws IOException {
    ServingStatusAggregator servingStatusAggregator = new ServingStatusAggregator();
    for (Ring ring : ringGroup.getRings()) {
      servingStatusAggregator.aggregate(Rings.computeServingStatusAggregator(ring, domainGroupVersion));
    }
    return servingStatusAggregator;
  }

  public static Map<Ring, Map<Host, Map<Domain, RuntimeStatisticsAggregator>>>
  computeRuntimeStatistics(RingGroup ringGroup) throws IOException {
    Map<Ring, Map<Host, Map<Domain, RuntimeStatisticsAggregator>>> result =
        new HashMap<Ring, Map<Host, Map<Domain, RuntimeStatisticsAggregator>>>();
    for (Ring ring : ringGroup.getRings()) {
      result.put(ring, Rings.computeRuntimeStatistics(ring));
    }
    return result;
  }

  public static RuntimeStatisticsAggregator
  computeRuntimeStatisticsForRingGroup(
      Map<Ring, Map<Host, Map<Domain, RuntimeStatisticsAggregator>>> runtimeStatistics) {
    RuntimeStatisticsAggregator result = new RuntimeStatisticsAggregator();
    for (Map.Entry<Ring, Map<Host, Map<Domain, RuntimeStatisticsAggregator>>> entry1 : runtimeStatistics.entrySet()) {
      for (Map.Entry<Host, Map<Domain, RuntimeStatisticsAggregator>> entry2 : entry1.getValue().entrySet()) {
        for (Map.Entry<Domain, RuntimeStatisticsAggregator> entry3 : entry2.getValue().entrySet()) {
          result.add(entry3.getValue());
        }
      }
    }
    return result;
  }

  public static RuntimeStatisticsAggregator
  computeRuntimeStatisticsForRing(
      Map<Ring, Map<Host, Map<Domain, RuntimeStatisticsAggregator>>> runtimeStatistics, Ring ring) {
    if (runtimeStatistics.containsKey(ring)) {
      return Rings.computeRuntimeStatisticsForRing(runtimeStatistics.get(ring));
    } else {
      return new RuntimeStatisticsAggregator();
    }
  }

  public static RuntimeStatisticsAggregator
  computeRuntimeStatisticsForDomain(
      Map<Ring, Map<Host, Map<Domain, RuntimeStatisticsAggregator>>> runtimeStatistics, Domain domain) {
    RuntimeStatisticsAggregator result = new RuntimeStatisticsAggregator();
    for (Map.Entry<Ring, Map<Host, Map<Domain, RuntimeStatisticsAggregator>>> entry1 : runtimeStatistics.entrySet()) {
      for (Map.Entry<Host, Map<Domain, RuntimeStatisticsAggregator>> entry2 : entry1.getValue().entrySet()) {
        if (entry2.getValue().containsKey(domain)) {
          result.add(entry2.getValue().get(domain));
        }
      }
    }
    return result;
  }
}
