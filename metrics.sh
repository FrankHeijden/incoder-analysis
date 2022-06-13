#!/bin/sh
#
#SBATCH --job-name="InCoder Metrics"
#SBATCH --partition=compute
#SBATCH --time=23:59:00
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=24
#SBATCH --mem-per-cpu=4G

module load 2022r2
module load compute
module load miniconda3
module load openssh
module load git

unset CONDA_SHLVL
source "$(conda info --base)/etc/profile.d/conda.sh"
conda activate /home/fnmvanderheijd/incoder-env

python -m pip install --user -r requirements.txt
cd scripts
python metrics.py
