package gov.dot.fhwa.saxton.carma.plugins.platooning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import gov.dot.fhwa.saxton.carma.guidance.ManeuverPlanner;
import gov.dot.fhwa.saxton.carma.guidance.maneuvers.IManeuverInputs;
import gov.dot.fhwa.saxton.carma.guidance.plugins.PluginServiceLocator;
import gov.dot.fhwa.saxton.carma.guidance.util.ILogger;

public class LeaderSelectionTest {
    
    private PlatoonManager manager;
    private PlatooningPlugin mockPlugin;
    private ILogger mockLogger;
    private PluginServiceLocator mockPSL;
    private ManeuverPlanner mockPlanner;
    private IManeuverInputs mockInputs;
    
    @Before
    public void setup() {
        mockLogger = mock(ILogger.class);
        mockPlugin = mock(PlatooningPlugin.class);
        mockPSL = mock(PluginServiceLocator.class);
        mockPlanner = mock(ManeuverPlanner.class);
        mockInputs = mock(IManeuverInputs.class);
        when(mockPSL.getManeuverPlanner()).thenReturn(mockPlanner);
        when(mockPlanner.getManeuverInputs()).thenReturn(mockInputs);
        manager = new PlatoonManager(mockPlugin, mockLogger, mockPSL);
        // This test is for APF leader selection algorithm
        when(mockPlugin.getAlgorithmType()).thenReturn(1);
        when(mockPlugin.getLowerBoundary()).thenReturn(1.65);
        when(mockPlugin.getUpperBoundary()).thenReturn(1.75);
        when(mockPlugin.getMaxSpacing()).thenReturn(2.0);
        when(mockPlugin.getMinSpacing()).thenReturn(1.9);
        when(mockPlugin.getMinGap()).thenReturn(12.0);
        when(mockPlugin.getMaxGap()).thenReturn(14.0);
    }
    
    @Test
    public void returnNullOnEmptyPlatoonList() {
        PlatoonMember nullLeader = manager.getLeader();
        assertNull(nullLeader);
    }
    
    @Test
    public void onNoPreviousLeader() {
        manager.platoon.add(new PlatoonMember("1", 5.0, 4.9, 50.0, System.currentTimeMillis()));
        manager.platoon.add(new PlatoonMember("2", 5.0, 4.9, 60.0, System.currentTimeMillis()));
        Collections.sort(manager.platoon, (a, b) -> (Double.compare(b.getVehiclePosition(), a.getVehiclePosition())));
        PlatoonMember leader = manager.getLeader();
        assertEquals("2", leader.getStaticId());
        assertEquals("2", manager.previousLeader);
    }
    
    @Test
    public void gapErrorWithPredecessor() {
        manager.platoon.add(new PlatoonMember("1", 5.0, 5.0, 50.0, System.currentTimeMillis()));
        manager.platoon.add(new PlatoonMember("2", 5.0, 5.0, 60.0, System.currentTimeMillis()));
        Collections.sort(manager.platoon, (a, b) -> (Double.compare(b.getVehiclePosition(), a.getVehiclePosition())));
        when(mockInputs.getDistanceFromRouteStart()).thenReturn(40.0);
        when(mockInputs.getCurrentSpeed()).thenReturn(5.0);
        when(mockInputs.getDistanceToFrontVehicle()).thenReturn(10.0);
        manager.previousLeader = "2";
        PlatoonMember leader = manager.getLeader();
        assertEquals("1", leader.getStaticId());
        assertEquals("1", manager.previousLeader);
        when(mockInputs.getDistanceFromRouteStart()).thenReturn(37.0);
        when(mockInputs.getDistanceToFrontVehicle()).thenReturn(13.0);
        leader = manager.getLeader();
        assertEquals("1", leader.getStaticId());
        assertEquals("1", manager.previousLeader);
        manager.previousLeader = "2";
        leader = manager.getLeader();
        assertNull(leader);
        manager.previousLeader = "1";
        when(mockInputs.getDistanceFromRouteStart()).thenReturn(35.0);
        when(mockInputs.getDistanceToFrontVehicle()).thenReturn(15.0);
        leader = manager.getLeader();
        assertNull(leader); //?????????
    }
}
