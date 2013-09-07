package org.genepattern.server.job.input;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.genepattern.server.job.input.cache.CachedFtpFile;
import org.genepattern.server.job.input.cache.DownloadException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;


/**
 * jUnit tests for downloading an input file from an external URL.
 * 
 * @author pcarr
 *
 */
public class TestFileDownloader {
    //smaller file (118811 KB)
    private static final String aThalianaUrl="ftp://ftp.broadinstitute.org/pub/genepattern/rna_seq/whole_genomes/Arabidopsis_thaliana_Ensembl_TAIR10.fa";
    private static final String aThaliana_expectedName="Arabidopsis_thaliana_Ensembl_TAIR10.fa";
    private static final long aThaliana_expectedLength=121662238L;
    
    //large file (3073525 KB, ~2.9G)
    private static final String largeFile="ftp://ftp.broadinstitute.org/pub/genepattern/rna_seq/whole_genomes/Homo_sapiens_Ensembl_GRCh37.fa";
    private static final String largeFile_expectedName="Homo_sapiens_Ensembl_GRCh37.fa";
    private static final long largeFile_expectedLength=118820495L;
    
    //tiny file on gpftp site
    final String smallFileUrl="ftp://gpftp.broadinstitute.org/chet/dummy_file_2.txt";
    final long smallFile_expectedLength=13L;
    final String smallFile_expectedName="dummy_file_2.txt";

    //smaller file on gpftp site (116036 KB)
    //final String gpftpFile="ftp://gpftp.broadinstitute.org/rna_seq/referenceAnnotation/gtf/Homo_sapiens_UCSC_hg18.gtf";
    //long expectedLength=118820495L;

    /*
     * this class creates tmp dirs, but cleans up after itself. 
     */
    private static final boolean cleanupTmpDirs=true;
    private static List<File> cleanupDirs;
    private static final File newTmpDir() {
        File tmpDir=Files.createTempDir();
        if (!tmpDir.exists()) {
            Assert.fail("tmpDir exists!");
        }
        if (tmpDir.list().length>0) {
            Assert.fail("tmpDir already contains "+tmpDir.list().length+" files");
        }
        cleanupDirs.add(tmpDir);
        return tmpDir;
    }
    
    private static final void saveToFile(final File file, final String contents) throws IOException {
        FileWriter fw=null;
        BufferedWriter out=null;
        try {
            fw = new FileWriter(file);
            out = new BufferedWriter(new FileWriter(file));
            out.write(contents);
        } 
        finally {
            if (out != null) {
                out.close();
            }
            else if (fw != null) {
                fw.close();
            }
        }
    }
    
    @BeforeClass
    public static void initTest() {
        cleanupDirs=new ArrayList<File>();
    }
    
    @AfterClass
    public static void cleanup() throws Exception {
        if (cleanupTmpDirs && cleanupDirs != null) {
            for(final File cleanupDir : cleanupDirs) {
                boolean deleted=FileUtils.deleteQuietly(cleanupDir);
                if (!deleted) {
                    throw new Exception("tmpDir not deleted: "+cleanupDir.getAbsolutePath());
                }
            }
        }
    }

    private void cancellationTest(boolean expected, final File toFile, final Callable<File> downloader) throws MalformedURLException, InterruptedException {
        final int sleep_interval_ms=4000;
        final ExecutorService service=Executors.newSingleThreadExecutor();
        final Future<File> future=service.submit( downloader );

        Thread.sleep(sleep_interval_ms);
        final boolean mayInterruptIfRunning=true;
        final boolean isCancelled=future.cancel(mayInterruptIfRunning);
        if (!isCancelled) {
            Assert.fail("future.cancel returned false");
        }
        
        //does cancel have any effect?
        long ts01=toFile.lastModified();
        Thread.sleep(sleep_interval_ms);
        long ts02=toFile.lastModified();
        boolean cancelWorked=ts02==ts01;

        //does shutdown have any effect? Note: always shut down, even if cancel worked
        service.shutdownNow();
        if (!cancelWorked) {
            Thread.sleep(sleep_interval_ms);
            long ts03=toFile.lastModified();
            Thread.sleep(sleep_interval_ms);
            long ts04=toFile.lastModified();
            boolean shutdownWorked=ts04==ts03;
            
            if (expected) {
                Assert.assertTrue( "file transfer still going, cancelWorked="+cancelWorked+", shutdownWorked="+shutdownWorked, cancelWorked || shutdownWorked);
            }
            else {
                Assert.assertFalse("Expecting cancellation to fail", cancelWorked || shutdownWorked);
            }
        }
    }
    
//    @Test
//    public void testDownloadFromBroadFtp_fileAlreadyExists() throws MalformedURLException, DownloadException {
//        final File userUploadDir=newTmpDir();
//        File userRootDir=new File(userUploadDir, "users");
//        boolean success=userRootDir.mkdirs();
//        if (!success) {
//            Assert.fail("userRootDir already exists or could not be created: "+userRootDir);
//        }
//        System.setProperty("user.root.dir", userUploadDir.getAbsolutePath());
//        //File f=new File(userRootDir, ".cache/uploads/cache");
//
//        CachedFtpFile cachedFile=new CachedFtpFile("ftp://gpftp.broadinstitute.org/chet/dummy_file_3.txt");
//        
//        try {
//            GpFilePath tempPath=cachedFile.getTempPath(cachedFile.getLocalPath());
//            success=tempPath.getServerFile().getParentFile().mkdirs();
//            saveToFile(tempPath.getServerFile(), "Blow away partial download");
//        }
//        catch (IOException e) {
//            Assert.fail("error initializing partial download file: "+cachedFile.getLocalPath().getServerFile());
//        }
//        GpFilePath downloaded=cachedFile.download();
//        
//    }

    @Test
    public void testDownloadFromBroadFtp() throws MalformedURLException, InterruptedException, DownloadException {
        final URL fromUrl=new URL(smallFileUrl);
        final File tmpDir=newTmpDir();
        final File toFile=new File(tmpDir, fromUrl.getFile());
        try {
            CachedFtpFile cachedFtpFile = CachedFtpFile.Factory.instance().newStdJava6Impl(fromUrl.toExternalForm());
            cachedFtpFile.downloadFile(fromUrl, toFile);
        }
        catch (IOException e) {
            Assert.fail("IOException downloading file: "+e.getLocalizedMessage());
        }
        
        //size check
        Assert.assertEquals("name check", smallFile_expectedName, toFile.getName());
        Assert.assertEquals("size check", smallFile_expectedLength, toFile.length());
    }
    
    @Test
    public void testDownloadFromGpFtp() throws MalformedURLException, InterruptedException, DownloadException {
        final URL fromUrl=new URL(smallFileUrl);
        final File tmpDir=newTmpDir();
        final File toFile=new File(tmpDir, fromUrl.getFile());
        try {
            CachedFtpFile cachedFtpFile = CachedFtpFile.Factory.instance().newStdJava6Impl(fromUrl.toExternalForm());
            cachedFtpFile.downloadFile(fromUrl, toFile);
        }
        catch (IOException e) {
            Assert.fail("IOException downloading file: "+e.getLocalizedMessage());
        }
        
        //size check
        Assert.assertEquals("name check", smallFile_expectedName, toFile.getName());
        Assert.assertEquals("size check", smallFile_expectedLength, toFile.length());
    }
    
    @Test
    public void testDownload_apacheCommons() throws MalformedURLException {
        final URL fromUrl=new URL(smallFileUrl);
        final File tmpDir=newTmpDir();
        final File toFile=new File(tmpDir, fromUrl.getFile());
        try {
            CachedFtpFile cachedFtpFile = CachedFtpFile.Factory.instance().newApacheCommonsImpl(fromUrl.toExternalForm());
            cachedFtpFile.downloadFile(fromUrl, toFile);
        }
        catch (Throwable e) {
            Assert.fail("Error downloading file: "+e.getClass().getName()+" - "+e.getLocalizedMessage());
        }
        
        //size check
        Assert.assertEquals("name check", smallFile_expectedName, toFile.getName());
        Assert.assertEquals("size check", smallFile_expectedLength, toFile.length());
    }

    @Test
    public void testDownload_EdtFtp_simple() throws MalformedURLException {
        final URL fromUrl=new URL(smallFileUrl);
        final File tmpDir=newTmpDir();
        final File toFile=new File(tmpDir, fromUrl.getFile());
        try {
            CachedFtpFile cachedFtpFile = CachedFtpFile.Factory.instance().newEdtFtpJImpl_simple(fromUrl.toExternalForm());
            cachedFtpFile.downloadFile(fromUrl, toFile);
        }
        catch (Throwable e) {
            Assert.fail("Error downloading file: "+e.getClass().getName()+" - "+e.getLocalizedMessage());
        }

        //size check
        Assert.assertEquals("name check", smallFile_expectedName, toFile.getName());
        Assert.assertEquals("size check", smallFile_expectedLength, toFile.length());
    }

    @Test
    public void testDownload_EdtFtp_advanced() throws MalformedURLException {
        final URL fromUrl=new URL(smallFileUrl);
        final File tmpDir=newTmpDir();
        final File toFile=new File(tmpDir, fromUrl.getFile());
        try {
            CachedFtpFile cachedFtpFile = CachedFtpFile.Factory.instance().newEdtFtpJImpl(fromUrl.toExternalForm());
            cachedFtpFile.downloadFile(fromUrl, toFile);
        }
        catch (Throwable e) {
            Assert.fail("Error downloading file: "+e.getClass().getName()+" - "+e.getLocalizedMessage());
        }
        //size check
        Assert.assertEquals("name check", smallFile_expectedName, toFile.getName());
        Assert.assertEquals("size check", smallFile_expectedLength, toFile.length());
    }


    /**
     * The java std library based downloader (pre nio) is interruptible, so it does stop
     * downloading when the task is cancelled.
     * 
     * @throws MalformedURLException
     * @throws InterruptedException
     */
    @Test
    public void testCancelDownload() throws MalformedURLException, InterruptedException {
        final URL fromUrl=new URL(largeFile);
        final File tmpDir=newTmpDir();
        final File toFile=new File(tmpDir, fromUrl.getFile());
        cancellationTest(true, toFile, new Callable<File>() {
            @Override
            public File call() throws Exception {
            CachedFtpFile cachedFtpFile = CachedFtpFile.Factory.instance().newStdJava6Impl(fromUrl.toExternalForm());
                cachedFtpFile.downloadFile(fromUrl, toFile);
                return toFile;
            }
        });
    }

    /**
     * The apache commons downloader does not respond to an interrupt, so it will continue to
     * download until the thread is killed, in jUnit I assume it is because it's created as a daemon
     * thread. In the GP server, the thread runs to completion before the server JVM exits.
     * 
     * See: https://issues.apache.org/jira/browse/NET-419, for a description of the problem
     */
    @Test
    public void testCancelDownloadApacheCommons() throws MalformedURLException, InterruptedException {
        final URL fromUrl=new URL(largeFile);
        final File apacheDir=newTmpDir();
        final File toFile=new File(apacheDir, fromUrl.getFile());
        cancellationTest(false, toFile, new Callable<File>() {
            @Override
            public File call() throws Exception {
                CachedFtpFile cachedFtpFile = CachedFtpFile.Factory.instance().newApacheCommonsImpl(fromUrl.toExternalForm());
                cachedFtpFile.downloadFile(fromUrl, toFile);
                return toFile;
            }
        });
    }
    
    /**
     * When wrapped in a helper method, the edtFtp downloader can respond to an interrupt.
     * @throws MalformedURLException
     * @throws InterruptedException
     */
    @Test
    public void testCancelDownloadEdtFtp()  throws MalformedURLException, InterruptedException {
        final URL fromUrl=new URL(largeFile);
        final File tmpDir=newTmpDir();
        final File toFile=new File(tmpDir, fromUrl.getFile());
        final File toParent=toFile.getParentFile();
        if (!toParent.exists()) {
            boolean success=toFile.getParentFile().mkdirs();
            if (!success) {
                Assert.fail("failed to create parent dir before download, parentDir="+toParent);
            }
        }
        cancellationTest(true, toFile, new Callable<File>() {
            @Override
            public File call() throws Exception {
                CachedFtpFile cachedFtpFile = CachedFtpFile.Factory.instance().newEdtFtpJImpl(fromUrl.toExternalForm());
                cachedFtpFile.downloadFile(fromUrl, toFile);
                return toFile;
            }
        });
    }
    
    
/*
 * Experimental code ...
    //test a connection timeout
    private static InputStream getMockInputStream() {
        final InputStream is=Mockito.mock(InputStream.class);
        //BDDMockito.given(is.read()).w
        return is;
    }

    public static URL getMockUrl(final String filename) throws MalformedURLException {
        final URLConnection mockConnection = Mockito.mock(URLConnection.class);
        //BDDMockito.given(mockConnection.getInputStream()).willReturn( new FileInputStream(file) );
        
//        try {
//            BDDMockito.given(mockConnection.getInputStream()).willReturn( getMockInputStream() );
//            BDDMockito.given(mockConnection.getInputStream()).willThrow(new Throwable("Should directly invoke "));
//        }
//        catch (IOException e) {
//            Assert.fail("Unexpected IOException initializing mockUrl( "+filename+" )");
//        }

        final URLStreamHandler handler = new URLStreamHandler() {

            @Override
            protected URLConnection openConnection(final URL arg0) throws IOException {
                try {
                    Thread.sleep(120*1000);
                    //throw new IOException("connection timeout");
                    return mockConnection;
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return mockConnection;
                }
            }
            
        };
        URL actual=new URL(filename);
        final URL url = new URL(actual.getProtocol(), actual.getHost(), actual.getPort(), actual.getFile(), handler);
        return url;
    }

     //URLConnection connection=fromUrl.openConnection();
     //   connection.setConnectTimeout(connectTimeout_ms);
     //   connection.setReadTimeout(readTimeout_ms);
     //   connection.connect();
    static class MockUrlConnection extends URLConnection {
        private final URLConnection actualUrlConnection;
        private final int wait_ms;
        
        public MockUrlConnection(final URLConnection actualUrlConnection) {
            this(actualUrlConnection, 60*1000);
        }
        public MockUrlConnection(final URLConnection actualUrlConnection, final int wait_ms) {
            super(actualUrlConnection.getURL());
            this.actualUrlConnection=actualUrlConnection;
            this.wait_ms=wait_ms;
        }
        
        @Override
        public void setConnectTimeout(int connectTimeout) {
            this.actualUrlConnection.setConnectTimeout(connectTimeout);
        }
        
        @Override
        public void setReadTimeout(int readTimeout) {
            this.actualUrlConnection.setReadTimeout(readTimeout);
        }

        @Override
        public void connect() throws IOException {
            try {
                Thread.sleep(wait_ms);
                actualUrlConnection.connect();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("we were interrupted!");
            }            
        }
    }

    private static URL getMockUrlLongConnectionTime(final int connectionTime_ms, final String urlStr) throws MalformedURLException, IOException {
        final URL actual=new URL(urlStr);
        //final URLConnection mockConnection = Mockito.mock(URLConnection.class);
//        doAnswer(new Answer<Object>() {
//            @Override
//            public Object answer(InvocationOnMock invocation) throws Throwable {
//                Thread.sleep(connectionTime_ms);
//                actual.openConnection().connect();
//                return null;
//            }
//        })
//        .when(mockConnection).connect();

        final URLStreamHandler handler = new URLStreamHandler() {
            URLConnection urlConnection=null;
            @Override
            protected URLConnection openConnection(final URL arg0) throws IOException {
                if (urlConnection==null) {
                    actual.openConnection();
                    urlConnection=new MockUrlConnection(actual.openConnection(), connectionTime_ms);
                }
                return urlConnection;
                
//                final URLConnection urlConnection = spy(actual.openConnection());
//                doAnswer(new Answer<Object>() {
//                    @Override
//                    public Object answer(InvocationOnMock invocation) throws Throwable {
//                        Thread.sleep(connectionTime_ms);
//                        actual.openConnection().connect();
//                        return null;
//                    }
//                })
//                .when(urlConnection).connect();
                
                
//                return mockConnection;
//                try {
//                    Thread.sleep(connectionTime_ms);
//                    return actual.openConnection();
//                }
//                catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                }
//                return null;
            }
        };

        final URL url = new URL(actual.getProtocol(), actual.getHost(), actual.getPort(), actual.getFile(), handler);
        return url;
    }

//    @Test
//    public void testConnectionTimeout() throws IOException, MalformedURLException, InterruptedException {
//        // the amount of time to wait before cancelling the download
//        final boolean deleteExisting=true;
//        final int connectTimeout_ms=5000;
//        final int readTimeout_ms=5000;
//
//        // the actual amount of time to delay the connection, to force the issue
//        final int actualConnectTimeout_ms=60*1000;
//        
//        final URL fromUrl=getMockUrlLongConnectionTime(actualConnectTimeout_ms, gpftpFile);
//        final File tmpDir=newTmpDir();
//        final File toFile=new File(tmpDir, fromUrl.getFile());
//        
//        try {
//            CachedFtpFile.downloadFile(fromUrl, toFile, deleteExisting, connectTimeout_ms, readTimeout_ms);
//            Assert.fail("Expecting connect timeout after 5 sec");
//        }
//        catch (IOException e) {
//            //expected
//        }
//        finally {
//            //do we need to clean up?
//        }
//    }
 * 
 *  end experimental code block comment.
 */
}
