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

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.RawEdgeIterator;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PrepareTowerNodesShortcutsTest {

    static LevelGraph createGraph() {
        return new GraphBuilder().levelGraphCreate();
    }

    @Test
    public void testSimpleShortcuts() {
        LevelGraph g = createGraph();
        // 1
        // 0-2-4-5
        // 3
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 2, true);
        g.edge(0, 3, 3, true);
        g.edge(2, 4, 4, true);
        g.edge(4, 5, 5, true);

        // assert additional 0, 5
        assertFalse(GraphUtility.contains(g.getEdges(0), 5));
        assertFalse(GraphUtility.contains(g.getEdges(5), 0));
        new PrepareTowerNodesShortcuts().graph(g).doWork();
        assertTrue(GraphUtility.contains(g.getEdges(0), 5));
        EdgeIterator iter = GraphUtility.until(g.getEdges(0), 5);
        assertEquals(11, iter.distance(), 1e-5);
        // the shortcut 0-4 is introduced as 5 gets a second edge from the 0-5 shortcut        
        assertEquals((5 + 1) * 2, GraphUtility.countEdges(g));

        // 1
        // 0->2->4->5
        // 3
        g = createGraph();
        g.edge(0, 1, 1, false);
        g.edge(0, 2, 2, false);
        g.edge(0, 3, 3, false);
        g.edge(2, 4, 4, false);
        g.edge(4, 5, 5, false);
        assertDirected0_5(g);
        assertEquals(5 + 1, GraphUtility.countEdges(g));

        g = createGraph();
        g.edge(0, 1, 1, false);
        g.edge(0, 2, 2, false);
        g.edge(0, 3, 3, false);
        g.edge(2, 4, 4, false);
        g.edge(4, 5, 5, false);
        g.edge(6, 5, 6, false);
        assertDirected0_5(g);
        assertEquals(6 + 1, GraphUtility.countEdges(g));
    }

    void assertDirected0_5(LevelGraph g) {
        // assert 0->5 but not 5->0
        assertFalse(GraphUtility.contains(g.getEdges(0), 5));
        assertFalse(GraphUtility.contains(g.getEdges(5), 0));
        new PrepareTowerNodesShortcuts().graph(g).doWork();
        assertTrue(GraphUtility.contains(g.getOutgoing(0), 5));
        assertFalse(GraphUtility.contains(g.getOutgoing(5), 0));
    }

    @Test
    public void testDirected() {
        LevelGraph g = createGraph();
        // 3->0->1<-2
        g.edge(0, 1, 10, false);
        g.edge(2, 1, 10, false);
        g.edge(3, 0, 10, false);

        assertFalse(new PrepareTowerNodesShortcuts().graph(g).has1InAnd1Out(2));
        assertTrue(new PrepareTowerNodesShortcuts().graph(g).has1InAnd1Out(0));
        assertFalse(new PrepareTowerNodesShortcuts().graph(g).has1InAnd1Out(1));
    }

    @Test
    public void testDirectedBug() {
        LevelGraph g = createGraph();
        initDirected1(g);
        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        assertEquals(2, prepare.shortcuts());
        assertEquals(-1, g.getLevel(5));
        assertEquals(0, g.getLevel(4));
        assertEquals(0, g.getLevel(3));
        assertEquals(0, g.getLevel(2));
    }

    @Test
    public void testCircleBug() {
        LevelGraph g = createGraph();
        //  /--1
        // -0--/
        //  |
        g.edge(0, 1, 10, true);
        g.edge(0, 1, 4, true);
        g.edge(0, 2, 10, true);
        g.edge(0, 3, 10, true);
        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        assertEquals(0, prepare.shortcuts());
    }

    @Test
    public void testChangeExistingShortcut() {
        LevelGraph g = createGraph();
        initBiGraph(g);

        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        assertEquals(1, prepare.shortcuts());
        EdgeSkipIterator iter = (EdgeSkipIterator) GraphUtility.until(g.getEdges(6), 3);
        assertEquals(40, iter.distance(), 1e-4);
        assertEquals(8, iter.skippedEdge());
    }

    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   /
    //  7-6-5-/
    void initBiGraph(Graph graph) {
        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 25, true);
        graph.edge(5, 6, 25, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);
    }

    // 0-1-.....-9-10
    // |         ^   \
    // |         |    |
    // 17-16-...-11<-/
    public static void initDirected2(Graph g) {
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);
        g.edge(9, 10, 1, true);
        g.edge(10, 11, 1, false);
        g.edge(11, 12, 1, true);
        g.edge(11, 9, 3, false);
        g.edge(12, 13, 1, true);
        g.edge(13, 14, 1, true);
        g.edge(14, 15, 1, true);
        g.edge(15, 16, 1, true);
        g.edge(16, 17, 1, true);
        g.edge(17, 0, 1, true);
    }

    //       8
    //       |
    //    6->0->1->3->7
    //    |        |
    //    |        v
    //10<-2---4<---5
    //    9
    public static void initDirected1(Graph g) {
        g.edge(0, 8, 1, true);
        g.edge(0, 1, 1, false);
        g.edge(1, 3, 1, false);
        g.edge(3, 7, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(5, 4, 1, false);
        g.edge(4, 2, 1, true);
        g.edge(2, 9, 1, false);
        g.edge(2, 10, 1, false);
        g.edge(2, 6, 1, true);
        g.edge(6, 0, 1, false);
    }

    @Test
    public void testMultiTypeShortcuts() {
        LevelGraph g = createGraph();
        g.edge(0, 10, 1, CarStreetType.flags(30, true));
        g.edge(0, 1, 10, CarStreetType.flags(30, true));
        g.edge(1, 2, 10, CarStreetType.flags(30, true));
        g.edge(2, 3, 10, CarStreetType.flags(30, true));
        g.edge(0, 4, 20, CarStreetType.flags(120, true));
        g.edge(4, 3, 20, CarStreetType.flags(120, true));
        g.edge(3, 11, 1, CarStreetType.flags(30, true));

        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        assertEquals(2, prepare.shortcuts());

        EdgeIterator iter = GraphUtility.until(g.getEdges(0), 3);
        assertEquals(30, iter.distance(), 1e-4);

        iter = GraphUtility.until(iter, 3);
        assertEquals(40, iter.distance(), 1e-4);
    }

    // prepare-routing.svg
    public static LevelGraph createShortcutsGraph() {
        final LevelGraph g = createGraph();
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(1, 4, 1, true);
        g.edge(2, 9, 1, true);
        g.edge(9, 3, 1, true);
        g.edge(10, 3, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);
        g.edge(4, 11, 1, true);
        g.edge(9, 14, 1, true);
        g.edge(10, 14, 1, true);
        g.edge(11, 12, 1, true);
        g.edge(12, 15, 1, true);
        g.edge(12, 13, 1, true);
        g.edge(13, 16, 1, true);
        g.edge(15, 16, 2, true);
        g.edge(14, 16, 1, true);
        return g;
    }

    @Test
    public void testIntroduceShortcuts() {
        LevelGraph g = createShortcutsGraph();
        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(g);
        prepare.doWork();
        assertEquals(4, prepare.shortcuts());

        assertTrue(GraphUtility.contains(g.getOutgoing(12), 16));
        EdgeIterator iter = GraphUtility.until(g.getOutgoing(12), 16);
        assertEquals(2, iter.distance(), 1e-4);

        assertTrue(GraphUtility.contains(g.getOutgoing(0), 1));
        iter = GraphUtility.until(g.getOutgoing(0), 1);
        assertEquals(1, iter.distance(), 1e-4);

        assertTrue(GraphUtility.contains(g.getOutgoing(2), 9));
        iter = GraphUtility.until(g.getOutgoing(2), 9);
        assertEquals(1, iter.distance(), 1e-4);

        assertTrue(GraphUtility.contains(g.getOutgoing(4), 9));
        iter = GraphUtility.until(g.getOutgoing(4), 9);
        assertEquals(5, iter.distance(), 1e-4);
    }

    public static void printEdges(LevelGraph g) {
        RawEdgeIterator iter = g.allEdges();
        while (iter.next()) {
            System.out.println(iter.nodeA() + "<->" + iter.nodeB()
                    + ", dist: " + (float) iter.distance()
                    + ", level:" + g.getLevel(iter.nodeA()) + "->" + g.getLevel(iter.nodeB())
                    + ", bothDir:" + CarStreetType.isBoth(iter.flags()));
        }
        System.out.println("---");
    }
}
