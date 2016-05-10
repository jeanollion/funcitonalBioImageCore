/*
 * Copyright (C) 2016 jollion
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
package utils;

import boa.gui.PropertyUtils;
import boa.gui.DBUtil;
import dataStructure.objects.MeasurementsDAO;
import dataStructure.objects.MorphiumObjectDAO;
import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class CommandExecuter {
    public static final org.slf4j.Logger logger = LoggerFactory.getLogger(CommandExecuter.class);
    public static final boolean isWin, isMac, isLinux;
    private static boolean interactive = false;
    
    static {
        String osname = System.getProperty("os.name");
        isWin = osname.startsWith("Windows");
        isMac = !isWin && osname.startsWith("Mac");
        isLinux = osname.startsWith("Linux");
    }
    public static boolean restoreDB(String host, String dbName, String inputPath) {
        File f = new File(inputPath);
        if (!f.isDirectory()) {
            logger.warn("Import DB: need a directory containing Experiment file");
            return false;
        }
        if (f.listFiles(new FilenameFilter() {
                @Override public boolean accept(File dir, String name) {
                    return name.equals("Experiment.bson");
                }
            }).length==1) return restore(host, dbName, null, inputPath, false);
        else {
            List<File> subFiles= Arrays.asList(f.listFiles(new FilenameFilter() {
                @Override public boolean accept(File dir, String name) {
                    return name.endsWith(".json") && !name.endsWith("metadata.json") && !name.startsWith("system.indexes");
                }
            }));
            boolean hasXp = false;
            for (File subFile : subFiles) if (subFile.getName().equals("Experiment.json")) hasXp = true;
            logger.debug("import db in: {}, #files: {}, hasXp: {}", inputPath, subFiles.size(), hasXp);
            if (hasXp) {
                boolean processOK= true;
                for (File subFile : subFiles) processOK = processOK && restore(host, dbName, subFile.getName().substring(0, subFile.getName().length()-5), subFile.getAbsolutePath(), false);
            } else logger.warn("Folder: {} doesn't have Experiment.bson or Experiment.json file");
        }
        return false;
    }
    public static boolean restore(String host, String dbName, String collectionName, String inputPath, boolean drop) {
        if (inputPath==null) throw new IllegalArgumentException("Input path is null");
        if (dbName==null) throw new IllegalArgumentException("DBName is null");
        String mongoBinPath = PropertyUtils.get(PropertyUtils.MONGO_BIN_PATH);
        if (host==null) host = "localhost";
        if (collectionName==null) drop=false;
        
        String cName = "mongorestore";
        File f = new File(inputPath);
        if (!f.isDirectory()) {
            if (inputPath.endsWith(".json")) cName = "mongoimport";
        }
        else {
            if (f.listFiles(new FilenameFilter() {
                @Override public boolean accept(File dir, String name) {
                    return name.equals("Experiment.bson");
                }
            }).length==0) return false; // cannot import a directory -> need to restore
        }
        
        if (interactive){
            String command = cName + " --host "+host+" --db "+dbName+ (collectionName==null ? "" : " --collection "+collectionName) + (drop ? " --drop " : " ") + inputPath;
            command = arrangeCommand(command, mongoBinPath);
            logger.info("Will execute interactively dump command: {}", command);
            //return mongoBinPath.executeInteractiveProcess(command);
            return false;
        } else {
            String command = cName;
            command = arrangeCommand(command, mongoBinPath);
            ArrayList<String> commandArgs = new ArrayList<String>();
            commandArgs.add(command);
            commandArgs.add("--host");
            commandArgs.add(host);
            commandArgs.add("--db");
            commandArgs.add(dbName);
            if (collectionName!=null) {
                commandArgs.add("--collection");
                commandArgs.add(collectionName);
            }
            if(drop) commandArgs.add("--drop");
            commandArgs.add(inputPath);
            return execProcess(null, commandArgs);
        }
    }
    
    public static boolean dumpDB(String host, String dbName, String outputPath, boolean json) {
        if (!json) return dump(host, dbName, null, outputPath, false);
        else { // get all collections
            boolean processOk = true;
            for (String collectionName : DBUtil.listCollections(dbName, host)) processOk = processOk && dump(host, dbName, collectionName, outputPath, true);
            return processOk;
        }
    }
    
    public static boolean dump(String host, String dbName, String collectionName, String outputPath, boolean json) {
        if (outputPath==null) throw new IllegalArgumentException("Output path is null");
        if (dbName==null) throw new IllegalArgumentException("DBName is null");
        if (json && collectionName==null) throw new IllegalArgumentException("Export to JSON only on single collections");
        String mongoBinPath = PropertyUtils.get(PropertyUtils.MONGO_BIN_PATH);
        if (host==null) host = "localhost";
        String cName = json? "mongoexport" : "mongodump";
        if (json) {
            File out = new File(outputPath);
            if (out.isDirectory()) {
                if (!outputPath.endsWith(File.separator)) outputPath+=File.separator;
                outputPath+=collectionName+".json";
            }
        }
        if(interactive){
            String command = cName+" --host "+host+" --db "+dbName + (collectionName==null ? "" : " --collection "+collectionName) +" -o "+outputPath;
            command = arrangeCommand(command, mongoBinPath);
            logger.info("Will execute interactively dump command: {}", command);
            //return mongoBinPath.executeInteractiveProcess(command);
            return false;
        } else {
            String command = cName;
            command = arrangeCommand(command, mongoBinPath);
            ArrayList<String> commandArgs = new ArrayList<String>();
            commandArgs.add(command);
            commandArgs.add("--host");
            commandArgs.add(host);
            commandArgs.add("--db");
            commandArgs.add(dbName);
            if (collectionName!=null) {
                commandArgs.add("--collection");
                commandArgs.add(collectionName);
            }
            commandArgs.add("-o");
            commandArgs.add(outputPath);
            return execProcess(null, commandArgs);
        }
    }
    
    private static String arrangeCommand(String command, String binPath) {
        if (binPath==null || "".equals(binPath)) {
            if (isWin) return "./"+command;
            else return command;
        } else {
            if (!binPath.endsWith(File.separator)) binPath+=File.separator;
            return binPath+command;
        }
    }
    /*public static boolean execProcessInteractive(File directory, String command) {
        ArrayList<String> commandArgs = new ArrayList<String>();
        if (directory!=null) commandArgs.add(directory.getAbsolutePath());
        if(isWin) command = "\""+command+"\"";
        commandArgs.add(command);
        return executeCommandWithParameters("runTerminal" ,commandArgs , null);
    }
    public static boolean executeCommandWithParameters(String scriptName, ArrayList<String> scriptArgs, File directory){
        String prefix = "";
        if(isWin){
            scriptName = scriptName + ".bat";
        }
        if(isLinux){
            scriptName = scriptName + ".sh";
            prefix = "./";
        }
        if(isMac){
            scriptName = scriptName + ".command";
            prefix = "./";
        }
        
        //if(directory==null) directory = new File(getBatchPath());
        ArrayList<String> commandArgs = new ArrayList<String>();
        commandArgs.add(prefix+scriptName);
        commandArgs.addAll(scriptArgs);
        chmodBatchScript(scriptName, ext, directory);
        return execProcess(directory, commandArgs);
    }
    
    public static void chmodBatchScript(String scriptName, String ext, File directory){
        File file = new File(directory.getAbsolutePath()+File.separator+scriptName+ext);
        file.setExecutable(true);
    }*/
    
    public static boolean execProcess(File directory, ArrayList<String> commandArgs) {
        ArrayList<String> processArgs= new ArrayList<String>();
        if(isWin){
            processArgs.add("cmd.exe");
            processArgs.add("/c");
            processArgs.addAll(commandArgs);
        } else {
            processArgs = commandArgs;
        }
        final ProcessBuilder pb = new ProcessBuilder(processArgs);
        if (directory!=null) pb.directory(directory);
        final boolean[] processOk = new boolean[]{false};
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    logger.info("Executing command : {}, in {} dir: {}", pb.command(), pb.directory()!=null && pb.directory().isDirectory() ? "existing" : "non existing", pb.directory());
                    
                    Process p = pb.start();
                    try {
                        p.waitFor();
                    } catch (InterruptedException ex) {
                        logger.error("Error while executing commend", ex);
                        processOk[0] = false;
                    }
                    OutputStream out = p.getOutputStream();
                    out.close();
                    BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line = null;
                    while ((line = in.readLine()) != null) logger.info(line);
                    line = null;
                    BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                    while ((line = error.readLine()) != null) logger.info(line);
                    try {
                        int v = p.exitValue();
                        if(v==0) processOk[0] = true;
                        else processOk[0] = false;
                    }
                    catch(IllegalThreadStateException e) {
                        logger.error("Error while executing commend", e);
                    }
                } catch (IOException ex) {
                    logger.error("Error while executing commend", ex);
                }
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException ex) {
            logger.error("Error while executing command", ex);
            return false;
        }
        return processOk[0];
    }
    public static Map<String, String> getObjectDumpedCollections(File... files) {
        Map<String, String> map = new HashMap<String, String>(files.length);
        for (File file : files) {
            if (file.isDirectory()) getObjectDumpedCollectionsInDirectory(file, map);
            else if (isExportedObjectCollection(file.getName())) map.put(trimExportedObjectCollectionFileName(file.getName()), file.getAbsolutePath());
        }
        return map;
    }
    public static Map<String, String> getObjectDumpedCollectionsInDirectory(File directory, Map<String, String> map) {
        File[] files = directory.listFiles(new FilenameFilter() {
            @Override public boolean accept(File file, String string) {
                return isExportedObjectCollection(string);
            }
        });
        if (map==null) map = new HashMap<String, String>(files.length);
        for (File file : files) map.put(trimExportedObjectCollectionFileName(file.getName()), file.getAbsolutePath());
        return map;
    }
    private static boolean isExportedObjectCollection(String string) {
        return ( (string.startsWith(MorphiumObjectDAO.getCollectionName("")) || string.startsWith(MeasurementsDAO.getCollectionName(""))) && (string.endsWith(".bson") || string.endsWith(".json")) &&  !string.endsWith(".metadata.json"));
    }
    private static String trimExportedObjectCollectionFileName(String fieldName) {
        if (fieldName.startsWith(MorphiumObjectDAO.getCollectionName(""))) return fieldName.substring(MorphiumObjectDAO.getCollectionName("").length(), fieldName.length()-5);
        else return fieldName.substring(MeasurementsDAO.getCollectionName("").length(), fieldName.length()-5);
    }
}