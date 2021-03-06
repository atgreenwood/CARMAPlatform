/*
 * Copyright (C) 2018-2019 LEIDOS.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package gov.dot.fhwa.saxton.carma.route;

import cav_msgs.*;
import cav_msgs.Route;
import cav_msgs.RouteSegment;
import org.ros.message.MessageFactory;
import org.ros.message.Time;
import org.ros.node.NodeConfiguration;
import org.ros.rosjava_geometry.Transform;

/**
 * Fake route manager for use in unit testing
 */
public class MockRouteManager implements IRouteManager {
  private boolean routeStateRouteCompleteSent = false;
  private Transform earthToHostVehicle = null;

  public static SystemAlert buildSystemAlert(byte systemAlertType, String msg) {
    NodeConfiguration nodeConfiguration = NodeConfiguration.newPrivate();
    MessageFactory messageFactory = nodeConfiguration.getTopicMessageFactory();

    SystemAlert alert = messageFactory.newFromType(SystemAlert._TYPE);
    alert.setType(systemAlertType);
    alert.setDescription(msg);

    return alert;
  }

  public void publishCurrentRouteSegment(RouteSegment routeSegment) {

  }

  @Override public void publishActiveRoute(Route route) {

  }

  @Override public void publishRouteState(RouteState routeState) {

  }

  @Override public void publishRouteEvent(RouteEvent routeEvent) {
    if (routeEvent.getEvent() == RouteEvent.ROUTE_COMPLETED) {
      routeStateRouteCompleteSent = true;
    }
  }

  @Override
  public Transform getTransform(String parentFrame, String childFrame, Time stamp) {
    return earthToHostVehicle;
  }
  
  @Override public Time getTime() {
    return Time.fromMillis(System.currentTimeMillis());
  }

  @Override public void shutdown() {

  }

  /**
   * Helper function to set the current earth -> host_vehicle transform
   */
  public void setEarthToHostTransform(Transform earthToHostVehicle) {
    this.earthToHostVehicle = earthToHostVehicle;
  }

  /**
   * Returns true if a attempt was made to send a route completion message
   * @return
   */
  public boolean routeCompletionDeclared() {
    return routeStateRouteCompleteSent;
  }

  /**
   * Resets the indicator of a route completion message being sent
   */
  public void resetRouteCompletionStatus() {
    routeStateRouteCompleteSent = false;
  }
}
