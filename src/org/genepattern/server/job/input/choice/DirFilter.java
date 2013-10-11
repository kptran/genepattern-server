package org.genepattern.server.job.input.choice;

import java.util.regex.Pattern;

import org.genepattern.server.util.FindFileFilter;
import org.genepattern.webservice.ParameterInfo;

/**
 * Helper class for filtering directory listings for dynamic drop-down parameters.
 * This is similar to a Java FilenameFilter, execpt that in some cases we want to filter 
 * apache commons FTPFiles.
 * 
 * 
 * @author pcarr
 *
 */
public class DirFilter {
    public static enum Type {
        file,
        dir,
        any
    }
    protected DirFilter.Type type=Type.file;
    final protected String choiceDirFilter;
    final protected FindFileFilter globs=new FindFileFilter();
    
    public DirFilter(final ParameterInfo param) {
        if (param==null) {
            throw new IllegalArgumentException("param==null");
        }
        if (param.getAttributes()==null) {
            throw new IllegalArgumentException("param.attributes==null");
        }
        if ( param.getAttributes().containsKey(ChoiceInfo.PROP_CHOICE_DIR_FILTER) )
            //trim if necessary
            choiceDirFilter = ((String) param.getAttributes().get(ChoiceInfo.PROP_CHOICE_DIR_FILTER)).trim();
        else {
            choiceDirFilter = "";
        }
        if (!ChoiceInfoHelper.isSet(choiceDirFilter)) {
            //by default, ignore '*.md5' and 'readme.*' files
            globs.addGlob("!*.md5");
            globs.addGlob("!readme.*");
        }
        else {
            //parse this as a ';' separated list of patterns
            String[] patterns=choiceDirFilter.split(Pattern.quote(";"));
            for(final String patternIn : patterns) {
                //trim if necessary
                final String pattern=patternIn.trim();
                if (!ChoiceInfoHelper.isSet(pattern)) {
                    //skip empty pattern
                }
                else if (pattern.startsWith("type="+Type.dir)) {
                    type=Type.dir;
                }
                else if (pattern.startsWith("type="+Type.file)) {
                    type=Type.file;
                }
                else if (pattern.startsWith("type="+Type.any)) {
                    type=Type.any;
                }
                else {
                    //it's a glob pattern
                    globs.addGlob(pattern);
                }
            }
        }
    }
}