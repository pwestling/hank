/**
 *  Copyright 2011 LiveRamp
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
package com.liveramp.hank.ring_group_conductor;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.liveramp.hank.config.RingGroupConductorConfigurator;
import com.liveramp.hank.config.yaml.YamlRingGroupConductorConfigurator;
import com.liveramp.hank.coordinator.Coordinator;
import com.liveramp.hank.coordinator.RingGroup;
import com.liveramp.hank.partition_assigner.RendezVousPartitionAssigner;
import com.liveramp.hank.util.CommandLineChecker;

public class RingGroupConductor {

  private static final Logger LOG = Logger.getLogger(RingGroupConductor.class);

  private final RingGroupConductorConfigurator configurator;
  private final String ringGroupName;
  private final Coordinator coordinator;
  private final Object lock = new Object();

  private RingGroup ringGroup;

  private final RingGroupUpdateTransitionFunction transFunc;

  private boolean stopping = false;
  private boolean claimedRingGroupConductor;

  private Thread shutdownHook;

  public RingGroupConductor(RingGroupConductorConfigurator configurator) throws IOException {
    this(configurator, new RingGroupUpdateTransitionFunctionImpl(new RendezVousPartitionAssigner(), configurator.getMinRingFullyServingObservations()));
  }

  RingGroupConductor(RingGroupConductorConfigurator configurator, RingGroupUpdateTransitionFunction transFunc) throws IOException {
    this.configurator = configurator;
    this.transFunc = transFunc;
    ringGroupName = configurator.getRingGroupName();
    this.coordinator = configurator.createCoordinator();
  }

  public void run() throws IOException {
    // Add shutdown hook
    addShutdownHook();
    claimedRingGroupConductor = false;
    LOG.info("Ring Group Conductor for ring group " + ringGroupName + " starting.");
    try {
      ringGroup = coordinator.getRingGroup(ringGroupName);

      // attempt to claim the ring group conductor title
      if (ringGroup.claimRingGroupConductor(configurator.getInitialMode())) {
        claimedRingGroupConductor = true;

        // loop until we're taken down
        stopping = false;
        try {
          while (!stopping) {
            // take a snapshot of the current ring, since it might get changed
            // while we're processing the current update.
            RingGroup snapshotRingGroup;
            synchronized (lock) {
              snapshotRingGroup = ringGroup;
            }

            processUpdates(snapshotRingGroup);
            Thread.sleep(configurator.getSleepInterval());
          }
        } catch (InterruptedException e) {
          // daemon is going down.
        }
      } else {
        LOG.info("Attempted to claim Ring Group Conductor status, but there was already a lock in place!");
      }
    } catch (Throwable t) {
      LOG.fatal("unexpected exception!", t);
    } finally {
      releaseIfClaimed();
    }
    LOG.info("Ring Group Conductor for ring group " + ringGroupName + " shutting down.");
    // Remove shutdown hook. We don't need it anymore
    removeShutdownHook();
  }

  void processUpdates(RingGroup ringGroup) throws IOException {
    // Only process updates if ring group conductor is configured to be active/proactive
    if (ringGroup.getRingGroupConductorMode() == RingGroupConductorMode.ACTIVE ||
        ringGroup.getRingGroupConductorMode() == RingGroupConductorMode.PROACTIVE) {
      transFunc.manageTransitions(ringGroup);
    }
  }

  private void releaseIfClaimed() throws IOException {
    if (claimedRingGroupConductor) {
      ringGroup.releaseRingGroupConductor();
      claimedRingGroupConductor = false;
    }
  }

  // Give up ring group conductor status on VM exit
  private void addShutdownHook() {
    if (shutdownHook == null) {
      shutdownHook = new Thread() {
        @Override
        public void run() {
          try {
            releaseIfClaimed();
          } catch (IOException e) {
            // When VM is exiting and we fail to release ring group conductor status, swallow the exception
          }
        }
      };
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
  }

  private void removeShutdownHook() {
    if (shutdownHook != null) {
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
    }
  }

  /**
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    CommandLineChecker.check(args, new String[]{"configuration_file_path", "log4j_properties_file_path"}, RingGroupConductor.class);
    String configPath = args[0];
    String log4jprops = args[1];

    RingGroupConductorConfigurator configurator = new YamlRingGroupConductorConfigurator(configPath);
    PropertyConfigurator.configure(log4jprops);
    new RingGroupConductor(configurator).run();
  }

  public void stop() {
    stopping = true;
  }
}
