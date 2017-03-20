#!/usr/bin/env bash
#
# Wrapper script for running R as a java command. This is for
#     R modules which pre-date the use of the Rscript command. 
#
# Usage:
#     run-rjava.sh -c <env-custom> <r-version> <java-flags> -cp <classpath> RunR <args>
#
# Example module commandLines
#     commandLine=<R2.15_Rjava> <libdir>hello.R hello
#     commandLine=<R2.5> <libdir>hello.R hello
#
# Configuration
#     run-rjava=<wrapper-scripts>/run-rjava.sh -c <env-custom>
#     R2.15_Rjava=<run-rjava> 2.15 <rjava_flags> -cp <run_r_path> RunR

function run_rjava() {
    local __dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
    source "${__dir}/gp-common.sh"
    
    # optional -c <__gp_env_custom_arg>
    if [ "${1:-}" = "-c" ]; then
        shift;
        __gp_env_custom_arg="${1}";
        shift;
    fi
    
    local r_version="${1}"
    shift;

    # ---> copied from gp-common::parseArgs ...
    # the remaining args are the module command line
    __gp_module_cmd=( "$@" );

    # process '-e' flags immediately
    setEnvironmentVariables;

    # process '-c' flag next
    __gp_env_custom_script=$(envCustomScript "${__gp_env_custom_arg}");
    if [[ ! -f "${__gp_env_custom_script}" ]]; then
        # it's not a regular file
        __gp_env_custom_script="";
    fi
    sourceSiteCustomizationScripts;
    addModuleEnvs;

    # <---- end
    # customization for run-rjava.sh script
    addEnv "R-${r_version}"
    addEnv "Java"
    initModuleEnvs;

    r=`which R`
    rhome=${r%/*/*}
    java "-DR_HOME=${rhome}" -Dr_flags='--no-save --quiet --slave --no-restore' "$@"
}

run_rjava "${@}"

