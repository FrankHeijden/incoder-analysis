#!/bin/sh
#
#SBATCH --job-name="InCoder Inferencing"
#SBATCH --partition=gpu
#SBATCH --time=23:59:00
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=4
#SBATCH --gpus-per-task=1
#SBATCH --mem-per-cpu=12G

module load 2022r2
module load gpu
module load cuda/11.3
module load py-pip

python -m pip install --user -r requirements.txt
cd scripts
python run.py $1 $2 $3
