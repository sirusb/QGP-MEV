//Author: Mohamed Nadhir Djekidel @ sidra
//email: mdjekidel1@sidra.org

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

def params = new JsonSlurper().parseText(new File(args[0]).text)

//def inputDir = params.inputDir
//def realInputDir = Paths.get(inputDir).toRealPath().toString()

def inputFiles = params.inputFile
def sampleExt = params.extension
def sampleSuffix = params.suffix

def extRegex = java.util.regex.Pattern.quote(sampleSuffix)

def pathFile = new File(inputFiles)

if(!pathFile.exists()){
    System.err.println("Please make sure the path of the inputFile is correct").
    System.err.println("Actual value : ${inputFiles}")
    System.exit(1)
}

def branches = [:]
pathFile.eachLine { file_path ->
    println "File : ${file_path.trim()}"
    def fileRealPath = Paths.get(file_path.trim()).toRealPath().toString()
    println "RealPath : ${fileRealPath}"

    def file = new File(fileRealPath)
    def file2 = new File(file_path.trim())
    if (file.exists()){
        def sampleName = file2.name.replaceFirst(/${extRegex}$/, '')
        branches[sampleName] = [file.path]
    }else{
        println "File not found:${file_path}."
    }
}


//Get the real path of all the parameters
def sif = Paths.get(params.sif).toRealPath().toString()
def fasta = Paths.get(params.fasta).toRealPath().toString()
def mk  = Paths.get(params.mk).toRealPath().toString()
def bindingDir = Paths.get(params.bindingDir).toRealPath().toString()
def outDirBase = params.outDirBase



config {
    executor = "lsf"
    queue = params.hpc_queue
    procs = 8
    lsf_request_options = "-M 1000 -P slk_lab"
 
    commands {      
        callGenotype {         
                procs=8
                lsf_request_options="-P slk_lab -J callGenotype"
            }
        mergeMEI {
                procs=8
                lsf_request_options="-P slk_lab -J mergeMEI"
            }
        mergeAbsentME {
                procs=8
                lsf_request_options="-P slk_lab -J mergeAbsentME"
            }
    }
}
 
 
// Task 2: Call genotype for multiple CRAM files
callGenotype = {
    produce("${outDirBase}/${branch.name}_call/absent.txt.gz") {
        exec """
            source /etc/bashrc &&
            source /etc/profile &&
            module load nextflow/v23.04.3 &&
            mkdir -p ${outDirBase}/${branch.name}_call &&
            singularity exec -B ${bindingDir} ${sif} call_genotype_38 
                -i ${input} 
                -fa ${fasta} 
                -mk ${mk} 
                -outdir ${outDirBase}/${branch.name}_call 
                -sample_name ${branch.name} \
                -p 8 &&
            echo ${outDirBase}/${branch.name}_call >> dirlist.txt
        """, "callGenotype"
    }    
}
 
// Task 3: Merge non-reference ME insertions
mergeMEI = {
    produce("${outDirBase}/jointcall_out/${params.cohortName}_MEI_jointcall.vcf.gz") {
        exec """
            source /etc/bashrc &&
            source /etc/profile &&
            module load nextflow/v23.04.3 &&
            singularity exec -B ${bindingDir} ${sif} joint_calling_hs 
                -merge_mei 
                -f dirlist.txt 
                -fa ${fasta} 
                -cohort_name ${params.cohortName}
                -outdir ${outDirBase}/jointcall_out/
        """, "mergeMEI"
    }
}
 
// Task 4: Merge reference ME polymorphisms
mergeAbsentME = {
    produce("${params.outDirBase}/jointcall_out/${params.cohortName}_MEA_jointcall.vcf.gz") {
        exec """
           source /etc/bashrc &&
            source /etc/profile &&
            module load nextflow/v23.04.3 &&
            singularity exec -B ${params.bindingDir} ${params.sif} joint_calling_hs 
                -merge_absent_me 
                -f dirlist.txt
                -fa ${params.fasta} 
                -cohort_name ${params.cohortName}
                -outdir ${params.outDirBase}/jointcall_out
        """, "mergeAbsentME"
    }
}
 

run {    
    branches *  [callGenotype] //+ mergeMEI + mergeAbsentME    
}
