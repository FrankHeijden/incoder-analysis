for (( i=0; i<$2; i++ ))
do
    ($1 $2 $i) &
done
wait
