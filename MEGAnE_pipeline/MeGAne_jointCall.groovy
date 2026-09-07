//Author: Mohamed Nadhir Djekidel @ sidra
//email: mdjekidel1@sidra.org

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

def params = new JsonSlurper().parseText(new File(args[0]).text)



//Get the real path of all the parameters
def sif = Paths.get(params.sif).toRealPath().toString()
def fasta = Paths.get(params.fasta).toRealPath().toString()
def mk  = Paths.get(params.mk).toRealPath().toString()
def bindingDir = Paths.get(params.bindingDir).toRealPath().toString()
def outDirBase = params.outDirBase
def dirlist = Paths.get(params.dirlist).toRealPath().toString()
def rscript = Paths.get(params.rscript).toRealPath().toString()

def branches = [
    "jointCalling" : dirlist
]

config {
    executor = "lsf"
    queue = params.hpc_queue
    procs = 1
    lsf_request_options = "-M 1000 -P slk_lab"
 
    commands {      
        
        mergeMEI {
                procs=16
                lsf_request_options="-P slk_lab -J mergeMEI"
            }
        mergeAbsentME {
                procs=16
                lsf_request_options="-P slk_lab -J mergeAbsentME"
            }
    }
}
 
 

GetDirsToUse = {
    produce("${outDirBase}/dirs_touse.txt"){
        exec """
        source /etc/bashrc &&
        source /etc/profile &&
        module load R/R-4.3.1 &&

        Rscript ${rscript} --i ${input} -o ${outDirBase}/dirs_touse.txt
        """
    }
}

 
// Task 3: Merge non-reference ME insertions
MergeMEI = {
    produce("${outDirBase}/jointcall_out/${params.cohortName}_MEI_jointcall.vcf.gz") {
        exec """
            source /etc/bashrc &&
            source /etc/profile &&
            module load nextflow/v23.04.3 &&
            singularity exec -B ${bindingDir} ${sif} joint_calling_hs 
                -merge_mei 
                -f ${input}
                -fa ${fasta} 
                -p 16
                -cohort_name ${params.cohortName}
                -outdir ${outDirBase}/jointcall_out/
        """, "mergeMEI"
    }
}
 
// Task 4: Merge reference ME polymorphisms
MergeAbsentME = {
    produce("${outDirBase}/jointcall_out/${params.cohortName}_MEA_jointcall.vcf.gz") {
        exec """
           source /etc/bashrc &&
           source /etc/profile &&
           module load nextflow/v23.04.3 &&
           singularity exec -B ${params.bindingDir} ${params.sif} joint_calling_hs 
                -merge_absent_me 
                -f ${input}
                -fa ${params.fasta} 
                -p 16
                -cohort_name ${params.cohortName}
                -outdir ${outDirBase}/jointcall_out
        """, "mergeAbsentME"
    }
}


ReshapeVCF = {
    produce("${outDirBase}/phasing_out/${params.cohortName}_biallelic.vcf.gz") {
        exec """
            source /etc/bashrc &&
            source /etc/profile &&
            module load nextflow/v23.04.3 &&
            singularity exec -B ${params.bindingDir} ${params.sif} reshape_vf 
                -i ${outDirBase}/jointcall_out/${params.cohortName}_MEI_jointcall.vcf.gz
                -a ${params.outDirBase}/jointcall_out/${params.cohortName}_MEA_jointcall.vcf.gz
                -cohort_name ${params.cohortName}
                -outdir ${outDirBase}/phasing_out
        """
    }
}
 

run {    
    branches * [GetDirsToUse + [ MergeMEI , MergeAbsentME] ] + ReshapeVCF
}
