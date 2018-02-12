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
package boa.core;

import boa.ui.Console;
import boa.ui.DBUtil;
import static boa.ui.DBUtil.searchForLocalDir;
import static boa.ui.DBUtil.searchLocalDirForDB;
import boa.gui.GUI;
import boa.ui.LogUserInterface;
import boa.ui.MultiUserInterface;
import boa.ui.UserInterface;
import boa.ui.PropertyUtils;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import static boa.core.TaskRunner.logger;
import boa.configuration.experiment.PreProcessingChain;
import boa.data_structure.dao.DBMapMasterDAO;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.MasterDAOFactory;
import ij.IJ;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import boa.measurement.MeasurementKeyObject;
import boa.measurement.MeasurementExtractor;
import org.apache.commons.lang.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import boa.utils.ArrayUtil;
import boa.utils.FileIO;
import boa.utils.FileIO.ZipWriter;
import boa.utils.ImportExportJSON;
import boa.utils.JSONUtils;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class Task extends SwingWorker<Integer, String> implements ProgressCallback {
        String dbName, dir;
        boolean preProcess, segmentAndTrack, trackOnly, measurements, generateTrackImages, exportPreProcessedImages, exportTrackImages, exportObjects, exportSelections, exportConfig;
        boolean exportData;
        List<Integer> positions;
        int[] structures;
        List<Pair<String, int[]>> extractMeasurementDir = new ArrayList<>();
        List<Pair<String, Exception>> errors = new ArrayList<>();
        MasterDAO db;
        int[] taskCounter;
        UserInterface ui;
        public JSONObject toJSON() {
            JSONObject res=  new JSONObject();
            res.put("dbName", dbName); 
            if (this.dir!=null) res.put("dir", dir); // put dbPath ?
            res.put("preProcess", preProcess);
            res.put("segmentAndTrack", segmentAndTrack);
            res.put("trackOnly", trackOnly);
            res.put("measurements", measurements);
            res.put("generateTrackImages", generateTrackImages);
            res.put("exportPreProcessedImages", exportPreProcessedImages);
            res.put("exportTrackImages", exportTrackImages);
            res.put("exportObjects", exportObjects);
            res.put("exportSelections", exportSelections);
            res.put("exportConfig", exportConfig);
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
        public Task fromJSON(JSONObject data) {
            if (data==null) return null;
            this.dbName = (String)data.getOrDefault("dbName", "");
            if (data.containsKey("dir")) {
                dir = (String)data.get("dir");
                if (!new File(dir).exists()) dir=null;
            }
            if (dir==null) dir = searchForLocalDir(dbName);
            this.preProcess = (Boolean)data.getOrDefault("preProcess", false);
            this.segmentAndTrack = (Boolean)data.getOrDefault("segmentAndTrack", false);
            this.trackOnly = (Boolean)data.getOrDefault("trackOnly", false);
            this.measurements = (Boolean)data.getOrDefault("measurements", false);
            this.generateTrackImages = (Boolean)data.getOrDefault("generateTrackImages", false);
            this.exportPreProcessedImages = (Boolean)data.getOrDefault("exportPreProcessedImages", false);
            this.exportTrackImages = (Boolean)data.getOrDefault("exportTrackImages", false);
            this.exportObjects = (Boolean)data.getOrDefault("exportObjects", false);
            this.exportSelections = (Boolean)data.getOrDefault("exportSelections", false);
            this.exportConfig = (Boolean)data.getOrDefault("exportConfig", false);
            if (exportPreProcessedImages || exportTrackImages || exportObjects || exportSelections || exportConfig) exportData= true;
            if (data.containsKey("positions")) positions = JSONUtils.fromIntArrayToList((JSONArray)data.get("positions"));
            if (data.containsKey("structures")) structures = JSONUtils.fromIntArray((JSONArray)data.get("structures"));
            if (data.containsKey("extractMeasurementDir")) {
                extractMeasurementDir = new ArrayList<>();
                JSONArray ex = (JSONArray)data.get("extractMeasurementDir");
                for (Object o : ex) {
                    JSONObject jo = (JSONObject)(o);
                    extractMeasurementDir.add(new Pair((String)jo.get("dir"), JSONUtils.fromIntArray((JSONArray)jo.get("s"))));
                }
            }
            return this;
        }
        public Task setUI(UserInterface ui) {
            if (ui==null) this.ui=null;
            else {
                if (ui.equals(this.ui)) return this;
                this.ui=ui;
                addPropertyChangeListener(new PropertyChangeListener() {
                    @Override    
                    public void propertyChange(PropertyChangeEvent evt) {
                        if ("progress".equals(evt.getPropertyName())) {
                            int progress = (Integer) evt.getNewValue();
                            ui.setProgress(progress);
                            
                            //if (IJ.getInstance()!=null) IJ.getInstance().getProgressBar().show(progress, 100);
                            //logger.ingo("progress: {}%", i);
                                //gui.setProgress((Integer) evt.getNewValue());
                        }
                    }
                });
            }
            return this;
        }
        public Task() {
            if (GUI.hasInstance()) setUI(GUI.getInstance());
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
            else this.dir = searchForLocalDir(dbName);
        }
        public Task setDBName(String dbName) {
            if (dbName!=null && dbName.equals(this.dbName)) return this;
            this.db=null;
            this.dbName=dbName;
            return this;
        }
        public Task setDir(String dir) {
            if (dir!=null && dir.equals(this.dir)) return this;
            this.db=null;
            this.dir=dir;
            return this;
        }
        
        public List<Pair<String, Exception>> getErrors() {return errors;}
        public MasterDAO getDB() {
            initDB();
            return db;
        }
        public String getDir() {
            return dir;
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

        public boolean isPreProcess() {
            return preProcess;
        }

        public boolean isSegmentAndTrack() {
            return segmentAndTrack;
        }

        public boolean isTrackOnly() {
            return trackOnly;
        }

        public boolean isMeasurements() {
            return measurements;
        }

        public boolean isGenerateTrackImages() {
            return generateTrackImages;
        }
        
        public Task setGenerateTrackImages(boolean generateTrackImages) {
            this.generateTrackImages=generateTrackImages;
            return this;
        }
        
        public Task setExportData(boolean preProcessedImages, boolean trackImages, boolean objects, boolean config, boolean selections) {
            this.exportPreProcessedImages=preProcessedImages;
            this.exportTrackImages=trackImages;
            this.exportObjects=objects;
            this.exportConfig=config;
            this.exportSelections=selections;
            if (preProcessedImages || trackImages || objects || config || selections) exportData= true;
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
                //else db = MasterDAOFactory.createDAO(dbName, dir, MasterDAOFactory.DAOType.Morphium);
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
            if (db.isReadOnly()) { // except if only extract measurement or data
                publish("db is read only! task cannot be run");
                return false;
            }
            if (db.getExperiment()==null) {
                errors.add(new Pair(dbName, new Exception("DB: "+ dbName+ " not found")));
                printErrors();
                db = null;
                return false;
            } 
            if (structures!=null) checkArray(structures, db.getExperiment().getStructureCount(), "Invalid structure: ");
            if (positions!=null) checkArray(positions, db.getExperiment().getPositionCount(), "Invalid position: ");
            if (preProcess) { // compare pre processing to template
                if (positions==null) positions=Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
                PreProcessingChain template = db.getExperiment().getPreProcessingTemplate();
                for (int p : positions) {
                    PreProcessingChain pr = db.getExperiment().getPosition(p).getPreProcessingChain();
                    if (!template.sameContent(pr)) publish("Warning: Position: "+db.getExperiment().getPosition(p).getName()+": pre-processing chain differs from template");
                }
            }
            // check files
            for (Pair<String, int[]> e : extractMeasurementDir) {
                String exDir = e.key==null? db.getDir() : e.key;
                File f= new File(exDir);
                if (!f.exists()) errors.add(new Pair(dbName, new Exception("File: "+ exDir+ " not found")));
                else if (!f.isDirectory()) errors.add(new Pair(dbName, new Exception("File: "+ exDir+ " is not a directory")));
                else if (e.value!=null) checkArray(e.value, db.getExperiment().getStructureCount(), "Extract structure for dir: "+e.value+": Invalid structure: ");
            }
            if (!measurements && !preProcess && !segmentAndTrack && ! trackOnly && extractMeasurementDir.isEmpty() &&!generateTrackImages && !exportData) errors.add(new Pair(dbName, new Exception("No action to run!")));
            printErrors();
            logger.info("task : {}, isValid: {}", dbName, errors.isEmpty());
            db.clearCache(); // unlock if (unlock) 
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
            if (this.exportObjects || this.exportPreProcessedImages || this.exportTrackImages) count+=positions.size();
            db.clearCache(); // avoid lock issues
            return count;
        }
        public void setSubtaskNumber(int[] taskCounter) {
            this.taskCounter=taskCounter;
        }
        public void runTask() {
            if (ui!=null) ui.setRunning(true);
            publish("Run task: "+this.toString());
            initDB();
            db.clearCache();
            db.getExperiment(); // lock directly after
            ImageWindowManagerFactory.getImageManager().flush();
            publishMemoryUsage("Before processing");
            if (positions==null) positions=Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
            if (structures==null) structures = ArrayUtil.generateIntegerArray(db.getExperiment().getStructureCount());
            
            boolean needToDeleteObjects = preProcess || segmentAndTrack;
            boolean deleteAll =  needToDeleteObjects && structures.length==db.getExperiment().getStructureCount() && positions.size()==db.getExperiment().getPositionCount();
            if (deleteAll) {
                publish("deleting objects...");
                db.deleteAllObjects();
            }
            boolean deleteAllField = needToDeleteObjects && structures.length==db.getExperiment().getStructureCount() && !deleteAll;
            logger.info("Run task: db: {} preProcess: {}, segmentAndTrack: {}, trackOnly: {}, runMeasurements: {}, need to delete objects: {}, delete all: {}, delete all by field: {}", dbName, preProcess, segmentAndTrack, trackOnly, measurements, needToDeleteObjects, deleteAll, deleteAllField);
            
            if (this.taskCounter==null) this.taskCounter = new int[]{0, this.countSubtasks()};
            publish("number of subtasks: "+countSubtasks());
            for (int pIdx : positions) {
                String position = db.getExperiment().getPosition(pIdx).getName();
                try {
                    run(position, deleteAllField);
                } catch (MultipleException e) {
                    errors.addAll(e.getExceptions());
                } catch (Exception e) {
                    errors.add(new Pair("Error while processing: db"+db.getDBName()+" pos:"+position, e));
                }
            }
            
            for (Pair<String, int[]> e  : this.extractMeasurementDir) extractMeasurements(e.key==null?db.getDir():e.key, e.value);
            if (exportData) exportData();
            db.clearCache();
            //db.getExperiment();
            //db=null;
        }
    private void run(String position, boolean deleteAllField) throws Exception {
        publish("Position: "+position);
        if (deleteAllField) db.getDao(position).deleteAllObjects();
        if (preProcess) {
            publish("Pre-Processing: DB: "+dbName+", Position: "+position);
            logger.info("Pre-Processing: DB: {}, Position: {}", dbName, position);
            try {
                Processor.preProcessImages(db.getExperiment().getPosition(position), db.getDao(position), true, preProcess, this);
            } catch (Exception e) {
                errors.add(new Pair(position, e));
            }
            db.getExperiment().getPosition(position).flushImages(true, false);
            incrementProgress();
        }
        //publishMemoryUsage("After PreProcessing:");
        if (segmentAndTrack || trackOnly) {
            logger.info("Processing: DB: {}, Position: {}", dbName, position);
            for (int s : structures) { // TODO take code from processor
                publish("Processing structure: "+s);
                Processor.processAndTrackStructures(db.getDao(position), true, trackOnly, s);
                incrementProgress();
                if (generateTrackImages && !db.getExperiment().getAllDirectChildStructures(s).isEmpty()) {
                    publish("Generating Track Images for Structure: "+s);
                    Processor.generateTrackImages(db.getDao(position), s, this);
                    incrementProgress();
                }
            }
            //publishMemoryUsage("After Processing:");
        } else if (generateTrackImages) {
            publish("Generating Track Images...");
            // generate track images for all selected structure that has direct children
            for (int s : structures) {
                if (db.getExperiment().getAllDirectChildStructures(s).isEmpty()) continue;
                Processor.generateTrackImages(db.getDao(position), s, this);
                incrementProgress();
            }
            //publishMemoryUsage("After Generate Track Images:");
        }
        
        if (measurements) {
            publish("Measurements...");
            logger.info("Measurements: DB: {}, Field: {}", dbName, position);
            db.getDao(position).deleteAllMeasurements();
            Processor.performMeasurements(db.getDao(position), this);
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
    public void publishMemoryUsage(String message) {
        publish(message+Utils.getMemoryUsage());
    }
    public void extractMeasurements(String dir, int[] structures) {
        if (structures==null) structures = ArrayUtil.generateIntegerArray(db.getExperiment().getStructureCount());
        String file = dir+File.separator+db.getDBName()+Utils.toStringArray(structures, "_", "", "_")+".csv";
        publish("extracting measurements from structures: "+Utils.toStringArray(structures));
        publish("measurements will be extracted to: "+ file);
        Map<Integer, String[]> keys = db.getExperiment().getAllMeasurementNamesByStructureIdx(MeasurementKeyObject.class, structures);
        logger.debug("keys: {}", keys);
        MeasurementExtractor.extractMeasurementObjects(db, file, getPositions(), keys);
        incrementProgress();
    }
    public void exportData() {
        try {
            String file = db.getDir()+File.separator+db.getDBName()+"_dump.zip";
            ZipWriter w = new ZipWriter(file);
            if (exportObjects || exportPreProcessedImages || exportTrackImages) {
                ImportExportJSON.exportPositions(w, db, exportObjects, exportPreProcessedImages, exportTrackImages , getPositions(), this);
            }
            if (exportConfig) ImportExportJSON.exportConfig(w, db);
            if (exportSelections) ImportExportJSON.exportSelections(w, db);
            w.close();
        } catch (Exception e) {
            publish("Error while dumping");
            this.errors.add(new Pair(this.dbName, e));
        }
    }
    private List<String> getPositions() {
        if (positions==null) positions=Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
        List<String> res = new ArrayList<>(positions.size());
        for (int i : positions) res.add(db.getExperiment().getPosition(i).getName());
        return res;
    }
    @Override public String toString() {
        String res =  "db: "+dbName+"/dir:"+dir;
        if (preProcess) res+="/preProcess/";
        if (segmentAndTrack) res+="/segmentAndTrack/";
        else if (trackOnly) res+="/trackOnly/";
        if (measurements) res+="/measurements/";
        if (structures!=null) res+="/structures:"+ArrayUtils.toString(structures)+"/";
        if (positions!=null) res+="/positions:"+ArrayUtils.toString(positions)+"/";
        if (!extractMeasurementDir.isEmpty()) {
            res+= "/Extract: ";
            for (Pair<String, int[]> p : this.extractMeasurementDir) res+=(p.key==null?dir:p.key)+ "="+ArrayUtils.toString(p.value);
            res+="/";
        }
        if (exportData) {
            if (exportPreProcessedImages) res+="/ExportPPImages/";
            if (exportTrackImages) res+="/ExportTrackImages/";
            if (exportObjects) res+="/ExportObjects/";
            if (exportConfig) res+="/ExportConfig/";
            if (exportSelections) res+="/ExportSelection/";
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
        if (ui!=null) for (String s : strings) ui.setMessage(s);
        for (String s : strings) logger.info(s);
        
    }
    private static boolean toPrint(String stackTraceElement) {
        return true;
        //return !stackTraceElement.startsWith("java.util.concurrent")&&!stackTraceElement.startsWith("utils.ThreadRunner")&&!stackTraceElement.startsWith("java.lang.Thread")&&!stackTraceElement.startsWith("core.Processor.lambda")&&!stackTraceElement.startsWith("plugins.plugins.processingScheme");
    }
    @Override 
    public void done() {
        //logger.debug("EXECUTING DONE FOR : {}", this.toJSON().toJSONString());
        this.publish("Job done.");
        unrollMultipleExceptions();
        publishErrors();
        this.printErrors();
        this.publish("------------------");
        if (ui!=null) ui.setRunning(false);
    }
    private void unrollMultipleExceptions() {
        // check for multiple exceptions and unroll them
        List<Pair<String, Exception>> errorsToAdd = new ArrayList<>();
        Iterator<Pair<String, Exception>> it = errors.iterator();
        while(it.hasNext()) {
            Pair<String, Exception> e = it.next();
            if (e.value instanceof MultipleException) {
                it.remove();
                errorsToAdd.addAll(((MultipleException)e.value).getExceptions());
            }
        }
        this.errors.addAll(errorsToAdd);
    }
    public void publishErrors() {
        
        this.publish("Errors: "+this.errors.size()+ " For JOB: "+this.toString());
        for (Pair<String, Exception> e : errors) {
            publish("Error @"+e.key+(e.value==null?"null":e.value.toString()));
            for (StackTraceElement s : e.value.getStackTrace()) {
                String ss = s.toString();
                if (toPrint(ss)) publish(s.toString());
            }
        }
    }
    // Progress Callback
    @Override
    public void incrementTaskNumber(int subtask) {
        if (taskCounter!=null) this.taskCounter[1]+=subtask;
    }

    @Override
    public void log(String message) {
        publish(message);
    }

    public static void executeTasks(List<Task> tasks, UserInterface ui, Runnable... endOfWork) {
        int totalSubtasks = 0;
        for (Task t : tasks) {
            logger.debug("checking task: {}", t);
            if (!t.isValid()) {
                if (ui!=null) ui.setMessage("Invalid task: "+t.toString());
                return;
            } 
            t.setUI(ui);
            totalSubtasks+=t.countSubtasks();
            logger.debug("check ok: current task number: {}");
        }
        if (ui!=null) ui.setMessage("Total subTasks: "+totalSubtasks);
        int[] taskCounter = new int[]{0, totalSubtasks};
        for (Task t : tasks) t.setSubtaskNumber(taskCounter);
        DefaultWorker.execute(i -> {
            tasks.get(i).initDB();
            Consumer<LogUserInterface> setLF = l->{if (l.getLogFile()==null) l.setLogFile(tasks.get(i).getDir()+File.separator+"Log.txt");};
            Consumer<LogUserInterface> unsetLF = l->{l.setLogFile(null);};
            if (ui instanceof MultiUserInterface) ((MultiUserInterface)ui).applyToLogUserInterfaces(setLF);
            else if (ui instanceof LogUserInterface) setLF.accept((LogUserInterface)ui);
            tasks.get(i).runTask();
            if (tasks.get(i).db!=null) tasks.get(i).db.clearCache(); // unlock
            tasks.get(i).db=null;
            if (ui instanceof MultiUserInterface) ((MultiUserInterface)ui).applyToLogUserInterfaces(unsetLF);
            else if (ui instanceof LogUserInterface) unsetLF.accept((LogUserInterface)ui);
            return "";
        }, tasks.size()).setEndOfWork(
                ()->{for (Task t : tasks) t.done(); for (Runnable r : endOfWork) r.run();});
    }
    public static void executeTask(Task t, UserInterface ui, Runnable... endOfWork) {
        executeTasks(new ArrayList<Task>(1){{add(t);}}, ui, endOfWork);
    }
}