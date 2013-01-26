/*
 *  Copyright 2011 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

//import com.graphhopper.routing.AStar;
//import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Several utility classes which are compatible with Java6 on Android.
 *
 * @see Helper7 for none-Android compatible methods.
 * @author Peter Karich,
 */
public class Helper {

    private static Logger logger = LoggerFactory.getLogger(Helper.class);
    public static final int MB = 1 << 20;

    private Helper() {
    }

    public static void loadProperties(Map<String, String> map, Reader tmpReader) throws IOException {
        BufferedReader reader = new BufferedReader(tmpReader);
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("//") || line.startsWith("#")) {
                    continue;
                }

                if (line.isEmpty()) {
                    continue;
                }

                int index = line.indexOf("=");
                if (index < 0) {
                    logger.warn("Skipping configuration at line:" + line);
                    continue;
                }

                String field = line.substring(0, index);
                String value = line.substring(index + 1);
                map.put(field, value);
            }
        } finally {
            reader.close();
        }
    }

    public static List<String> readFile(String file) throws IOException {
        return readFile(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    }

    public static List<String> readFile(Reader simpleReader) throws IOException {
        BufferedReader reader = new BufferedReader(simpleReader);
        try {
            List<String> res = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                res.add(line);
            }
            return res;
        } finally {
            reader.close();
        }
    }

    public static int idealIntArraySize(int need) {
        return idealByteArraySize(need * 4) / 4;
    }

    public static int idealByteArraySize(int need) {
        for (int i = 4; i < 32; i++) {
            if (need <= (1 << i) - 12) {
                return (1 << i) - 12;
            }
        }
        return need;
    }

    public static void removeDir(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                removeDir(f);
            }
        }

        file.delete();
    }

    public static String getMemInfo() {
        return "totalMB:" + Runtime.getRuntime().totalMemory() / MB
                + ", usedMB:" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB;
    }

    public static int sizeOfObjectRef(int factor) {
        // pointer to class, flags, lock
        return factor * (4 + 4 + 4);
    }

    public static int sizeOfLongArray(int length, int factor) {
        // pointer to class, flags, lock, size
        return factor * (4 + 4 + 4 + 4) + 8 * length;
    }

    public static int sizeOfObjectArray(int length, int factor) {
        // TODO add 4byte to make a multiple of 8 in some cases
        // TODO compressed oop
        return factor * (4 + 4 + 4 + 4) + 4 * length;
    }

    public static void close(Closeable cl) {
        try {
            if (cl != null) {
                cl.close();
            }
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't close resource", ex);
        }
    }

    public static boolean isEmpty(String strOsm) {
        return strOsm == null || strOsm.trim().isEmpty();
    }

    /**
     * Creates a preparation wrapper for the specified algorithm. Warning/TODO:
     * set the _graph for the instance otherwise you'll get NPE when calling
     * createAlgo. Possible values for algorithmStr: astar (A* algorithm),
     * astarbi (bidirectional A*) dijkstra (Dijkstra), dijkstrabi and
     * dijkstraNative (a bit faster bidirectional Dijkstra).
     */
    public static AlgorithmPreparation createAlgoPrepare(final String algorithmStr) {
        return new NoOpAlgorithmPreparation() {
            @Override
            public RoutingAlgorithm createAlgo() {
                return createAlgoFromString(_graph, algorithmStr);
            }
        };
    }

    /**
     * Possible values: astar (A* algorithm), astarbi (bidirectional A*)
     * dijkstra (Dijkstra), dijkstrabi and dijkstraNative (a bit faster
     * bidirectional Dijkstra).
     */
    public static RoutingAlgorithm createAlgoFromString(Graph g, String algorithmStr) {
        if (g == null) {
            throw new NullPointerException("You have to specify a graph different from null!");
        }
        RoutingAlgorithm algo = null;
        if ("dijkstrabi".equalsIgnoreCase(algorithmStr)) {
            algo = new DijkstraBidirectionRef(g);
        } /*else if ("dijkstraNative".equalsIgnoreCase(algorithmStr)) {
            algo = new DijkstraBidirection(g);
        } else if ("dijkstra".equalsIgnoreCase(algorithmStr)) {
            algo = new DijkstraSimple(g);
        } else if ("astarbi".equalsIgnoreCase(algorithmStr)) {
            algo = new AStarBidirection(g).setApproximation(true);
        } else {
            algo = new AStar(g);
        }*/
        return algo;
    }

    /**
     * Determines if the specified ByteBuffer is one which maps to a file!
     */
    public static boolean isFileMapped(ByteBuffer bb) {
        if (bb instanceof MappedByteBuffer) {
            try {
                ((MappedByteBuffer) bb).isLoaded();
                return true;
            } catch (UnsupportedOperationException ex) {
            }
        }
        return false;
    }

    public static void unzip(String from, boolean remove) throws IOException {
        String to = pruneFileEnd(from);
        unzip(from, to, remove);
    }

    public static boolean unzip(String fromStr, String toStr, boolean remove) throws IOException {
        File from = new File(fromStr);
        File to = new File(toStr);
        if (!from.exists() || fromStr.equals(toStr))
            return false;

        if (!to.exists())
            to.mkdirs();

        ZipInputStream zis = new ZipInputStream(new FileInputStream(from));
        try {
            ZipEntry ze = zis.getNextEntry();
            byte[] buffer = new byte[1024];
            while (ze != null) {
                if (ze.isDirectory()) {
                    new File(to, ze.getName()).mkdir();
                } else {
                    File newFile = new File(to, ze.getName());
                    FileOutputStream fos = new FileOutputStream(newFile);
                    try {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    } finally {
                        fos.close();
                    }
                }
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
        } finally {
            zis.close();
        }

        if (remove)
            Helper.removeDir(from);

        return true;
    }

    public static int calcIndexSize(BBox graphBounds) {
        if (!graphBounds.isValid())
            throw new IllegalArgumentException("Bounding box is not valid to calculate index size: " + graphBounds);
        double dist = new DistanceCalc().calcDist(graphBounds.maxLat, graphBounds.minLon,
                graphBounds.minLat, graphBounds.maxLon);
        // convert to km and maximum 5000km => 25mio capacity, minimum capacity is 2000
        dist = Math.min(dist / 1000, 5000);
        return Math.max(2000, (int) (dist * dist));
    }

    public static String pruneFileEnd(String file) {
        int index = file.lastIndexOf(".");
        if (index < 0)
            return file;
        return file.substring(0, index);
    }

    public static TIntList createTList(int... list) {
        TIntList res = new TIntArrayList(list.length);
        for (int val : list) {
            res.add(val);
        }
        return res;
    }

    public static PointList createPointList(double... list) {
        if (list.length % 2 != 0)
            throw new IllegalArgumentException("list should consist of lat,lon pairs!");
        PointList res = new PointList(list.length);
        int max = list.length / 2;
        for (int i = 0; i < max; i++) {
            res.add(list[2 * i], list[2 * i + 1]);
        }
        return res;
    }

    /**
     * Converts a double (maximum value 10000) into an integer.
     *
     * @return the integer to be stored
     */
    public static int doubleToInt(double deg) {
        return (int) (deg * INT_FACTOR);
    }

    /**
     * Converts back the once transformed storedInt from doubleToInt
     */
    public static double intToDouble(int storedInt) {
        return (double) storedInt / INT_FACTOR;
    }

    /**
     * Converts into an integer to be compatible with the still limited
     * DataAccess class (accepts only integer values). But this conversation
     * also reduces memory consumption where the precision loss is accceptable.
     * As +- 180° and +-90° are assumed as maximum values.
     *
     * @return the integer of the specified degree
     */
    public static int degreeToInt(double deg) {
        return (int) (deg * DEGREE_FACTOR);
    }

    /**
     * Converts back the integer value.
     *
     * @return the degree value of the specified integer
     */
    public static double intToDegree(int storedInt) {
        // Double.longBitsToDouble();
        return (double) storedInt / DEGREE_FACTOR;
    }
    // +- 180 and +-90 => let use use 400
    private static final float DEGREE_FACTOR = Integer.MAX_VALUE / 400f;
    private static final float INT_FACTOR = Integer.MAX_VALUE / 10000f;
    /**
     * The file version is independent of the real world version. E.g. to make
     * major version jumps without the need to change the file version.
     */
    public static final int VERSION_FILE = 4;
    /**
     * The version without the snapshot string
     */
    public static final String VERSION;
    public static final boolean SNAPSHOT;

    static {
        String version = "0.0";
        try {
            List<String> v = readFile(new InputStreamReader(Helper.class.getResourceAsStream("/version"), "UTF-8"));
            version = v.get(0);
        } catch (Exception ex) {
            System.err.println("GraphHopper Initialization ERROR: cannot read version!? " + ex.getMessage());
        }
        int indexM = version.indexOf("-");
        int indexP = version.indexOf(".");
        if ("${project.version}".equals(version)) {
            VERSION = "0.0";
            SNAPSHOT = true;
            System.err.println("GraphHopper Initialization WARNING: maven did not preprocess the version file!?");
        } else if ("0.0".equals(version) || indexM < 0 || indexP >= indexM) {
            VERSION = "0.0";
            SNAPSHOT = true;
            System.err.println("GraphHopper Initialization WARNING: cannot get version!?");
        } else {
            // throw away the "-SNAPSHOT"
            int major = -1, minor = -1;
            try {
                major = Integer.parseInt(version.substring(0, indexP));
                minor = Integer.parseInt(version.substring(indexP + 1, indexM));
            } catch (Exception ex) {
                System.err.println("GraphHopper Initialization WARNING: cannot parse version!? " + ex.getMessage());
            }
            SNAPSHOT = version.toLowerCase().contains("-snapshot");
            VERSION = major + "." + minor;
        }
    }

    public static void cleanMappedByteBuffer(final ByteBuffer buffer) {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override public Object run() throws Exception {
                    final Method getCleanerMethod = buffer.getClass().getMethod("cleaner");
                    getCleanerMethod.setAccessible(true);
                    final Object cleaner = getCleanerMethod.invoke(buffer);
                    if (cleaner != null)
                        cleaner.getClass().getMethod("clean").invoke(cleaner);
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            final RuntimeException ioe = new RuntimeException("unable to unmap the mapped buffer");
            ioe.initCause(e.getCause());
            throw ioe;
        }
    }
}
