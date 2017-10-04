/*
 * TODO: Copyright (C) 2017 LEIDOS.
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
import cav_srvs.SetActiveRouteResponse;
import cav_srvs.StartActiveRouteResponse;
import gov.dot.fhwa.saxton.carma.geometry.geodesic.HaversineStrategy;
import gov.dot.fhwa.saxton.carma.geometry.geodesic.Location;
import org.apache.commons.logging.Log;
import org.ros.message.MessageFactory;
import org.ros.message.Time;
import org.ros.node.NodeConfiguration;
import sensor_msgs.NavSatFix;
import sensor_msgs.NavSatStatus;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;

/**
 * The RouteWorker is responsible for implementing all non pub-sub logic of the RouteManager node
 * The class operates as a state machine with states LOADING_ROUTES, ROUTE_SELECTION, READY_TO_FOLLOW and FOLLOWING_ROUTE
 * The state of the RouteWorker is used by a ros node to determine which topics will be available
 */
public class RouteWorker {
  protected final IRouteManager routeManager;
  protected final Log log;
  protected final NodeConfiguration nodeConfiguration = NodeConfiguration.newPrivate();
  protected final MessageFactory messageFactory = nodeConfiguration.getTopicMessageFactory();
  // State array to assign indexes to states
  protected final WorkerState[] states =
    { WorkerState.LOADING_ROUTES, WorkerState.ROUTE_SELECTION, WorkerState.WAITING_TO_START,
      WorkerState.FOLLOWING_ROUTE };
  // Transition table for state machine
  protected final int[][] transition = {
    /*STATES: LoadingRoutes(0), RouteSelection(1), WaitingToStart(2), FollowingRoute(3) */
    /*          EVENTS           */
    { 1, 1, 2, 3 }, /*FILES_LOADED    */
    { 0, 2, 2, 3 }, /*ROUTE_SELECTED  */
    { 0, 1, 2, 1 }, /*ROUTE_COMPLETED */
    { 0, 1, 2, 2 }, /*LEFT_ROUTE      */
    { 0, 1, 2, 2 }, /*SYSTEM_FAILURE  */
    { 0, 1, 2, 2 }, /*SYSTEM_NOT_READY*/
    { 0, 1, 3, 3 }, /*ROUTE_STARTED   */
    { 0, 1, 1, 1 }, /*ROUTE_ABORTED   */ };

  // Current state
  protected int currentStateIndex = 0;

  protected Route activeRoute;
  protected HashMap<String, Route> availableRoutes = new HashMap<>();
  protected RouteSegment currentSegment;
  protected int currentSegmentIndex = 0;
  protected Location hostVehicleLocation = new Location();

  protected int currentWaypointIndex = 0;
  protected double downtrackDistance = 0;
  protected double crossTrackDistance = 0;
  protected boolean systemOkay = false;
  protected double MAX_CROSSTRACK_DISTANCE_M = 1000.0;
    // TODO put in route files as may change based on road type
  protected double MAX_START_DISTANCE_M = 1000.0; // Can only join route if within this many meters of waypoint
  protected int routeStateSeq = 0;

  /**
   * Constructor initializes a route worker object with the provided logging tool
   *
   * @param log The logger to be used
   */
  public RouteWorker(IRouteManager manager, Log log) {
    this.log = log;
    this.routeManager = manager;
  }

  /**
   * Constructor initializes the state and transitions and starts the timeout timer for the starting state
   *
   * @param manager negotiation manager which is used to publish data
   * @param log     the logger
   */
  public RouteWorker(IRouteManager manager, Log log, String database_path) {
    this.routeManager = manager;
    this.log = log;
    // Load route files from database
    File folder = new File(database_path);
    File[] listOfFiles = folder.listFiles();

    for (int i = 0; i < listOfFiles.length; i++) {
      if (listOfFiles[i].isFile()) {
        FileStrategy loadStrategy = new FileStrategy(listOfFiles[i].getPath(), log);
        loadAdditionalRoute(loadStrategy);
      }
    }
    // At this point the current state should be WaitingForRouteSelection
  }

  /**
   * Function which coordinates state transitions and the timeout timers
   *
   * @param event the event which will be used to determine the next state
   */
  protected void next(WorkerEvent event) {
    currentStateIndex = transition[event.ordinal()][currentStateIndex];
    log.info("Route State = " + currentSegmentIndex);
    // Publish the new route state
    routeManager.publishRouteState(getRouteStateTopicMsg(routeStateSeq, routeManager.getTime(), false));
  }

  /**
   * Handles the given event and updates the current state
   *
   * @param event the event to be handled
   */
  protected void handleEvent(WorkerEvent event) {
    SystemAlert alertMsg;
    switch (event) {
      case FILES_LOADED:
        log.info("Route has loaded new routes");
        break;
      case ROUTE_SELECTED:
        log.info("Route has been selected");
        break;
      case ROUTE_COMPLETED:
        alertMsg = buildSystemAlertMsg(SystemAlert.SHUTDOWN,
          "Route: The end of the active route has been reached");
        // Notify system of route completion
        routeManager.publishSystemAlert(alertMsg);
        routeManager.publishRouteState(getRouteStateTopicMsg(routeStateSeq, routeManager.getTime(), true));
        log.info("Route has been completed");
        routeManager.shutdown(); // Shutdown this node
        break;
      case LEFT_ROUTE:
        alertMsg = buildSystemAlertMsg(SystemAlert.WARNING,
          "Route: The host vehicle has left the route vicinity");
        routeManager.publishSystemAlert(alertMsg);
        log.info("The vehicle has left the active route");
        break;
      case SYSTEM_FAILURE:
        log.info(
          "Route: Received a system failure message and is shutting down");
        routeManager.shutdown();
        break;
      case SYSTEM_NOT_READY:
        log.info(
          "Route has received a system not ready message and is switching to pausing the active route");
        break;
      case ROUTE_ABORTED:
        alertMsg = buildSystemAlertMsg(SystemAlert.WARNING, "Route: The active route was aborted");
        routeManager.publishSystemAlert(alertMsg);
        log.info("Route has been aborted");
        break;
      case ROUTE_STARTED:
        log.info("Route has been started");
        break;
      default:
        log.warn("Route was provided with an unsupported event");
    }
    // Update current state
    next(event);
  }

  /**
   * Gets the current state of this FSM
   *
   * @return the current state
   */
  public WorkerState getCurrentState() {
    return states[currentStateIndex];
  }

  /**
   * Loads a route into memory using the provided route loading strategy
   *
   * @param loadStrategy
   */
  protected void loadAdditionalRoute(IRouteLoadStrategy loadStrategy) {
    Route route = loadStrategy.load();
    route
      .setRouteID(route.getRouteName()); //TODO come up with better method of defining the route id
    availableRoutes.put(route.getRouteID(), route);
    handleEvent(WorkerEvent.FILES_LOADED);
  }

  /**
   * Returns true when the host vehicle has passed the end of the current route segment
   * @return indication of vehicle in next segment
   */
  protected boolean atNextSegment() {
    return currentSegment.downTrackDistance(hostVehicleLocation) > currentSegment.length();
  }

  /**
   * Returns true if crossTrackDistance is so large that the vehicle can no longer be considered on the route
   *
   * @return vehicle on route status
   */
  protected boolean leftRouteVicinity() {
    return Math.abs(crossTrackDistance) > MAX_CROSSTRACK_DISTANCE_M;
  }

  /**
   * Gets the collection of available routes
   * @return a collection of routes
   */
  protected Collection<Route> getAvailableRoutes() {
    return availableRoutes.values();
  }

  /**
   * Helper function which builds a system alert message
   *
   * @param type        The type of the alert
   * @param description Description of the message
   * @return System Alert message
   */
  protected SystemAlert buildSystemAlertMsg(byte type, String description) {
    SystemAlert alertMsg = messageFactory.newFromType(SystemAlert._TYPE);
    alertMsg.setType(type);
    alertMsg.setDescription(description);
    return alertMsg;
  }

  /**
   * Function to be used in a callback for the setActiveRoute service
   *
   * @param routeID The route
   * @return the service response
   */
  public byte setActiveRoute(String routeID) {
    Route route = availableRoutes.get(routeID);
    // Check if the specified route exists.
    if (route == null) {
      return SetActiveRouteResponse.NO_ROUTE;
    } else {
      activeRoute = route;

      routeManager.publishActiveRoute(getActiveRouteTopicMsg());
      handleEvent(WorkerEvent.ROUTE_SELECTED);
      return SetActiveRouteResponse.NO_ERROR;
    }
  }

  /**
   * Starts the currently active route and returns a byte (unit8) indicating the success of the start
   * Requests to start the route are validated for feasibility
   *
   * @return The error code (See cav_srvs.StartActiveRoute for possible options)
   */
  public byte startActiveRoute() {
    if (activeRoute == null) {
      return StartActiveRouteResponse.NO_ACTIVE_ROUTE;
    }
    if (getCurrentState() == WorkerState.FOLLOWING_ROUTE) {
      return StartActiveRouteResponse.ALREADY_FOLLOWING_ROUTE;
    }
    int startingIndex = getValidStartingWPIndex();
    log.debug("Route starting index = " + startingIndex);
    if (startingIndex == -1) {
      return StartActiveRouteResponse.INVALID_STARTING_LOCATION;
    } else {
      startRouteAtIndex(startingIndex);
      return StartActiveRouteResponse.NO_ERROR;
    }
  }

  /**
   * Gets the index of the first waypoint which can serve as a valid starting point for a route
   *
   * @return the valid waypoint index (-1 if not waypoint is valid)
   */
  protected int getValidStartingWPIndex() {
    if (activeRoute == null) {
      return -1;
    }
    int startingIndex = -1;
    int count = 0;
    for (RouteWaypoint wp : activeRoute.getWaypoints()) {
      double dist = hostVehicleLocation.distanceFrom(wp.getLocation(), new HaversineStrategy());
      if (MAX_START_DISTANCE_M > dist) {
        startingIndex = count;
        break;
      }
      count++;
    }

    return startingIndex;
  }

  /**
   * Setup the following of a new route starting from the specified waypoint index on the route
   *
   * @param index the index of the first waypoint to start from.
   *              An additional segment will be added from the vehicle to this starting point
   */
  protected void startRouteAtIndex(int index) {
    // Insert a starting waypoint at the current vehicle location which is connected to the route
    RouteWaypoint startingWP = new RouteWaypoint(new Location(hostVehicleLocation)); // don't want the route and vehicle location to reference the same object
    boolean ableToConnectToRoute = false;
    try {
      ableToConnectToRoute = activeRoute.insertWaypoint(startingWP, index);
    } catch (Exception e) {
      ableToConnectToRoute = false;
      log.debug("Exception caught when inserting route starting waypoint Exception = " + e);
    }

    // If we can't join the route return
    if (ableToConnectToRoute == false) {
      log.info("Could not join the route from the current location");
      return;
    }

    currentSegment = activeRoute.getSegments().get(index);
    currentSegmentIndex = index;
    currentWaypointIndex = index + 1; // The current waypoint should be the downtrack one
    downtrackDistance = activeRoute.lengthOfSegments(0, index - 1);
    crossTrackDistance = currentSegment.crossTrackDistance(hostVehicleLocation);

    handleEvent(WorkerEvent.ROUTE_STARTED);
  }

  /**
   * Function to be used as a callback for the arrival of NavSatFix messages
   *
   * @param msg The received message
   */
  protected void handleNavSatFixMsg(NavSatFix msg) {
    switch (msg.getStatus().getStatus()) {
      case NavSatStatus.STATUS_NO_FIX:
        log.warn("Gps data with no fix received by route");
        return;
      case NavSatStatus.STATUS_FIX:
        hostVehicleLocation
          .setLocationData(msg.getLatitude(), msg.getLongitude(), 0); // Used to be msg.getAltitude()
        break;
      case NavSatStatus.STATUS_SBAS_FIX:
        //TODO: Handle this variant
        hostVehicleLocation
          .setLocationData(msg.getLatitude(), msg.getLongitude(), 0); // Used to be msg.getAltitude()
        break;
      case NavSatStatus.STATUS_GBAS_FIX:
        //TODO: Handle this variant
        hostVehicleLocation
          .setLocationData(msg.getLatitude(), msg.getLongitude(), 0); // Used to be msg.getAltitude()
        break;
      default:
        //TODO: Handle this variant maybe throw exception?
        log.error("Unknown nav sat fix status type: " + msg.getStatus().getStatus());
        return;
    }

    if (getCurrentState() != WorkerState.FOLLOWING_ROUTE || activeRoute == null) {
      return;
    }

    // Loop to find current segment. This allows for small breaks in gps data
    while (atNextSegment()) { // TODO this might be problematic on tight turns
      currentSegmentIndex++;
      currentWaypointIndex++;
      // Check if the route has been completed
      if (currentSegmentIndex >= activeRoute.getSegments().size()) {
        handleEvent(WorkerEvent.ROUTE_COMPLETED);
        return;
      }
      currentSegment = activeRoute.getSegments().get(currentSegmentIndex);
    }

    // Update downtrack distance
    downtrackDistance = activeRoute.lengthOfSegments(0, currentSegmentIndex - 1) + currentSegment
      .downTrackDistance(hostVehicleLocation);

    // Update crosstrack distance
    crossTrackDistance = currentSegment.crossTrackDistance(hostVehicleLocation);

    log.debug("CrossTrackDistance = " + crossTrackDistance);
    log.debug("DownTrackDistance = " + downtrackDistance);
    log.debug("CurrentSegmentIndex = " + currentSegmentIndex);
    log.debug("CurrentWaypointIndex = " + currentWaypointIndex);
    if (leftRouteVicinity()) {
      handleEvent(WorkerEvent.LEFT_ROUTE);
    }

    // Publish updated route information
    routeManager.publishRouteState(getRouteStateTopicMsg(routeStateSeq, routeManager.getTime(), false));
    routeManager.publishCurrentRouteSegment(getCurrentRouteSegmentTopicMsg());
  }

  /**
   * Function to be used as a callback for received system alert messages
   *
   * @param msg the system alert message
   */
  protected void handleSystemAlertMsg(SystemAlert msg) {
    switch (msg.getType()) {
      case cav_msgs.SystemAlert.CAUTION:
        // TODO: Handle this message type
        break;
      case cav_msgs.SystemAlert.WARNING:
        // TODO: Handle this message type
        break;
      case cav_msgs.SystemAlert.FATAL:
        handleEvent(WorkerEvent.SYSTEM_FAILURE);
        log.info("route_manager received system fatal on system_alert and is abandoning the route");
        break;
      case cav_msgs.SystemAlert.NOT_READY:
        handleEvent(WorkerEvent.SYSTEM_NOT_READY);
        break;
      case cav_msgs.SystemAlert.DRIVERS_READY:
        systemOkay = true;
        log.info("route_manager received system ready on system_alert and is starting to publish");
        break;
      case cav_msgs.SystemAlert.SHUTDOWN:
        log.info("Route manager received a shutdown message");
        routeManager.shutdown();
        break;
      default:
        //TODO: Handle this variant maybe throw exception?
        log.error("System alert message received with unknown type: " + msg.getType());
    }
  }

  /**
   * Returns a message to be published on the current route segment topic
   *
   * @return route segment message
   */
  protected cav_msgs.RouteSegment getCurrentRouteSegmentTopicMsg() {
    if (currentSegment == null) {
      log.warn("Request for current segment message when current segment is null");
      return messageFactory.newFromType(cav_msgs.RouteSegment._TYPE);
    }
    return currentSegment.toMessage(messageFactory, currentWaypointIndex);
  }

  /**
   * Returns an active route message to publish
   *
   * @return route message
   */
  protected cav_msgs.Route getActiveRouteTopicMsg() {
    if (activeRoute == null) {
      log.warn("Request for active route message when current segment is null");
      return messageFactory.newFromType(cav_msgs.Route._TYPE);
    }
    return activeRoute.toMessage(messageFactory);
  }

  /**
   * Returns a route state message to be published
   *
   * @param seq  The header sequence
   * @param time the time
   * @return route state message
   */
  protected RouteState getRouteStateTopicMsg(int seq, Time time, boolean routeComplete) {
    RouteState routeState = messageFactory.newFromType(RouteState._TYPE);
    // ROUTE_COMPLETE is not an internal state so we set it here
    if (routeComplete) {
      routeState.setState(RouteState.ROUTE_COMPLETE);
    } else {
      switch (getCurrentState()) {
        case LOADING_ROUTES:
          routeState.setState(RouteState.LOADING_ROUTES);
          break;
        case ROUTE_SELECTION:
          routeState.setState(RouteState.ROUTE_SELECTION);
          break;
        case WAITING_TO_START:
          routeState.setState(RouteState.WAITING_TO_START);
          break;
        case FOLLOWING_ROUTE:
          routeState.setState(RouteState.FOLLOWING_ROUTE);
          break;
      }
    }

    if (activeRoute != null) {
      routeState.setCrossTrack(crossTrackDistance);
      routeState.setRouteID(activeRoute.getRouteID());
      routeState.setDownTrack(downtrackDistance);
    }

    std_msgs.Header hdr = messageFactory.newFromType(std_msgs.Header._TYPE);
    hdr.setFrameId("0");
    hdr.setSeq(seq);
    hdr.setStamp(time);
    return routeState;
  }

  //  /**  TODO: Add once we have tim messages
  //   * Function for used in Tim topic callback. Used to update waypoints on a route
  //   * @param msg The tim message
  //   */
  //   void handleTimMsg(cav_msgs.Tim msg);
}
