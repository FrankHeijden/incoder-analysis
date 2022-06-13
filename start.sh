for (( i=0; i<$2; i++ ))
do
    j=$[i+1]
    echo -n "[$j/$2]: "
    sbatch run.sh $1 $2 $i;
done
