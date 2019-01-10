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

package gov.dot.fhwa.saxton.carma.signal_plugin.ead;


/**
 * Factory method to instantiate an appropriate ITrajectory concrete class
 */
public class TrajectoryFactory {

    /**
     * Reflection helper to create a ITrajectory object based on class name
     *
     * @param trajectoryClassName
     * @throws If the target class cannot be found or initialized the corresponding exceptions will be thrown by this function
     * @return A non-null fully initialized object of the requested class cast to type ITrajectory
     */
    public static ITrajectory newInstance(String trajectoryClassName) throws Exception {
        @SuppressWarnings("rawtypes")
		Class tClass = null;

        tClass = Class.forName(trajectoryClassName);

        Object newObject = null;
        newObject = tClass.newInstance();

        return (ITrajectory) newObject;
    }

}
