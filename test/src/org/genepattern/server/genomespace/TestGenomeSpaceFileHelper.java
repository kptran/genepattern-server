package org.genepattern.server.genomespace;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.genepattern.junitutil.Demo;
import org.genepattern.server.dm.UrlUtil;
import org.genepattern.server.genomespace.GenomeSpaceFileHelper;
import org.genepattern.util.SemanticUtil;
import org.junit.Test;

public class TestGenomeSpaceFileHelper {
    // default GS url
    final String gsUrl="https://dm.genomespace.org";
    // default data file path, relative to GS url file prefix
    final String filePath="/Home/userId/all_aml_test.tab";

    @Test
    public void httpProtocol() throws MalformedURLException, URISyntaxException {
        final String inputSpec="http://dm.genomespace.org/datamanager/file"+filePath;
        final String  expected="http://dm.genomespace.org/datamanager/v1.0/file"+filePath;
        assertEquals(new URL(expected), GenomeSpaceFileHelper.insertProtocolVersion(new URL(inputSpec)));
    }

    @Test
    public void noDataFormat() throws MalformedURLException, URISyntaxException {
        final String inputSpec=gsUrl+"/datamanager/file"+filePath;
        final String  expected=gsUrl+"/datamanager/v1.0/file"+filePath;
        assertEquals(new URL(expected), GenomeSpaceFileHelper.insertProtocolVersion(new URL(inputSpec)));
    }
    
    @Test
    public void withDataFormat() throws MalformedURLException, URISyntaxException {
        final String inputSpec=gsUrl+"/datamanager/file"+filePath+"?dataformat=http://www.genomespace.org/datamanager/dataformat/gct";
        final String  expected=gsUrl+"/datamanager/v1.0/file"+filePath+"?dataformat=http://www.genomespace.org/datamanager/dataformat/gct";
        assertEquals(new URL(expected), GenomeSpaceFileHelper.insertProtocolVersion(new URL(inputSpec)));
    }
    
    @Test
    public void fromGoogleDrive() throws MalformedURLException {
        final String inputSpec=gsUrl+"/datamanager/file/Home/googledrive:test@email.com(lHv4L0eliPcV19HRCqwWQg==)/GenomeSpacePublic/all_aml(0Bx1oidMlPWQtN2RlQV8zd25Md1E)/all_aml_test.cls?dataformat=http://www.genomespace.org/datamanager/dataformat/gct";
        final String  expected=gsUrl+"/datamanager/v1.0/file/Home/googledrive:test@email.com(lHv4L0eliPcV19HRCqwWQg==)/GenomeSpacePublic/all_aml(0Bx1oidMlPWQtN2RlQV8zd25Md1E)/all_aml_test.cls?dataformat=http://www.genomespace.org/datamanager/dataformat/gct";
        assertEquals(new URL(expected), GenomeSpaceFileHelper.insertProtocolVersion(new URL(inputSpec)));
    }

    // input.path already starts with /datamanager/v1.0
    @Test
    public void noInsertNeeded() throws MalformedURLException {
        final String inputSpec=gsUrl+"/datamanager/v1.0/file"+filePath+"?dataformat=http://www.genomespace.org/datamanager/dataformat/gct";
        final String  expected=gsUrl+"/datamanager/v1.0/file"+filePath+"?dataformat=http://www.genomespace.org/datamanager/dataformat/gct";
        assertEquals(new URL(expected), GenomeSpaceFileHelper.insertProtocolVersion(new URL(inputSpec)));
    }
    
    // helper test for GenomeSpace_Google drive url
    protected void checkGoogleDocGsUrl(final String message, final String expected, final String urlSpec, final boolean checkGsHelper) throws IOException {
        final URL url=new URL(urlSpec);
        assertEquals(message, // e.g. root folder 
                expected, // e.g. "googledrive:test_user@email.com", 
                UrlUtil.getFilenameFromUrl(url));
        if (checkGsHelper) {
            assertEquals(message+", GsHelper", 
                expected, 
                GenomeSpaceFileHelper.extractFilename(url));
        }
    }

    @Test
    public void getFilenameFromUrl_GenomeSpace_GoogleDrive() throws IOException {
        // simulate root path to a GenomeSpace user's shared google drive folder
        final String rootDir="https://dm.genomespace.org/datamanager/v1.0/file/Home/googledrive:test_user@email.com(mIv4L0eliPcQ21HRCqwWQg==)";
        final String subDir=rootDir+"/GenomeSpacePublic/all_aml(0By2oidMlPWQtQ2RlQV8zd25Md1E)";
        final String file=subDir+"/all_aml_test.gct";
        final String subDirWithParen=rootDir+"/all_aml(test%20dir)(0Cz0uuuUUUUaXo2SFR0000)";
        
        
        checkGoogleDocGsUrl("root folder", "googledrive:test_user@email.com", rootDir, true);
        checkGoogleDocGsUrl("sub dir", "all_aml", subDir, true);
        checkGoogleDocGsUrl("file in sub dir", "all_aml_test.gct", file, true);
        checkGoogleDocGsUrl("file name with paren", "all_aml_test(paren).gct", rootDir+"/all_aml_test(paren).gct", 
                // not working in GenomeSpaceHelper
                false);
        checkGoogleDocGsUrl("sub dir with paren", "all_aml(test dir)", subDirWithParen, 
                // not working in GenomeSpaceHelper
                false);
    }

    // helper test for GenomeSpace custom converter url
    protected void checkGsKind(final String expectedKind, final String queryString, final boolean checkGsHelper) throws IOException {
        final String expectedName="CEL_IK50.tab";
        URL url=new URL(Demo.dataGsDir+expectedName+queryString);
        
        String filename=UrlUtil.getFilenameFromUrl(url);
        String extension=SemanticUtil.getExtension(filename);
        String kind=UrlUtil.getKindFromUrl(url, queryString, extension);
        assertEquals("'"+queryString+"', filename", expectedName, filename);
        assertEquals("'"+queryString+"', kind", expectedKind, kind);
        
        if (checkGsHelper) {
            filename=GenomeSpaceFileHelper.extractFilename(url);
            kind=GenomeSpaceFileHelper.extractKind(url, filename);
            assertEquals("GsHelper, '"+queryString+"', filename", expectedName, filename);
            assertEquals("GsHelper, '"+queryString+"', kind", expectedKind, kind);
        }
    }
    
    @Test public void getKindFromUrl_GS_converter() throws Exception {
         checkGsKind("gct", "?dataformat=http://www.genomespace.org/datamanager/dataformat/gct", true);
    }

    @Test public void getKindFromUrl_GS_converter_corner_cases() throws Exception {
         checkGsKind("tab", "", true);
         checkGsKind("tab", "?", true);
         checkGsKind("tab", "?dataformat", true);
         checkGsKind("tab", "?dataformat=", true);
         checkGsKind("tab", "?dataformat=gct", true);
         checkGsKind("tab", "?dataformat=/gct", true);
         checkGsKind("gct", "?dataformat=/dataformat/gct", true);

         // GenomeSpaceFileHelper anomalies, still converts but probably shouldn't
         checkGsKind("tab", "?/dataformat=/dataformat/gct", false);
         checkGsKind("tab", "?A=/dataformat/gct", false);
         checkGsKind("tab", "?/dataformat/gct", false);
    }
    
}
