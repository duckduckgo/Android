cmake_minimum_required(VERSION 3.5)

if(UNIX AND NOT APPLE)
    link_libraries(uuid)
endif()

add_executable (RunTests BloomFilterTest.cpp)
target_link_libraries (RunTests LINK_PUBLIC BloomFilter)

add_test(NAME RunTests COMMAND RunTests)
