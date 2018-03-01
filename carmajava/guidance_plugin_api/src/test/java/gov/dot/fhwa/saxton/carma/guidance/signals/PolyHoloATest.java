/*
 * Copyright (C) 2018 LEIDOS.
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

package gov.dot.fhwa.saxton.carma.guidance.signals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.Assert.assertEquals;
import gov.dot.fhwa.saxton.carma.guidance.util.ILogger;
import gov.dot.fhwa.saxton.carma.guidance.util.ILoggerFactory;
import gov.dot.fhwa.saxton.carma.guidance.util.LoggerManager;

public class PolyHoloATest {
    
    private static ILogger log_;

    @Before
    public void setup() {
      ILoggerFactory mockFact = mock(ILoggerFactory.class);
      log_ = mock(ILogger.class);
      when(mockFact.createLoggerForClass(anyObject())).thenReturn(log_);
      LoggerManager.setLoggerFactory(mockFact);
    }
    
    @Test
    public void testLinear1() {
    try {
      Thread.sleep(2);
      log_.debug("TEST", "===== Entering testLinear1");
      Thread.sleep(2); //to force the log file to be sequenced more correctly
    } catch (InterruptedException e1) {
      // do nothing
    }
      
    PolyHoloA s = new PolyHoloA(0.1);

    s.apply(new Signal<>(1.0));
    s.apply(new Signal<>(2.0));
    s.apply(new Signal<>(3.0));
    s.apply(new Signal<>(4.0));
    s.apply(new Signal<>(5.0));
    s.apply(new Signal<>(6.0));
    s.apply(new Signal<>(7.0));
    s.apply(new Signal<>(8.0));
    s.apply(new Signal<>(9.0));
    s.apply(new Signal<>(10.0));
    s.apply(new Signal<>(11.0));
    s.apply(new Signal<>(12.0));
    s.apply(new Signal<>(13.0));
    s.apply(new Signal<>(14.0));
    double speed = s.apply(new Signal<>(15.0)).get().getData();
    
    assertEquals(15.0, speed, 0.001);
    }
    
    @Test
    public void testSample2() { //from textbook example
    try {
      Thread.sleep(2);
      log_.debug("TEST", "===== Entering testSample2");
      Thread.sleep(2); //to force the log file to be sequenced more correctly
    } catch (InterruptedException e1) {
      // do nothing
    }
      
    PolyHoloA s = new PolyHoloA(1.0);
      s.setAlternatePolyPoints(5);
      
      s.apply(new Signal<>(-4.0));
      s.apply(new Signal<>(-1.0));
      s.apply(new Signal<>(4.0));
      s.apply(new Signal<>(11.0));
      double y = s.apply(new Signal<>(20.0)).get().getData();
      
      assertEquals(20.0, y, 0.1);
    }
    
    @Test
    public void testGlidepath131846Late() { //from 150220 - this isn't for functional testing, but for generating analysis data
    try {
      Thread.sleep(2);
      log_.debug("TEST", "===== Entering testGlidepath131846Late");
      Thread.sleep(2); //to force the log file to be sequenced more correctly
    } catch (InterruptedException e1) {
      // do nothing
    }
    
    //open a data file for all these points
    FileWriter fw;
    try {
      fw = new FileWriter("logs\\Analysis131846Late.txt");
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    PrintWriter f = new PrintWriter(fw);
      
    final int SMOOTHING_POINTS = 11;
    final int PRELIM_SIZE = 15; //max of SMOOTHING_POINTS & Holo size
    final int DATA_SIZE   = 25;
    
      PolyHoloA s = new PolyHoloA(0.1);
      s.setAlternatePolyPoints(SMOOTHING_POINTS);
      
      //set up preliminary data for 21 points
      double[] prelim = {  4.4352,  4.411,  4.3978,  4.3868,  4.4,  4.4198,  4.4264,  4.4726,  4.4396,  4.4418,
                4.4484,  4.5056,  4.477,  4.4902,  4.4506,  4.5144,  4.499,  4.5562,  4.532,  4.451  };

      double[] data = {  4.5474,  4.4836,  4.5056,  4.4462,  4.4506,  4.4264,  4.4836,  4.6266,  4.3692,  4.543,
                4.4242,  4.4682,  4.4242,  4.4858,  4.4308,  4.4572,  4.4264,  4.4748,  4.4132,  4.4308,
                4.4638,  4.4,  4.466,  4.4506,  4.422};

      for (int i = 0;  i < PRELIM_SIZE;  ++i) {
        s.apply(new Signal<>(prelim[20 - PRELIM_SIZE + i])); //use the right-hand end of the array
      }
      
      double speed[] = new double[DATA_SIZE];
      double accel[] = new double[DATA_SIZE];
      double jerk[] = new double[DATA_SIZE];
      
      f.println("Speeds");
      for (int j = 0;  j < DATA_SIZE;  ++j) {
        speed[j] = s.apply(new Signal<>(data[j])).get().getData();
        accel[j] = s.getSmoothedDerivative();
        jerk[j]  = s.getSmoothedSecondDerivative();
        f.println(speed[j]);
      }
      
      f.println(" ");
      f.println("Accel");
      for (int k = 0;  k < DATA_SIZE;  ++k) {
        f.println(accel[k]);
      }
      
      f.println(" ");
      f.println("Jerks");
      for (int m = 0;  m < DATA_SIZE;  ++m) {
        f.println(jerk[m]);
      }
      
      f.close();
    }
    
    @Test
    public void testGlidepath131846Early() { //from 150220 - this isn't for functional testing, but for generating analysis data
    try {
      Thread.sleep(2);
      log_.debug("TEST", "===== Entering testGlidepath131846Early");
      Thread.sleep(2); //to force the log file to be sequenced more correctly
    } catch (InterruptedException e1) {
      // do nothing
    }
    
    //open a data file for all these points
    FileWriter fw;
    try {
      fw = new FileWriter("logs\\Analysis131846Early.txt");
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    PrintWriter f = new PrintWriter(fw);
      
    final int SMOOTHING_POINTS = 11;
    final int PRELIM_SIZE = 15; //max of SMOOTHING_POINTS & Holo size
    final int DATA_SIZE   = 32;
    
      PolyHoloA s = new PolyHoloA(0.1);
      s.setAlternatePolyPoints(SMOOTHING_POINTS);
      
      //set up preliminary data for 20 points
      double[] prelim = {  0.011,  0.0264,  0.022,  0.0484,  0.1562,  0.3058,  0.3322,  0.5104,  0.8272,  1.0054,
                1.2826,  1.6412,  2.1604,  2.112,  2.3562,  2.629,  2.8336,  3.113,  3.3858,  3.5772};

      double[] data = {  3.9204,  4.2306,  4.6002,  4.675,  4.9786,  5.2074,  5.5902,  5.7574,  5.973,  6.2304,
                6.4108,  6.5538,  6.6352,  6.8684,  7.0708,  6.787,  6.7958,  6.4372,  5.7662,  4.983,
                4.4924,  3.41,  1.98,  3.2934,  2.8908,  2.728,  2.5564,  2.5124,  2.5212,  2.6136,
                2.5608,  2.7302};

      for (int i = 0;  i < PRELIM_SIZE;  ++i) {
        s.apply(new Signal<>(prelim[20 - PRELIM_SIZE + i])); //use the right-hand end of the array
      }
      
      double speed[] = new double[DATA_SIZE];
      double accel[] = new double[DATA_SIZE];
      double jerk[] = new double[DATA_SIZE];
      
      f.println("Speeds");
      for (int j = 0;  j < DATA_SIZE;  ++j) {
        speed[j] = s.apply(new Signal<>(data[j])).get().getData();
        accel[j] = s.getSmoothedDerivative();
        jerk[j]  = s.getSmoothedSecondDerivative();
        f.println(speed[j]);
      }
      assertEquals(2.5593, accel[0], 0.0001);
      assertEquals(1.7567, accel[12], 0.0001);
      assertEquals(0.713, jerk[2], 0.001);
      assertEquals(0.093, jerk[10], 0.001);
      
      f.println(" ");
      f.println("Accel");
      for (int k = 0;  k < DATA_SIZE;  ++k) {
        f.println(accel[k]);
      }
      
      f.println(" ");
      f.println("Jerks");
      for (int m = 0;  m < DATA_SIZE;  ++m) {
        f.println(jerk[m]);
      }
      
      f.close();
    }
    
    @After
    public void shutdown() {
    }
}