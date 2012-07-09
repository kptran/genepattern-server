package org.genepattern.server.plugin;

import static org.genepattern.util.GPConstants.COMMAND_LINE;
import static org.genepattern.util.GPConstants.DEFAULT_PATCH_URL;
import static org.genepattern.util.GPConstants.INSTALLED_PATCH_LSIDS;
import static org.genepattern.util.GPConstants.MANIFEST_FILENAME;
import static org.genepattern.util.GPConstants.PATCH_ERROR_EXIT_VALUE;
import static org.genepattern.util.GPConstants.PATCH_SUCCESS_EXIT_VALUE;
import static org.genepattern.util.GPConstants.REQUIRED_PATCH_LSIDS;
import static org.genepattern.util.GPConstants.REQUIRED_PATCH_URLS;
import static org.genepattern.util.GPConstants.UTF8;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.genepattern.server.executor.JobDispatchException;
import org.genepattern.server.genepattern.CommandLineParser;
import org.genepattern.server.genepattern.GenePatternAnalysisTask;
import org.genepattern.server.webservice.server.Status;
import org.genepattern.util.LSID;
import org.genepattern.webservice.ParameterInfo;
import org.genepattern.webservice.TaskInfo;
import org.genepattern.webservice.TaskInfoAttributes;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Duplicate patch installation functions from GenePatternAnalysisTask class, circa GP 3.3.3.
 * 
 * @author pcarr
 */
public class PluginManagerLegacy {
    public static Logger log = Logger.getLogger(PluginManagerLegacy.class);
    // check that each patch listed in the TaskInfoAttributes for this task is installed.
    // if not, download and install it.
    // For any problems, throw an exception
    public static boolean validatePatches(TaskInfo taskInfo, Status taskIntegrator) throws MalformedURLException, JobDispatchException {
        TaskInfoAttributes tia = taskInfo.giveTaskInfoAttributes();
        String requiredPatchLSID = tia.get(REQUIRED_PATCH_LSIDS);
        // no patches required?
        if (requiredPatchLSID == null || requiredPatchLSID.length() == 0) {
            return true;
        }

        // some patches required, check which are already installed
        String[] requiredPatchLSIDs = requiredPatchLSID.split(",");
        String requiredPatchURL = tia.get(REQUIRED_PATCH_URLS);
        String[] patchURLs = (requiredPatchURL != null && requiredPatchURL.length() > 0 ? requiredPatchURL.split(",")
                : new String[requiredPatchLSIDs.length]);
        if (patchURLs != null && patchURLs.length != requiredPatchLSIDs.length) {
            throw new JobDispatchException(taskInfo.getName() + " has " + requiredPatchLSIDs.length + " patch LSIDs but " + patchURLs.length + " URLs");
        }
        eachRequiredPatch: for (int requiredPatchNum = 0; requiredPatchNum < requiredPatchLSIDs.length; requiredPatchNum++) {
            String installedPatches = System.getProperty(INSTALLED_PATCH_LSIDS);
            String[] installedPatchLSIDs = new String[0];
            if (installedPatches != null) {
                installedPatchLSIDs = installedPatches.split(",");
            }
            requiredPatchLSID = requiredPatchLSIDs[requiredPatchNum];
            LSID requiredLSID = new LSID(requiredPatchLSID);
            log.debug("Checking whether " + requiredPatchLSID + " is already installed...");
            for (int p = 0; p < installedPatchLSIDs.length; p++) {
                LSID installedLSID = new LSID(installedPatchLSIDs[p]);
                if (installedLSID.isEquivalent(requiredLSID)) {
                    // there are installed patches, and there is an LSID match to this one
                    log.info(requiredLSID.toString() + " is already installed");
                    continue eachRequiredPatch;
                }
            }

            // download and install this patch
            installPatch(requiredPatchLSIDs[requiredPatchNum], patchURLs[requiredPatchNum], taskIntegrator);
        } 
        // end of loop for each patch LSID for the task
        return true;
    }

    public static void installPatch(String requiredPatchLSID, String requiredPatchURL) throws Exception {
        String installedPatches = System.getProperty(INSTALLED_PATCH_LSIDS);
        String[] installedPatchLSIDs = new String[0];
        if (installedPatches != null) {
            installedPatchLSIDs = installedPatches.split(",");
        }
        LSID requiredLSID = new LSID(requiredPatchLSID);
        log.debug("Checking whether " + requiredPatchLSID + " is already installed...");
        for (int p = 0; p < installedPatchLSIDs.length; p++) {
            LSID installedLSID = new LSID(installedPatchLSIDs[p]);
            if (installedLSID.isEquivalent(requiredLSID)) {
                // there are installed patches, and there is an LSID match to
                // this one
                log.info(requiredLSID.toString() + " is already installed");
                return;
            }
        }
        installPatch(requiredPatchLSID, requiredPatchURL, null);
    }

        /**
     * Install a specific patch, downloading a zip file with a manifest containing a command line, 
     * running that command line after substitutions, and recording the result 
     * in the genepattern.properties patch registry
     */
    private static void installPatch(String requiredPatchLSID, String requiredPatchURL, Status taskIntegrator) throws JobDispatchException {
        LSID patchLSID = null;
        try {
            patchLSID = new LSID(requiredPatchLSID);
        }
        catch (MalformedURLException e) {
            throw new JobDispatchException("Error installing patch, requiredPatchLSID="+requiredPatchLSID, e);
        }
        
        boolean wasNullURL = (requiredPatchURL == null || requiredPatchURL.length() == 0);
        if (wasNullURL) {
            requiredPatchURL = System.getProperty(DEFAULT_PATCH_URL);
        }
        HashMap hmProps = new HashMap();
        try {
            if (wasNullURL) {
                taskIntegrator.statusMessage("Fetching patch information from " + requiredPatchURL);
                URL url = new URL(requiredPatchURL);
                URLConnection connection = url.openConnection();
                connection.setUseCaches(false);
                if (connection instanceof HttpURLConnection) {
                    connection.setDoOutput(true);
                    PrintWriter pw = new PrintWriter(connection.getOutputStream());
                    String[] patchQualifiers = System.getProperty("patchQualifiers", "").split(",");
                    pw.print("patch");
                    pw.print("=");
                    pw.print(URLEncoder.encode(requiredPatchLSID, UTF8));
                    for (int p = 0; p < patchQualifiers.length; p++) {
                        pw.print("&");
                        pw.print(URLEncoder.encode(patchQualifiers[p], UTF8));
                        pw.print("=");
                        pw.print(URLEncoder.encode(System.getProperty(patchQualifiers[p], ""), UTF8));
                    }
                    pw.close();
                }
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(connection.getInputStream());
                Element root = doc.getDocumentElement();
                processNode(root, hmProps);
                String result = (String) hmProps.get("result");
                if (!result.equals("Success")) {
                    throw new JobDispatchException("Error requesting patch: " + result + " in request for " + requiredPatchURL);
                }
                requiredPatchURL = (String) hmProps.get("site_module.url");
            }
        }
        catch (Exception e) {
            throw new JobDispatchException(e);
        }
        if (taskIntegrator != null) {
            taskIntegrator.statusMessage("Downloading required patch from " + requiredPatchURL + "...");
        }
        String zipFilename = null;
        try {
            zipFilename = downloadPatch(requiredPatchURL, taskIntegrator, (String) hmProps.get("site_module.zipfilesize"));
        }
        catch (IOException e) {
            throw new JobDispatchException(e);
        }
        String patchName = patchLSID.getAuthority() + "." + patchLSID.getNamespace() + "." + patchLSID.getIdentifier() + "." + patchLSID.getVersion();
        File patchDirectory = new File(System.getProperty("patches"), patchName);
        if (taskIntegrator != null) {
            taskIntegrator.statusMessage("Installing patch from " + patchDirectory.getPath() + ".");
        }
        try {
            explodePatch(zipFilename, patchDirectory, taskIntegrator);
        }
        catch (IOException e) {
            throw new JobDispatchException(e);
        }
        new File(zipFilename).delete();

        // entire zip file has been exploded, now load the manifest, get the command line, and execute it
        Properties props = null;
        try {
            props = loadManifest(patchDirectory);
        }
        catch (IOException e) {
            throw new JobDispatchException(e);
        }
        String nomDePatch = props.getProperty("name");
        if (taskIntegrator != null) {
            taskIntegrator.statusMessage("Running " + nomDePatch + " Installer.");
        }
        String exitValue = null; 
        
        String cmdLine = props.getProperty(COMMAND_LINE);
        if (cmdLine == null || cmdLine.length() == 0) {
            throw new JobDispatchException("No command line defined in " + MANIFEST_FILENAME);
        }
        Properties systemProps = new Properties();
        //copy into from System.getProperties, TODO: should use new configuration system
        for(Object keyObj : System.getProperties().keySet()) {
            String key = keyObj.toString();
            String val = System.getProperty(key);
            systemProps.setProperty(key, val);
        }
        ParameterInfo[] formalParameters = new ParameterInfo[0];
        List<String> cmdLineArgs = CommandLineParser.createCmdLine(cmdLine, systemProps, formalParameters);        
        try {
            String[] cmdLineArray = new String[0];
            cmdLineArray = cmdLineArgs.toArray(cmdLineArray);
            exitValue = "" + executePatch(cmdLineArray, patchDirectory, taskIntegrator);
        }
        catch (IOException e) {
            throw new JobDispatchException(e);
        }
        catch (InterruptedException e2) {
            Thread.currentThread().interrupt();
            throw new JobDispatchException(e2);
        }
        if (taskIntegrator != null) {
            taskIntegrator.statusMessage("Patch installed, exit code " + exitValue);
        }
        String goodExitValue = props.getProperty(PATCH_SUCCESS_EXIT_VALUE, "0");
        String failureExitValue = props.getProperty(PATCH_ERROR_EXIT_VALUE, "");
        if (exitValue.equals(goodExitValue) || !exitValue.equals(failureExitValue)) {
            try {
                recordPatch(requiredPatchLSID);
            }
            catch (IOException e) {
                throw new JobDispatchException(e);
            }
            if (taskIntegrator != null) {
                taskIntegrator.statusMessage("Patch LSID recorded");
            }

            // keep the manifest file around for future reference
            if (!new File(patchDirectory, MANIFEST_FILENAME).exists()) {
                try {
                    explodePatch(zipFilename, patchDirectory, null, MANIFEST_FILENAME);
                }
                catch (IOException e) {
                    throw new JobDispatchException(e);
                }
                if (props.getProperty(REQUIRED_PATCH_URLS, null) == null) {
                    try {
                        File f = new File(patchDirectory, MANIFEST_FILENAME);
                        Properties mprops = new Properties();
                        mprops.load(new FileInputStream(f));
                        mprops.setProperty(REQUIRED_PATCH_URLS, requiredPatchURL);
                        mprops.store(new FileOutputStream(f), "added required patch");
                    } 
                    catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }
        } 
        else {
            if (taskIntegrator != null) {
                taskIntegrator.statusMessage("Deleting patch directory after installation failure");
            }
            // delete patch directory
            File[] old = patchDirectory.listFiles();
            for (int i = 0; old != null && i < old.length; i++) {
                old[i].delete();
            }
            patchDirectory.delete();
            throw new JobDispatchException("Could not install required patch: " + props.get("name") + "  " + props.get("LSID"));
        }
    }

    // download the patch zip file from a URL
    private static String downloadPatch(String url, Status taskIntegrator, String contentLength) throws IOException {
        try {
            long len = -1;
            try {
                len = Long.parseLong(contentLength);
            } 
            catch (NullPointerException npe) {
                // ignore
            } 
            catch (NumberFormatException nfe) {
                // ignore
            }
            return GenePatternAnalysisTask.downloadTask(url, taskIntegrator, len, false);
        } 
        catch (IOException ioe) {
            if (ioe.getCause() != null) {
                ioe = (IOException) ioe.getCause();
            }
            throw new IOException(ioe.toString() + " while downloading " + url);
        }
    }

    // unzip the patch files into their own directory
    private static void explodePatch(String zipFilename, File patchDirectory, Status taskIntegrator) throws IOException {
        explodePatch(zipFilename, patchDirectory, taskIntegrator, null);
    }

    // unzip the patch files into their own directory
    private static void explodePatch(String zipFilename, File patchDirectory, Status taskIntegrator, String zipEntryName)
    throws IOException 
    {
        ZipFile zipFile = new ZipFile(zipFilename);
        InputStream is = null;
        patchDirectory.mkdirs();
        if (zipEntryName == null) {
            // clean out existing directory
            File[] old = patchDirectory.listFiles();
            for (int i = 0; old != null && i < old.length; i++) {
                old[i].delete();
            }
        }
        for (Enumeration eEntries = zipFile.entries(); eEntries.hasMoreElements();) {
            ZipEntry zipEntry = (ZipEntry) eEntries.nextElement();
            if (zipEntryName != null && !zipEntryName.equals(zipEntry.getName())) {
                continue;
            }
            File outFile = new File(patchDirectory, zipEntry.getName());
            if (zipEntry.isDirectory()) {
                if (taskIntegrator != null) {
                    taskIntegrator.statusMessage("Creating subdirectory " + outFile.getAbsolutePath());
                }
                outFile.mkdirs();
                continue;
            }
            is = zipFile.getInputStream(zipEntry);
            OutputStream os = new FileOutputStream(outFile);
            long fileLength = zipEntry.getSize();
            long numRead = 0;
            byte[] buf = new byte[100000];
            int i;
            while ((i = is.read(buf, 0, buf.length)) > 0) {
                os.write(buf, 0, i);
                numRead += i;
            }
            os.close();
            os = null;
            if (numRead != fileLength) {
                throw new IOException("only read " + numRead + " of " + fileLength + " bytes in " + zipFile.getName() + "'s " + zipEntry.getName());
            }
            is.close();
        } // end of loop for each file in zip file
        zipFile.close();
    }

    // load the patch manifest file into a Properties object
    private static Properties loadManifest(File patchDirectory) throws IOException {
        File manifestFile = new File(patchDirectory, MANIFEST_FILENAME);
        if (!manifestFile.exists()) {
            throw new IOException(MANIFEST_FILENAME + " missing from patch " + patchDirectory.getName());
        }
        Properties props = new Properties();
        FileInputStream manifest = new FileInputStream(manifestFile);
        props.load(manifest);
        manifest.close();
        return props;
    }

    /**
     * Run the patch command line in the patch directory, returning the exit code from the executable.
     */
    private static int executePatch(String[] commandLineArray, File patchDirectory, Status taskIntegrator) throws IOException, InterruptedException {
        // spawn the command
        Process process = Runtime.getRuntime().exec(commandLineArray, null, patchDirectory);

        // BUG: there is race condition during a tiny time window between the exec and the close
        // (the lines above and below this comment) during which it is possible for an application
        // to imagine that there might be useful input coming from stdin.
        // This seemed to be the case for Perl 5.0.1 on Wilkins, and might be a problem in
        // other applications as well.
        process.getOutputStream().close(); 
        // there is no stdin to feed to the program. So if it asks, let it see EOF!

        // create threads to read from the command's stdout and stderr streams
        Thread outputReader = (taskIntegrator != null) ? antStreamCopier(process.getInputStream(), taskIntegrator)
                : streamCopier(process.getInputStream(), System.out);
        Thread errorReader = (taskIntegrator != null) ? antStreamCopier(process.getErrorStream(), taskIntegrator) : streamCopier(
                process.getInputStream(), System.err);

        // drain the output and error streams
        outputReader.start();
        errorReader.start();

        // wait for all output
        outputReader.join();
        errorReader.join();

        // the process will be dead by now
        process.waitFor();
        int exitValue = process.exitValue();
        return exitValue;
    }

    // record the patch LSID in the genepattern.properties file
    private static synchronized void recordPatch(String patchLSID) throws IOException {
        // add this LSID to the installed patches repository
        String installedPatches = System.getProperty(INSTALLED_PATCH_LSIDS);
        if (installedPatches == null || installedPatches.length() == 0) {
            installedPatches = "";
        } 
        else {
            installedPatches = installedPatches + ",";
        }
        installedPatches = installedPatches + patchLSID;
        System.setProperty(INSTALLED_PATCH_LSIDS, installedPatches);
        Properties props = new Properties();
        props.load(new FileInputStream(new File(System.getProperty("resources"), "genepattern.properties")));

        // make sure any changes are properly set in the System props
        props.setProperty(INSTALLED_PATCH_LSIDS, installedPatches);
        props.store(new FileOutputStream(new File(System.getProperty("resources"), "genepattern.properties")), "added installed patch LSID");

        for (Iterator iter = props.keySet().iterator(); iter.hasNext();) {
            String k = (String) iter.next();
            String v = (String) props.get(k);
            System.setProperty(k, v);
        }
    }
    
    /**
     * // read the genepattern.properties file into a String (preserving comments!) public static String
     * readGenePatternProperties() throws IOException { File gpPropertiesFile = new
     * File(System.getProperty("resources"), "genepattern.properties"); return readPropertiesFile(gpPropertiesFile); }
     * // read the genepattern.properties file into a String (preserving comments!) protected static String
     * readPropertiesFile(File propertiesFile) throws IOException { FileReader fr = new FileReader(propertiesFile); char
     * buf[] = new char[(int)propertiesFile.length()]; int len = fr.read(buf, 0, buf.length); fr.close(); String
     * properties = new String(buf, 0, len); return properties; } // write a String as a genepattern.properties file
     * (preserving comments) public static void writeGenePatternProperties(String properties) throws IOException { File
     * gpPropertiesFile = new File(System.getProperty("resources"), "genepattern.properties");
     * writePropertiesFile(gpPropertiesFile, properties); }
     * <p/>
     * protected static void writePropertiesFile(File propertiesFile, String properties) throws IOException { FileWriter
     * fw = new FileWriter(propertiesFile, false); fw.write(properties); fw.close(); } // add or set the value of a
     * particular key in the String representation of a properties file public static String addProperty(String
     * properties, String key, String value) { int ipStart = properties.indexOf(key + "="); if (ipStart == -1) {
     * properties = properties + System.getProperty("line.separator") + key + "=" + value +
     * System.getProperty("line.separator"); } else { int ipEnd =
     * properties.indexOf(System.getProperty("line.separator"), ipStart); properties = properties.substring(0, ipStart +
     * key.length() + "=".length()) + value; if (ipEnd != -1) properties = properties + "," +
     * properties.substring(ipEnd); } return properties; }
     */

    protected static void processNode(Node node, HashMap hmProps) {
    if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element c_elt = (Element) node;
        String nodeValue = c_elt.getFirstChild().getNodeValue();
        log.debug("GPAT.processNode: adding " + c_elt.getTagName() + "=" + nodeValue);
        hmProps.put(c_elt.getTagName(), nodeValue);
        NamedNodeMap attributes = c_elt.getAttributes();
        if (attributes != null) {
        for (int i = 0; i < attributes.getLength(); i++) {
            String attrName = ((Attr) attributes.item(i)).getName();
            String attrValue = ((Attr) attributes.item(i)).getValue();
            log.debug("GPAT.processNode: adding " + c_elt.getTagName() + "." + attrName + "=" + attrValue);
            hmProps.put(c_elt.getTagName() + "." + attrName, attrValue);
        }
        }
    } else {
        log.debug("non-Element node: " + node.getNodeName() + "=" + node.getNodeValue());
    }
    NodeList childNodes = node.getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
        processNode(childNodes.item(i), hmProps);
    }
    }
    // copy an InputStream to a PrintStream until EOF
    public static Thread streamCopier(final InputStream is, final PrintStream ps) throws IOException {
    // create thread to read from the a process' output or error stream
    return new Thread(new Runnable() {
        public void run() {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = in.readLine()) != null) {
            ps.println(line);
            ps.flush();
            }
        } catch (IOException ioe) {
            System.err.println(ioe + " while reading from process stream");
        }
        }
    });
    }

    // copy an InputStream to a PrintStream until EOF
    public static Thread streamCopier(final InputStream is, final Status taskIntegrator) throws IOException {
    // create thread to read from the a process' output or error stream
    return new Thread(new Runnable() {
        public void run() {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = in.readLine()) != null) {
            if (taskIntegrator != null && line != null) {
                taskIntegrator.statusMessage(line);
            }
            }
        } catch (IOException ioe) {
            System.err.println(ioe + " while reading from process stream");
        }
        }
    });
    }

    // copy an InputStream to a PrintStream until EOF
    public static Thread antStreamCopier(final InputStream is, final Status taskIntegrator) throws IOException {
    // create thread to read from the a process' output or error stream
    return new Thread(new Runnable() {
        public void run() {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = in.readLine()) != null) {
            int idx = 0;
            if ((idx = line.indexOf("[echo]")) >= 0) {
                line = line.substring(idx + 6);
            }
            if (taskIntegrator != null && line != null) {
                taskIntegrator.statusMessage(line);
            }
            }
        } catch (IOException ioe) {
            System.err.println(ioe + " while reading from process stream");
        }
        }
    });
    }

}