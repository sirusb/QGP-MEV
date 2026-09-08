# QGP-MEV

Scripts used to characterize **mobile element variants (MEVs)** in the **Qatar Genome Program (QGP)**. The repository contains MEGAnE variant-calling workflows and downstream analyses of MEV families, allele frequencies, and population structure, including comparisons with the 1000 Genomes Project and BioBank Japan (BBJ).

These are research scripts adapted to a specific HPC environment. Running them elsewhere requires updating paths and cluster settings and supplying the external inputs and helper scripts described below.

## Repository contents

| File | Purpose |
| --- | --- |
| [`MEGAnE_pipeline/MeGAne_pipeline.groovy`](MEGAnE_pipeline/MeGAne_pipeline.groovy) | Per-sample MEGAnE calling, with eight threads per sample. |
| [`MEGAnE_pipeline/MeGAne_jointCall.groovy`](MEGAnE_pipeline/MeGAne_jointCall.groovy) | Select sample output directories, jointly call insertions and reference-element absences, and reshape the resulting VCFs. |
| [`MEGAnE_pipeline/MeGANE_params/params.json`](MEGAnE_pipeline/MeGANE_params/params.json) | Example configuration for per-sample calling. |
| [`MEGAnE_pipeline/MeGANE_params/params_jointCalling.json`](MEGAnE_pipeline/MeGANE_params/params_jointCalling.json) | Example configuration for joint calling. |
| [`downstreamAnalysis/QGPAnalysis.rmd`](downstreamAnalysis/QGPAnalysis.rmd) | R Markdown notebook for cohort comparisons and population-level analyses. |

## Workflow

1. **Per-sample calling:** read alignment paths from a text file and run the container command `call_genotype_38` for each sample.
2. **Joint calling:** prepare a list of sample output directories and run `joint_calling_hs` separately with `-merge_mei` and `-merge_absent_me`.
3. **VCF reshaping:** combine the insertion and absence callsets into a biallelic VCF using the container command specified in the joint-calling script.
4. **Downstream analysis:** analyze prepared cohort VCFs alongside population metadata, allele-frequency tables, and previously computed PCA results.

The active `run` block in `MeGAne_pipeline.groovy` performs only per-sample calling. Its merge stages are defined but disabled; joint calling is handled by `MeGAne_jointCall.groovy`. The `phasing_out` directory contains the reshaped VCF; the scripts do not implement a phasing step.

## Requirements

### Variant-calling workflows

- A Linux HPC environment with Bash, environment modules, and an LSF scheduler, as configured in the scripts.
- Bpipe and its Java/Groovy runtime for the pipeline DSL (`config`, `produce`, `exec`, and `run`).
- Singularity and a MEGAnE `.sif` container exposing the commands used by the workflows.
- An hg38 reference FASTA, the corresponding MEGAnE k-mer resource (`refGenome.mk`), and input alignments with any required companion files.
- R for the joint-calling directory-selection helper.

The scripts explicitly load `nextflow/v23.04.3` and, for directory selection, `R/R-4.3.1`. Adapt these module commands, scheduler settings, and resource requests to your environment. The Groovy files use Bpipe syntax even though their shell commands load a Nextflow module.

### Downstream analysis

The notebook uses R packages `tidyverse`, `glue`, `ggplot2`, `readr`, `purrr`, `VariantAnnotation`, `GenomicRanges`, `SCP`, `RColorBrewer`, `fs`, `patchwork`, `plotthis`, `cli`, and `scales`. Rendering it as HTML also requires `rmarkdown` and Pandoc. Additional dependencies may be required by the external utility script.

## Configuration

Copy the example JSON files and replace the site-specific paths before running. Commands below assume a Bash shell in the repository root.

```bash
cp MEGAnE_pipeline/MeGANE_params/params.json params.local.json
cp MEGAnE_pipeline/MeGANE_params/params_jointCalling.json params_jointCalling.local.json
```

### Shared parameters

| Parameter | Description |
| --- | --- |
| `sif` | Path to the MEGAnE Singularity image. |
| `fasta` | Path to the hg38 reference FASTA. |
| `mk` | Path to the MEGAnE k-mer resource. Both scripts resolve this path, although joint calling does not pass it to MEGAnE. |
| `bindingDir` | Host directory mounted inside the container with `singularity exec -B`. Ensure required input and output paths are accessible inside the container. |
| `hpc_queue` | LSF queue name. |
| `cohortName` | Cohort identifier used in joint-call output names. |
| `outDirBase` | Base output directory; the examples use `./megane_output`. |

### Per-sample parameters

| Parameter | Description |
| --- | --- |
| `inputFile` | Text file containing one alignment path per line. Use existing paths, with no header or blank lines. |
| `suffix` | Filename suffix removed to derive the sample name, such as `.bwa.bam`. Derived sample names must be unique. |
| `extension` | Present in the example configuration but not used to filter inputs by the current script. |

For example, an input list can contain:

```text
/path/to/alignments/sample_001.bwa.bam
/path/to/alignments/sample_002.bwa.bam
```

With `suffix` set to `.bwa.bam`, these become samples `sample_001` and `sample_002`.

### Joint-calling parameters

| Parameter | Description |
| --- | --- |
| `dirlist` | Text file listing per-sample MEGAnE output directories. |
| `rscript` | Path to the external `getDirPaths.R` helper. It receives `--i <input-list> -o <output-list>` and must write the directory list used for joint calling. This helper is not included. |

## Running the pipelines

After adapting the scripts and configuration for your environment, the intended Bpipe invocations are:

```bash
# Run per-sample calling.
bpipe run MEGAnE_pipeline/MeGAne_pipeline.groovy params.local.json

# Run separately after the sample calls and directory list are ready.
bpipe run MEGAnE_pipeline/MeGAne_jointCall.groovy params_jointCalling.local.json
```

The per-sample workflow appends completed sample output directories to `dirlist.txt` in the working directory. Review this list before using it as the joint-calling `dirlist`: repeated runs can append duplicate entries, and separate batches need a consolidated list. Absolute directory paths avoid ambiguity when joint calling runs from another working directory.

### Expected outputs

The pipeline scripts declare the following outputs beneath `outDirBase`:

```text
megane_output/
├── <sample>_call/
│   ├── absent.txt.gz
│   └── ... other MEGAnE sample outputs
├── dirs_touse.txt
├── jointcall_out/
│   ├── <cohortName>_MEI_jointcall.vcf.gz
│   └── <cohortName>_MEA_jointcall.vcf.gz
└── phasing_out/
    └── <cohortName>_biallelic.vcf.gz
```

`MEI` represents non-reference mobile element insertions; `MEA` represents absence of mobile elements present in the reference.

## Downstream analysis

[`QGPAnalysis.rmd`](downstreamAnalysis/QGPAnalysis.rmd) contains analyses that:

- Classify mobile elements into AluY, Alu, L1HS, L1, SVA, HERV-K, and other categories.
- Update QGP VCF annotations and compare category distributions across QGP, 1000 Genomes, and BBJ.
- Join QGP sample identifiers with population assignments and plot existing PCA results.
- Summarize cumulative variant discovery by allele-frequency bin and mobile element family.
- Export population summaries, Excel tables, and PDF/PNG figures.

Before running the notebook, provide and update its references to:

- `../R/utils.R`, including the Excel export helpers used by the notebook.
- QGP and 1000 Genomes prepared VCFs and the BBJ mobile-element summary table.
- QGP population assignments, cluster metadata, and subject identifier mappings under `../data-raw/`.
- PCA eigenvector files and the QGP allele-frequency table under `../MeGAne/PCA_analysis/`.

These inputs and helper functions are not bundled. The notebook reads filtered/prepared VCF filenames that differ from the raw workflow outputs; the repository does not provide every intervening preparation step. It also uses hg38 VCFs and a BBJ table labeled GRCh37 for category summaries, rather than implementing coordinate harmonization.

Once dependencies and paths are resolved, render from the repository root:

```bash
Rscript -e 'rmarkdown::render("downstreamAnalysis/QGPAnalysis.rmd")'
```

Most analysis exports are written to `data/MEGAnE_analysis/` when the notebook's relative directory layout is preserved. Review the notebook's other output paths, including the annotated VCF, before execution.

## Reproducibility notes

The repository includes scripts and example configurations, but no sequencing data, reference resources, MEGAnE image, dependency lockfile, or test dataset. The external `getDirPaths.R` and `R/utils.R` helpers are required for their respective stages.

Review the scripts before execution: the missing-input error handler in `MeGAne_pipeline.groovy` contains a trailing period after a `println(...)` call, and the reshaping command in `MeGAne_jointCall.groovy` is spelled `reshape_vf`. Check the former for syntax issues and verify the latter against the installed container. The commands above describe the intended workflow; they have not been validated end to end in this repository.

## Author and license

Mohamed Nadhir Djekidel — Sidra. Contact: `mdjekidel1@sidra.org`.

Licensed under the [Apache License 2.0](LICENSE).
