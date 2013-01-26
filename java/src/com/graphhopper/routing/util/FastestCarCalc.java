/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

/**
 * @author Peter Karich
 */
public class FastestCarCalc implements WeightCalculation {

    public final static FastestCarCalc DEFAULT = new FastestCarCalc();

    private FastestCarCalc() {
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / CarStreetType.MAX_SPEED;
    }

    @Override
    public long getTime(double distance, int flags) {
        return (long) (distance * 3.6 / CarStreetType.getSpeed(flags));
    }

    @Override
    public double getWeight(double distance, int flags) {
        return distance / CarStreetType.getSpeedPart(flags);
    }

    @Override public double revertWeight(double weight, int flags) {
        return weight * CarStreetType.getSpeedPart(flags);
    }

    @Override public String toString() {
        return "FASTEST";
    }
}
