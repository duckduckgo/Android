directory=`dirname $0`
mkdir $directory/build;
(cd $directory/build; cmake ..; make all;)
$directory/build/test/RunTests