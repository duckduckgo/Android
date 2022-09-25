directory=`dirname $0`
mkdir $directory/build;
(cd $directory/build; cmake ..; make all;)
$directory/build/utils/GenerateFilter $1 $2 $3