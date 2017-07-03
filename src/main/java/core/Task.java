/*
 * Copyright (C) 2017 jollion
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package core;

import boa.gui.DBUtil;
import boa.gui.GUI;
import boa.gui.GUIInterface;
import boa.gui.PropertyUtils;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import com.mongodb.util.JSON;
import static core.TaskRunner.logger;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import ij.IJ;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingWorker;
import measurement.MeasurementKeyObject;
import measurement.extraction.DataExtractor;
import org.apache.commons.lang.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import utils.ArrayUtil;
import utils.JSONUtils;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class Task extends SwingWorker<Integer, String> implements ProgressCallback {
        String dbName, dir;
        boolean preProcess, segmentAndTrack, trackOnly, measurements, generateTrackImages;
        List<Integer> positions;
        int[] structures;
        List<Pair<String, int[]>> extractMeasurementDir = new ArrayList<>();
        List<Pair<String, Exception>> errors = new ArrayList<>();
        MasterDAO db;
        int[] taskCounter;
        GUIInterface gui;
        
        public JSONObject toJSON() {
            JSONObject res=  new JSONObject();
            res.put("dbName", dbName); // put dbPath ?
            if (this.dir!=null) res.put("dir", dir);
            res.put("preProcess", preProcess);
            res.put("segmentAndTrack", segmentAndTrack);
            res.put("trackOnly", trackOnly);
            res.put("measurements", measurements);
            res.put("generateTrackImages", generateTrackImages);
            if (positions!=null) res.put("positions", positions);
            if (structures!=null) res.put("structures", JSONUtils.toJSONArray(structures));
            JSONArray ex = new JSONArray();
            for (Pair<String, int[]> p : extractMeasurementDir) {
                JSONObject o = new JSONObject();
                o.put("dir", p.key);
                o.put("s", JSONUtils.toJSONArray(p.value));
                ex.add(o);
            }
            res.put("extractMeasurementDir", ex);
            return res;
        }
        public void fromJSON(JSONObject data) {
            this.dbName = (String)data.getOrDefault("dbName", "");
            if (data.containsKey("dir")) dir = (String)data.get("dir");
            this.preProcess = (Boolean)data.getOrDefault("preProcess", false);
            this.segmentAndTrack = (Boolean)data.getOrDefault("segmentAndTrack", false);
            this.trackOnly = (Boolean)data.getOrDefault("trackOnly", false);
            this.measurements = (Boolean)data.getOrDefault("measurements", false);
            this.generateTrackImages = (Boolean)data.getOrDefault("generateTrackImages", false);
            if (data.containsKey("positions")) positions = ((JSONArray)data.get("positions"));
            if (data.containsKey("structures")) structures = JSONUtils.fromIntArray((JSONArray)data.get("structures"));
            if (data.containsKey("extractMeasurementDir")) {
                extractMeasurementDir = new ArrayList<>();
                JSONArray ex = (JSONArray)data.get("extractMeasurementDir");
                for (Object o : ex) {
                    JSONObject jo = (JSONObject)(o);
                    extractMeasurementDir.add(new Pair((String)jo.get("dir"), JSONUtils.fromIntArray((JSONArray)jo.get("s"))));
                }
            }
        }
        
        private Task() {
            if (GUI.hasInstance()) gui = GUI.getInstance();
            if (gui!=null) {
                addPropertyChangeListener(new PropertyChangeListener() {
                    @Override    
                    public void propertyChange(PropertyChangeEvent evt) {
                        if ("progress".equals(evt.getPropertyName())) {
                            int progress = (Integer) evt.getNewValue();
                            gui.setProgress(progress);
                            
                            //if (IJ.getInstance()!=null) IJ.getInstance().getProgressBar().show(progress, 100);
                            //logger.ingo("progress: {}%", i);
                                //gui.setProgress((Integer) evt.getNewValue());
                        }
                    }
                });
            }
        }
        public Task(MasterDAO db) {
            this();
            this.db=db;
            this.dbName=db.getDBName();
            this.dir=db.getDir();
        }
        public Task(String dbName) {
            this(dbName, null);
        }
        public Task(String dbName, String dir) {
            this();
            this.dbName=dbName;
            if (dir!=null && !"".equals(dir)) this.dir=dir;
            else { // look in local path
                String defPath = PropertyUtils.get(PropertyUtils.LOCAL_DATA_PATH);
                String d = null;
                if (defPath!=null) d = getLocalDirForDB(dbName, defPath);
                if (d==null) {
                    for (String path : PropertyUtils.getStrings(PropertyUtils.LOCAL_DATA_PATH)) {
                        if (path.equals(defPath)) continue;
                        d = getLocalDirForDB(dbName, path);
                        if (d!=null) break;
                    }
                }
                this.dir=d;
                if (this.dir==null) throw new IllegalArgumentException("no config file found for db: "+dbName);
                else {
                    //DBUtil.listExperiments("localhost");
                }
                
            }
        }
        
        public MasterDAO getDB() {
            initDB();
            return db;
        }
        private String getLocalDirForDB(String dbName, String dir) {
            File config = Utils.seach(dir, dbName+"_config.db", 2);
            if (config!=null) return config.getParent();
            else {
                config = Utils.seach(new File(dir).getParent(), dbName+"_config.db", 2);
                if (config!=null) return config.getParent();
                else return null;
            }
        }
        public Task setAllActions() {
            this.preProcess=true;
            this.segmentAndTrack=true;
            this.measurements=true;
            this.trackOnly=false;
            return this;
        }
        public Task setActions(boolean preProcess, boolean segment, boolean track, boolean measurements) {
            this.preProcess=preProcess;
            this.segmentAndTrack=segment;
            if (segmentAndTrack) trackOnly = false;
            else trackOnly = track;
            this.measurements=measurements;
            return this;
        }
        public Task setGenerateTrackImages(boolean generateTrackImages) {
            this.generateTrackImages=generateTrackImages;
            return this;
        }
        
        public Task setPositions(int... positions) {
            if (positions!=null && positions.length>0) this.positions=Utils.toList(positions);
            return this;
        }
        public Task unsetPositions(int... positions) {
            initDB();
            if (this.positions==null) this.positions=Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
            for (int p : positions) this.positions.remove((Integer)p);
            logger.debug("positions: {} ({})", this.positions, Utils.transform(this.positions, i->db.getExperiment().getPositionsAsString()[i]));
            return this;
        }
        private void initDB() {
            if (db==null) {
                if (!"localhost".equals(dir) && new File(dir).exists()) db = MasterDAOFactory.createDAO(dbName, dir, MasterDAOFactory.DAOType.DBMap);
                else db = MasterDAOFactory.createDAO(dbName, dir, MasterDAOFactory.DAOType.Morphium);
            }
        }
        public Task setPositions(String... positions) {
            if (positions!=null && positions.length>0) {
                initDB();
                this.positions=new ArrayList<>(positions.length);
                for (int i = 0; i<positions.length; ++i) this.positions.add(db.getExperiment().getPositionIdx(positions[i]));
                db=null;
            }
            return this;
        }
        
        public Task setStructures(int... structures) {
            if (structures!=null && structures.length>0) this.structures=structures;
            return this;
        }
        
        public Task addExtractMeasurementDir(String dir, int... extractStructures) {
            if (extractStructures!=null && extractStructures.length==0) extractStructures = null;
            this.extractMeasurementDir.add(new Pair(dir, extractStructures));
            return this;
        }
        public boolean isValid() {
            initDB();
            if (db.getExperiment()==null) {
                errors.add(new Pair(dbName, new Exception("DB: "+ dbName+ " not found")));
                printErrors();
                db = null;
                return false;
            } else {
                if (structures!=null) checkArray(structures, db.getExperiment().getStructureCount(), "Invalid structure: ");
                if (positions!=null) checkArray(positions, db.getExperiment().getPositionCount(), "Invalid position: ");
                // check files
                for (Pair<String, int[]> e : extractMeasurementDir) {
                    String exDir = e.key==null? db.getDir() : e.key;
                    File f= new File(exDir);
                    if (!f.exists()) errors.add(new Pair(dbName, new Exception("File: "+ exDir+ " not found")));
                    else if (!f.isDirectory()) errors.add(new Pair(dbName, new Exception("File: "+ exDir+ " is not a directory")));
                    else if (e.value!=null) checkArray(e.value, db.getExperiment().getStructureCount(), "Extract structure for dir: "+e.value+": Invalid structure: ");
                }
            }
            if (!measurements && !preProcess && !segmentAndTrack && ! trackOnly && extractMeasurementDir.isEmpty()) errors.add(new Pair(dbName, new Exception("No action to run!")));
            db=null;
            printErrors();
            logger.info("task : {}, isValid: {}", dbName, errors.isEmpty());
            return errors.isEmpty();
        }
        private void checkArray(int[] array, int maxValue, String message) {
            if (array[ArrayUtil.max(array)]>=maxValue) errors.add(new Pair(dbName, new Exception(message + array[ArrayUtil.max(array)]+ " not found, max value: "+maxValue)));
            if (array[ArrayUtil.min(array)]<0) errors.add(new Pair(dbName, new Exception(message + array[ArrayUtil.min(array)]+ " not found")));
        }
        private void checkArray(List<Integer> array, int maxValue, String message) {
            if (Collections.max(array)>=maxValue) errors.add(new Pair(dbName, new Exception(message + Collections.max(array)+ " not found, max value: "+maxValue)));
            if (Collections.min(array)<0) errors.add(new Pair(dbName, new Exception(message + Collections.min(array)+ " not found")));
        }
        public void printErrors() {
            if (!errors.isEmpty()) logger.error("Errors for Task: {}", toString());
            for (Pair<String, Exception> e : errors) logger.error(e.key, e.value);
        }
        public int countSubtasks() {
            initDB();
            if (positions==null) positions=Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
            if (structures==null) structures = ArrayUtil.generateIntegerArray(db.getExperiment().getStructureCount());
            int count=0;
            // preProcess: 
            if (preProcess) count += positions.size();
            if (this.segmentAndTrack || this.trackOnly) count += positions.size() * structures.length;
            if (this.measurements) count += positions.size();
            if (this.generateTrackImages) {
                int gen = 0;
                for (int s : structures)  if (!db.getExperiment().getAllDirectChildStructures(s).isEmpty()) ++gen;
                count+=positions.size()*gen;
            }
            count+=extractMeasurementDir.size();
            return count;
        }
        public void setSubtaskNumber(int[] taskCounter) {
            this.taskCounter=taskCounter;
        }
        public void runTask() {
            if (gui!=null) gui.setRunning(true);
            publish("init db...");
            initDB();
            publish("clering cache...");
            db.clearCache();
            publish("db cache cleared...");
            ImageWindowManagerFactory.getImageManager().flush();
            publishMemoryUsage("Before processing");
            if (positions==null) positions=Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
            if (structures==null) structures = ArrayUtil.generateIntegerArray(db.getExperiment().getStructureCount());
            publish("deleting objects...");
            boolean needToDeleteObjects = preProcess || segmentAndTrack;
            boolean deleteAll =  needToDeleteObjects && structures.length==db.getExperiment().getStructureCount() && positions.size()==db.getExperiment().getPositionCount();
            if (deleteAll) db.deleteAllObjects();
            boolean deleteAllField = needToDeleteObjects && structures.length==db.getExperiment().getStructureCount() && !deleteAll;
            logger.info("Run task: db: {} preProcess: {}, segmentAndTrack: {}, trackOnly: {}, runMeasurements: {}, need to delete objects: {}, delete all: {}, delete all by field: {}", dbName, preProcess, segmentAndTrack, trackOnly, measurements, needToDeleteObjects, deleteAll, deleteAllField);
            
            if (this.taskCounter==null) this.taskCounter = new int[]{0, this.countSubtasks()};
            publish("number of subtasks: "+countSubtasks());
            for (int pIdx : positions) {
                String position = db.getExperiment().getPosition(pIdx).getName();
                try {
                    run(position, deleteAllField);
                } catch (Exception e) {
                    errors.add(new Pair(position, e));
                }
            }
            
            for (Pair<String, int[]> e  : this.extractMeasurementDir) extract(e.key==null?db.getDir():e.key, e.value);
            
            db.clearCache();
            db=null;
        }
    private void run(String position, boolean deleteAllField) {
        publish("Position: "+position);
        if (deleteAllField) db.getDao(position).deleteAllObjects();
        if (preProcess) {
            publish("Pre-Processing: DB: "+dbName+", Position: "+position);
            logger.info("Pre-Processing: DB: {}, Position: {}", dbName, position);
            Processor.preProcessImages(db.getExperiment().getPosition(position), db.getDao(position), true, preProcess);
            db.getExperiment().getPosition(position).flushImages(true, false);
            incrementProgress();
        }
        //publishMemoryUsage("After PreProcessing:");
        if (segmentAndTrack || trackOnly) {
            logger.info("Processing: DB: {}, Position: {}", dbName, position);
            for (int s : structures) { // TODO take code from processor
                publish("Processing structure: "+s);
                List<Pair<String, Exception>> e = Processor.processAndTrackStructures(db.getDao(position), true, trackOnly, s);
                errors.addAll(e);
                incrementProgress();
                
                if (generateTrackImages && !db.getExperiment().getAllDirectChildStructures(s).isEmpty()) {
                    publish("Generating Track Images for Structure: "+s);
                    Processor.generateTrackImages(db.getDao(position), s);
                    incrementProgress();
                }
            }
            //publishMemoryUsage("After Processing:");
        } else if (generateTrackImages) {
            publish("Generating Track Images...");
            // generate track images for all selected structure that has direct children
            for (int s : structures) {
                if (db.getExperiment().getAllDirectChildStructures(s).isEmpty()) continue;
                Processor.generateTrackImages(db.getDao(position), s);
                incrementProgress();
            }
            //publishMemoryUsage("After Generate Track Images:");
        }
        
        if (measurements) {
            publish("Measurements...");
            logger.info("Measurements: DB: {}, Field: {}", dbName, position);
            db.getDao(position).deleteAllMeasurements();
            List<Pair<String, Exception>> e = Processor.performMeasurements(db.getDao(position));
            errors.addAll(e);
            incrementProgress();
            //publishMemoryUsage("After Measurements");
        }
        
        if (preProcess) db.updateExperiment(); // save field preProcessing configuration value @ each field
        db.clearCache(position); // also flush images
        db.getSelectionDAO().clearCache();
        ImageWindowManagerFactory.getImageManager().flush();
        System.gc();
        publishMemoryUsage("After clearing cache");
    }
    private void publishMemoryUsage(String message) {
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        publish(message+" Used Memory: "+ (used/1000000)/1000d+"Go ("+ (int)Math.round(100d*used/((double)Runtime.getRuntime().totalMemory())) + "%)");
    }
    private void extract(String dir, int[] structures) {
        if (structures==null) structures = ArrayUtil.generateIntegerArray(db.getExperiment().getStructureCount());
        String file = dir+File.separator+db.getDBName()+Utils.toStringArray(structures, "_", "", "_")+".csv";
        publish("extracting measurements from structures: "+Utils.toStringArray(structures));
        logger.info("measurements will be extracted to: {}", file);
        Map<Integer, String[]> keys = db.getExperiment().getAllMeasurementNamesByStructureIdx(MeasurementKeyObject.class, structures);
        DataExtractor.extractMeasurementObjects(db, file, getPositionNames(), keys);
        incrementProgress();
    }
        private List<String> getPositionNames() {
            if (positions==null) positions=Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
            List<String> res = new ArrayList<>(positions.size());
            for (int i : positions) res.add(db.getExperiment().getPosition(i).getName());
            return res;
        }
    @Override public String toString() {
        String res =  "db: "+dbName;
        if (preProcess) res+="/preProcess/";
        if (segmentAndTrack) res+="/segmentAndTrack/";
        else if (trackOnly) res+="/trackOnly/";
        if (measurements) res+="/measurements/";
        if (structures!=null) res+="/structures:"+ArrayUtils.toString(structures)+"/";
        if (positions!=null) res+="/positions:"+ArrayUtils.toString(positions)+"/";
        if (!extractMeasurementDir.isEmpty()) {
            res+= "/Extract: ";
            for (Pair<String, int[]> p : this.extractMeasurementDir) res+=p.key+ "="+ArrayUtils.toString(res);
            res+="/";
        }
        return res;
    }
    @Override
    public void incrementProgress() {
        setProgress(100*(++taskCounter[0])/taskCounter[1]);
    }
    @Override
    protected Integer doInBackground() throws Exception {
        this.runTask();
        return this.errors.size();
    }
    @Override
    protected void process(List<String> strings) {
        if (gui!=null) for (String s : strings) gui.setMessage(s);
        for (String s : strings) logger.info(s);
    }
    @Override 
    public void done() {
        this.publish("Job done. Errors: "+this.errors.size());
        for (Pair<String, Exception> e : errors) {
            publish("Error: "+e.key);
            for (StackTraceElement s : e.value.getStackTrace()) publish(s.toString());
        }
        this.printErrors();
        this.publish("------------------------------------------------------------------");
        if (gui!=null) gui.setRunning(false);
    }

    @Override
    public void incrementTaskNumber(int subtask) {
        if (taskCounter!=null) this.taskCounter[1]+=subtask;
    }

    @Override
    public void log(String message) {
        publish(message);
    }


}
