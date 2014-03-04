package org.genepattern.server.job.input.configparams;

import static org.hamcrest.core.Is.is;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.genepattern.junitutil.FileUtil;
import org.genepattern.server.job.input.configparam.JobConfigParams;
import org.genepattern.server.job.input.configparam.JobConfigParamsParserYaml;
import org.junit.Assert;
import org.junit.Test;

/**
 * junit tests for serializing/deserializing a JobConfigParams instance from a file.
 * @author pcarr
 *
 */
public class TestJobConfigParamsParser {
    
    @Test
    public void testParser() throws Exception {
        File in=FileUtil.getSourceFile(this.getClass(), "executor_input_params.yaml");
        JobConfigParams params=JobConfigParamsParserYaml.parse(in);
        
        Assert.assertEquals("group.name", "Advanced/Job Configuration", params.getInputParamGroup().getName());
        Assert.assertEquals("group.description", "Set job configuration parameters", params.getInputParamGroup().getDescription());
        Assert.assertEquals("group.hidden", true, params.getInputParamGroup().isHidden());
        Assert.assertEquals("group.numParams", 6, params.getInputParamGroup().getParameters().size());
        List<String> expected = Arrays.asList("drm.queue", "drm.memory", "drm.walltime", "drm.nodeCount", "drm.cpuCount", "drm.extraArgs");
        Assert.assertThat("group.parameters", params.getInputParamGroup().getParameters(), is(expected));
    }

}
